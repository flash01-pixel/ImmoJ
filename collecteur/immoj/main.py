"""Point d'entrée du collecteur.

    python -m immoj.main                 # collecte normale (toutes les sources)
    python -m immoj.main --source immovlan --pages 6
    python -m immoj.main --simulation    # sans Supabase ni ntfy : affiche seulement
    python -m immoj.main --forcer        # ignore la plage horaire 7h–23h

Déroulé : vérification de l'heure → lecture des recherches sauvegardées →
pour chaque source : collecte, intégration (géocodage, dédoublonnage, prix,
nouveautés), notifications → journal des collectes → nettoyage.
"""

from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime
from zoneinfo import ZoneInfo

from . import config
from .config import Secrets, charger_secrets
from .dedoublonnage import Candidat, trouver_doublon
from .geocodage import Geocodeur
from .modele import Annonce
from .notifications import Notificateur
from .recherches import correspond
from .sources import REGISTRE, ResultatCollecte
from .stockage import Stockage, StockageSimulation, maintenant

log = logging.getLogger("immoj")

# Sans aucune recherche sauvegardée, on notifie toutes les nouveautés de cette
# transaction (None pour ne rien notifier).
TRANSACTION_PAR_DEFAUT = "location"


def dans_la_plage_horaire(instant: datetime | None = None) -> bool:
    """Le cron GitHub est en UTC : on vérifie l'heure de Bruxelles nous-mêmes."""
    local = (instant or datetime.now(ZoneInfo(config.FUSEAU))).astimezone(ZoneInfo(config.FUSEAU))
    return config.HEURE_DEBUT <= local.hour <= config.HEURE_FIN


class Integrateur:
    """Intègre les annonces d'une source dans la base et déclenche les notifications."""

    def __init__(self, stockage: Stockage, geocodeur: Geocodeur, notificateur: Notificateur,
                 recherches: list[dict]) -> None:
        self.stockage = stockage
        self.geocodeur = geocodeur
        self.notificateur = notificateur
        self.recherches = recherches
        self.recherches_notif = [r for r in recherches if r.get("notifier", True)]

    def integrer(self, resultat: ResultatCollecte) -> int:
        """Renvoie le nombre d'annonces réellement nouvelles (jamais vues nulle part)."""
        source = resultat.source
        connues = self.stockage.sources_connues(source)
        # Biens déjà rattachés à cette source : une autre annonce de la même
        # source ne peut pas en être un doublon (le site ne publie pas deux
        # fois le même bien ; deux annonces identiques = deux biens, ex. deux
        # appartements jumeaux dans un immeuble neuf).
        deja_sur_cette_source = {c["annonce_id"] for c in connues.values()}
        import_initial = not self.stockage.a_deja_reussi(source)
        vues_ids_externes: list[str] = []
        vues_annonce_ids: list[str] = []
        nouvelles = 0

        for a in resultat.annonces:
            commune = self.geocodeur.trouver(a.commune, a.code_postal)
            nom_commune = commune.nom if commune else None
            lat, lon = (a.latitude, a.longitude) if a.latitude is not None else (
                (commune.lat, commune.lon) if commune else (None, None))

            connue = connues.get(a.id_externe)
            if connue:
                vues_ids_externes.append(a.id_externe)
                vues_annonce_ids.append(connue["annonce_id"])
                ancien = connue.get("prix_affiche")
                if a.prix is not None and a.prix != ancien:
                    log.info("[%s] prix modifié %s : %s → %s", source, a.id_externe, ancien, a.prix)
                    self.stockage.changer_prix(connue["annonce_id"], source, a.id_externe,
                                               ancien, a.prix)
                    if ancien is not None and a.prix < ancien and not import_initial:
                        self._notifier_baisse(connue["annonce_id"], a, ancien, nom_commune,
                                              lat, lon)
                continue

            # Jamais vue sur cette source : est-ce un bien déjà connu ailleurs ?
            candidat = Candidat(id="", transaction=a.transaction, commune=nom_commune,
                                code_postal=a.code_postal, prix=a.prix, chambres=a.chambres,
                                surface=a.surface, titre=a.titre, description=a.description)
            candidats = [c for c in self.stockage.candidats_doublon(a.transaction, nom_commune,
                                                                     a.code_postal)
                         if c.id not in deja_sur_cette_source]
            doublon = trouver_doublon(candidat, candidats)
            if doublon:
                log.info("[%s] %s est un doublon de %s", source, a.id_externe, doublon.id)
                annonce_id = doublon.id
                self.stockage.ajouter_source(annonce_id, a)
                self.stockage.completer_annonce(annonce_id, a)
            else:
                annonce_id = self.stockage.creer_annonce(a, nom_commune, lat, lon)
                self.stockage.ajouter_source(annonce_id, a)
                nouvelles += 1
                if not import_initial:
                    self._notifier_nouvelle(annonce_id, a, nom_commune, lat, lon)
            deja_sur_cette_source.add(annonce_id)
            vues_ids_externes.append(a.id_externe)
            vues_annonce_ids.append(annonce_id)

        self.stockage.toucher(source, vues_ids_externes, vues_annonce_ids)
        if import_initial and resultat.annonces:
            self.notificateur.import_initial(source, len(resultat.annonces))
        return nouvelles

    def _recherches_correspondantes(self, a: Annonce, commune: str | None,
                                    lat: float | None, lon: float | None) -> list[dict | None]:
        """Liste des recherches qui matchent ; [None] = « notifier sans recherche »."""
        if not self.recherches_notif:
            return [None] if a.transaction == TRANSACTION_PAR_DEFAUT else []
        return [r for r in self.recherches_notif if correspond(a, r, commune, lat, lon)]

    def _notifier_nouvelle(self, annonce_id: str, a: Annonce, commune: str | None,
                           lat: float | None, lon: float | None) -> None:
        for r in self._recherches_correspondantes(a, commune, lat, lon):
            rid = r["id"] if r else None
            if self.stockage.marquer_notification(annonce_id, "nouvelle", rid):
                self.notificateur.nouvelle_annonce(a, r["nom"] if r else None)

    def _notifier_baisse(self, annonce_id: str, a: Annonce, ancien: int, commune: str | None,
                         lat: float | None, lon: float | None) -> None:
        for r in self._recherches_correspondantes(a, commune, lat, lon):
            rid = r["id"] if r else None
            if self.stockage.marquer_notification(annonce_id, f"baisse:{a.prix}", rid):
                self.notificateur.baisse_de_prix(a, ancien, a.prix)


def executer(noms_sources: list[str] | None, pages: int | None, simulation: bool,
             forcer: bool, transactions: tuple[str, ...] | None) -> int:
    if not forcer and not dans_la_plage_horaire():
        log.info("Hors plage horaire (%dh–%dh Bruxelles) : rien à faire.",
                 config.HEURE_DEBUT, config.HEURE_FIN)
        return 0

    if simulation:
        secrets = Secrets(supabase_url="", supabase_cle_service="", ntfy_topic="")
        stockage: Stockage = StockageSimulation()
    else:
        secrets = charger_secrets()
        stockage = Stockage(secrets)
    notificateur = Notificateur(secrets, simulation=simulation)
    geocodeur = Geocodeur()
    recherches = stockage.lire_recherches()
    log.info("%d recherche(s) sauvegardée(s) active(s)", len(recherches))
    integrateur = Integrateur(stockage, geocodeur, notificateur, recherches)

    noms = noms_sources or list(REGISTRE)
    code_sortie = 0
    for nom in noms:
        if nom not in REGISTRE:
            log.error("source inconnue : %s (disponibles : %s)", nom, ", ".join(REGISTRE))
            code_sortie = 2
            continue
        if not REGISTRE[nom].est_configuree():
            log.info("[%s] non configurée (secrets absents) : ignorée", nom)
            continue
        heure = datetime.now(ZoneInfo(config.FUSEAU)).hour
        if not forcer and not noms_sources and not REGISTRE[nom].doit_tourner(heure):
            log.info("[%s] pas cette heure-ci (une exécution sur %d)", nom,
                     REGISTRE[nom].frequence_heures)
            continue
        debut = maintenant()
        source = REGISTRE[nom](pages_max=pages)
        resultat = source.executer(transactions)
        nouvelles = 0
        try:
            nouvelles = integrateur.integrer(resultat)
        except Exception as e:  # noqa: BLE001
            log.exception("[%s] erreur pendant l'intégration", nom)
            resultat.ok = False
            resultat.erreur = f"intégration : {type(e).__name__}: {e}"
        stockage.enregistrer_collecte(nom, debut, resultat.ok, len(resultat.annonces), nouvelles,
                                      resultat.erreur, resultat.bloque, resultat.duree_s)
        if resultat.ok:
            log.info("[%s] OK : %d annonces vues, %d nouvelles, %d requêtes, %.0f s",
                     nom, len(resultat.annonces), nouvelles, resultat.nb_requetes,
                     resultat.duree_s)
        else:
            echecs = stockage.echecs_consecutifs(nom)
            log.error("[%s] ÉCHEC (%d consécutif(s)) : %s", nom, echecs, resultat.erreur)
            if echecs == config.ECHECS_AVANT_ALERTE:
                notificateur.source_en_panne(nom, resultat.erreur, echecs)

    nb = stockage.desactiver_anciennes(config.JOURS_AVANT_DESACTIVATION)
    if nb:
        log.info("%d annonce(s) désactivée(s) (plus vues depuis %d jours)",
                 nb, config.JOURS_AVANT_DESACTIVATION)
    log.info("Terminé. %d notification(s) envoyée(s).", notificateur.nb_envoyees)
    return code_sortie


def main(argv: list[str] | None = None) -> int:
    parseur = argparse.ArgumentParser(description="Collecteur d'annonces ImmoJ")
    parseur.add_argument("--source", action="append", dest="sources",
                         help="ne lancer que cette source (répétable)")
    parseur.add_argument("--pages", type=int, default=None,
                         help=f"pages de résultats par source (défaut {config.PAGES_MAX})")
    parseur.add_argument("--transaction", choices=("location", "achat"), action="append",
                         help="limiter à location ou achat (répétable)")
    parseur.add_argument("--simulation", action="store_true",
                         help="n'écrit rien dans Supabase, n'envoie rien à ntfy")
    parseur.add_argument("--forcer", action="store_true",
                         help="ignorer la plage horaire 7h–23h")
    parseur.add_argument("--verbeux", action="store_true")
    args = parseur.parse_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbeux else logging.INFO,
                        format="%(asctime)s %(levelname)-7s %(message)s", datefmt="%H:%M:%S")
    return executer(args.sources, args.pages, args.simulation, args.forcer,
                    tuple(args.transaction) if args.transaction else None)


if __name__ == "__main__":
    sys.exit(main())
