"""Point d'entrée du collecteur.

    python -m immoj.main --fichier donnees/base.json   # stockage JSON (sans Supabase)
    python -m immoj.main                               # Supabase (variables d'environnement)
    python -m immoj.main --source immovlan --pages 6
    python -m immoj.main --simulation                  # n'écrit rien : affiche seulement
    python -m immoj.main --forcer                      # ignore la plage horaire 7h–23h

Deux stockages possibles, cumulables : Supabase (si SUPABASE_URL et
SUPABASE_SERVICE_KEY sont définis) et le fichier JSON (--fichier). Chaque
source n'est visitée qu'une fois ; ses annonces sont intégrées dans chaque
stockage (géocodage, dédoublonnage, prix, nouveautés), puis les notifications
ntfy partent pour le stockage Supabase.
"""

from __future__ import annotations

import argparse
import logging
import sys
import time
from datetime import datetime
from zoneinfo import ZoneInfo

from . import config
from .config import Secrets, charger_secrets, supabase_configure
from .dedoublonnage import Candidat, trouver_doublon
from .geocodage import Geocodeur
from .modele import Annonce
from .notifications import Notificateur
from .recherches import correspond
from .sources import REGISTRE, ResultatCollecte
from .stockage import Stockage, StockageSimulation, maintenant
from .stockage_fichier import StockageFichier

log = logging.getLogger("immoj")

# Sans aucune recherche sauvegardée, on notifie toutes les nouveautés de cette
# transaction (None pour ne rien notifier).
TRANSACTION_PAR_DEFAUT = "location"


def dans_la_plage_horaire(instant: datetime | None = None) -> bool:
    """Le cron GitHub est en UTC : on vérifie l'heure de Bruxelles nous-mêmes."""
    local = (instant or datetime.now(ZoneInfo(config.FUSEAU))).astimezone(ZoneInfo(config.FUSEAU))
    return config.HEURE_DEBUT <= local.hour <= config.HEURE_FIN


def hors_province(code_postal: str | None) -> bool:
    """True si le code postal est connu et n'est pas en province de Luxembourg (6600–6999)."""
    if not code_postal or not str(code_postal).isdigit():
        return False
    return not 6600 <= int(code_postal) <= 6999


class Integrateur:
    """Intègre les annonces d'une source dans la base et déclenche les notifications."""

    def __init__(self, stockage: Stockage, geocodeur: Geocodeur, notificateur: Notificateur,
                 recherches: list[dict]) -> None:
        self.stockage = stockage
        self.geocodeur = geocodeur
        self.notificateur = notificateur
        self.recherches = recherches
        self.recherches_notif = [r for r in recherches if r.get("notifier", True)]
        #: instant (time.monotonic) au-delà duquel on arrête d'intégrer ; None = sans limite
        self.date_limite: float | None = None
        #: vrai si le dernier passage a été interrompu par le budget de temps
        self.incomplet = False

    def _candidats(self, cache: dict, a: Annonce, nom_commune: str | None) -> list[Candidat]:
        """Candidats au dédoublonnage, lus une fois par (transaction, commune) et par passage :
        une requête Supabase par commune au lieu d'une par annonce."""
        cle = (a.transaction, nom_commune, a.code_postal if not nom_commune else None)
        if cle not in cache:
            cache[cle] = self.stockage.candidats_doublon(a.transaction, nom_commune, a.code_postal)
        return cache[cle]

    def _situer(self, a: Annonce) -> tuple[bool, str | None, float | None, float | None]:
        """(en province ?, commune officielle, latitude, longitude)."""
        commune = self.geocodeur.trouver(a.commune, a.code_postal, a.latitude, a.longitude)
        if commune is None and hors_province(a.code_postal):
            return False, None, None, None
        if a.latitude is not None and a.longitude is not None \
                and not self.geocodeur.dans_le_contour(a.latitude, a.longitude):
            # Coordonnées hors du contour de la province : une localité homonyme ailleurs.
            return False, None, None, None
        nom = commune.nom if commune else None
        if a.latitude is not None and a.longitude is not None:
            return True, nom, a.latitude, a.longitude
        if commune:
            return True, nom, commune.lat, commune.lon
        return True, nom, None, None

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
        vus_ce_passage: set[str] = set()
        cache_candidats: dict = {}
        nouvelles = 0
        self.incomplet = False

        for a in resultat.annonces:
            if self.date_limite is not None and time.monotonic() > self.date_limite:
                log.warning("[%s] budget de temps épuisé : %d annonce(s) attendront le prochain "
                            "passage", source, len(resultat.annonces) - len(vus_ce_passage))
                self.incomplet = True
                break
            # C4 : un identifiant répété dans le même passage (page de résultats
            # identique) ne doit jamais créer une annonce jumelle.
            if a.id_externe in vus_ce_passage:
                continue
            vus_ce_passage.add(a.id_externe)
            en_province, nom_commune, lat, lon = self._situer(a)
            if not en_province:
                log.debug("[%s] %s ignorée : %s %s hors province", source, a.id_externe,
                          a.code_postal, a.commune)
                continue

            connue = connues.get(a.id_externe)
            if connue:
                vues_ids_externes.append(a.id_externe)
                vues_annonce_ids.append(connue["annonce_id"])
                ancien = connue.get("prix_affiche")
                if a.prix is not None and a.prix != ancien:
                    log.info("[%s] prix modifié %s : %s → %s", source, a.id_externe, ancien, a.prix)
                    self.stockage.changer_prix(connue["annonce_id"], source, a.id_externe,
                                               ancien, a.prix)
                    connue["prix_affiche"] = a.prix
                    if ancien is not None and a.prix < ancien and not import_initial:
                        self._notifier_baisse(connue["annonce_id"], a, ancien, nom_commune,
                                              lat, lon)
                self._completer_connue(connue, a)
                continue

            # Jamais vue sur cette source : est-ce un bien déjà connu ailleurs ?
            candidat = Candidat(id="", transaction=a.transaction, commune=nom_commune,
                                code_postal=a.code_postal, prix=a.prix, chambres=a.chambres,
                                surface=a.surface, titre=a.titre, description=a.description,
                                agence=a.agence, reference=a.reference)
            candidats = [c for c in self._candidats(cache_candidats, a, nom_commune)
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
                if not import_initial and not self._trop_ancienne_pour_notifier(resultat, a):
                    self._notifier_nouvelle(annonce_id, a, nom_commune, lat, lon)
            deja_sur_cette_source.add(annonce_id)
            # `connues` suit au fil de l'eau : une 2e occurrence plus loin dans le
            # passage est traitée comme connue, jamais comme une nouvelle fiche.
            connues[a.id_externe] = {"annonce_id": annonce_id, "prix_affiche": a.prix,
                                     "url": a.url}
            vues_ids_externes.append(a.id_externe)
            vues_annonce_ids.append(annonce_id)

        self.stockage.toucher(source, vues_ids_externes, vues_annonce_ids)
        if import_initial and resultat.annonces:
            self.notificateur.import_initial(source, len(resultat.annonces))
        return nouvelles

    def _completer_connue(self, connue: dict, a: Annonce) -> None:
        """Annonce déjà connue : on récupère ce qui manquait (date de publication
        de cette source, type enfin deviné, annonceur, position exacte)."""
        fiche = connue.get("annonce")
        modifier = getattr(self.stockage, "modifier_annonce", None)
        if not isinstance(fiche, dict) or modifier is None:
            return
        maj: dict = {}
        if a.date_publication and (not fiche.get("date_publication")
                                   or a.date_publication.isoformat() < fiche["date_publication"]):
            maj["date_publication"] = a.date_publication.isoformat()
        if a.type != "autre" and fiche.get("type") == "autre":
            maj["type"] = a.type
        if a.annonceur != "inconnu" and fiche.get("annonceur", "inconnu") == "inconnu":
            maj["annonceur"] = a.annonceur
            if a.annonceur == "particulier":
                maj["agence"] = None
        if a.latitude is not None and fiche.get("precision_position") != "exacte":
            maj.update({"latitude": a.latitude, "longitude": a.longitude,
                        "precision_position": "exacte"})
        if maj:
            modifier(connue["annonce_id"], maj)
            fiche.update(maj)

    @staticmethod
    def _trop_ancienne_pour_notifier(resultat: ResultatCollecte, a: Annonce) -> bool:
        """Un passage profond déterre des annonces publiées depuis longtemps : pas de
        notification pour celles publiées depuis plus de HEURES_NOTIF_PROFOND."""
        if not resultat.profond or not a.date_publication:
            return False
        age = datetime.now(ZoneInfo(config.FUSEAU)).date() - a.date_publication
        return age.total_seconds() > config.HEURES_NOTIF_PROFOND * 3600

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


def preparer_cibles(simulation: bool, fichier: str | None, geocodeur: Geocodeur) -> list[dict]:
    """Construit la liste des stockages à alimenter, chacun avec son intégrateur."""
    cibles: list[dict] = []
    if simulation:
        secrets = Secrets(supabase_url="", supabase_cle_service="", ntfy_topic="")
        cibles.append({"nom": "simulation", "stockage": StockageSimulation(),
                       "notificateur": Notificateur(secrets, simulation=True)})
    else:
        if supabase_configure():
            secrets = charger_secrets()
            cibles.append({"nom": "supabase", "stockage": Stockage(secrets),
                           "notificateur": Notificateur(secrets)})
        if fichier:
            sans_ntfy = Secrets(supabase_url="", supabase_cle_service="", ntfy_topic="")
            cibles.append({"nom": f"fichier {fichier}", "stockage": StockageFichier(fichier),
                           "notificateur": Notificateur(sans_ntfy)})
    if not cibles:
        raise SystemExit("Aucun stockage : définis SUPABASE_URL/SUPABASE_SERVICE_KEY "
                         "ou passe --fichier donnees/base.json (ou --simulation).")
    for c in cibles:
        recherches = c["stockage"].lire_recherches()
        log.info("[%s] %d recherche(s) sauvegardée(s) active(s)", c["nom"], len(recherches))
        c["integrateur"] = Integrateur(c["stockage"], geocodeur, c["notificateur"], recherches)
    return cibles


def heure_du_passage_profond(instant: datetime | None = None) -> bool:
    """Le passage profond quotidien a lieu à HEURE_PASSAGE_PROFOND (Bruxelles)."""
    local = (instant or datetime.now(ZoneInfo(config.FUSEAU))).astimezone(ZoneInfo(config.FUSEAU))
    return local.hour == config.HEURE_PASSAGE_PROFOND


def executer(noms_sources: list[str] | None, pages: int | None, simulation: bool,
             forcer: bool, transactions: tuple[str, ...] | None,
             fichier: str | None = None, profond: bool | None = None,
             reparer: bool = False) -> int:
    if not forcer and not dans_la_plage_horaire():
        log.info("Hors plage horaire (%dh–%dh Bruxelles) : rien à faire.",
                 config.HEURE_DEBUT, config.HEURE_FIN)
        return 0
    if profond is None:
        profond = heure_du_passage_profond()
    if profond:
        log.info("Passage PROFOND : toutes les pages (jusqu'à %d) de chaque portail.",
                 config.PAGES_PROFOND)

    geocodeur = Geocodeur()
    cibles = preparer_cibles(simulation, fichier, geocodeur)
    log.info("Stockages : %s", ", ".join(c["nom"] for c in cibles))

    noms = noms_sources or list(REGISTRE)
    code_sortie = 0
    limite = time.monotonic() + config.BUDGET_PASSAGE_S
    for c in cibles:
        c["integrateur"].date_limite = limite
    # Les fiches (toutes les photos, détails) se partagent un budget de temps global.
    from .sources.base import Source
    Source.budget_fiches_s = config.BUDGET_FICHES_S
    Source.temps_fiches_s = 0.0
    for nom in noms:
        if nom not in REGISTRE:
            log.error("source inconnue : %s (disponibles : %s)", nom, ", ".join(REGISTRE))
            code_sortie = 2
            continue
        if not REGISTRE[nom].est_configuree():
            log.info("[%s] non configurée (secrets absents) : ignorée", nom)
            continue
        if time.monotonic() > limite:
            log.warning("[%s] budget de temps du passage épuisé : source reportée au prochain "
                        "passage", nom)
            continue
        heure = datetime.now(ZoneInfo(config.FUSEAU)).hour
        if not forcer and not noms_sources and not REGISTRE[nom].doit_tourner(heure):
            log.info("[%s] pas cette heure-ci (une exécution sur %d)", nom,
                     REGISTRE[nom].frequence_heures)
            continue
        debut = maintenant()
        # Premier passage d'une source (jamais réussi nulle part) : profond d'office.
        premier = any(not c["stockage"].a_deja_reussi(nom) for c in cibles)
        source = REGISTRE[nom](pages_max=pages, profond=profond or premier)
        for c in cibles:   # ce qu'on sait déjà de cette source (évite de relire des fiches)
            if isinstance(c["stockage"], StockageFichier):
                source.connues = c["stockage"].annonces_connues(nom)
        resultat = source.executer(transactions)
        log.info("[%s] couverture : %d lue(s) / %s disponible(s), %d de particuliers%s", nom,
                 len(resultat.annonces),
                 resultat.nb_disponibles if resultat.nb_disponibles is not None else "?",
                 resultat.nb_particuliers, " (passage profond)" if resultat.profond else "")
        for c in cibles:
            stockage, notificateur = c["stockage"], c["notificateur"]
            ok, erreur, nouvelles = resultat.ok, resultat.erreur, 0
            try:
                nouvelles = c["integrateur"].integrer(resultat)
            except Exception as e:  # noqa: BLE001
                log.exception("[%s] erreur pendant l'intégration (%s)", nom, c["nom"])
                ok, erreur = False, f"intégration : {type(e).__name__}: {e}"
            # Un passage profond interrompu par le budget n'en est pas un (désactivation).
            profond_complet = resultat.profond and not c["integrateur"].incomplet
            stockage.enregistrer_collecte(nom, debut, ok, len(resultat.annonces), nouvelles,
                                          erreur, resultat.bloque, resultat.duree_s,
                                          profond=profond_complet,
                                          nb_disponibles=resultat.nb_disponibles,
                                          nb_particuliers=resultat.nb_particuliers)
            if isinstance(stockage, StockageFichier):
                stockage.sauvegarder()   # un arrêt brutal (timeout) ne perd pas l'acquis
            if ok:
                log.info("[%s] OK (%s) : %d annonces vues, %d nouvelles, %d requêtes, %.0f s",
                         nom, c["nom"], len(resultat.annonces), nouvelles,
                         resultat.nb_requetes, resultat.duree_s)
            else:
                echecs = stockage.echecs_consecutifs(nom)
                log.error("[%s] ÉCHEC (%d consécutif(s)) : %s", nom, echecs, erreur)
                if echecs == config.ECHECS_AVANT_ALERTE:
                    notificateur.source_en_panne(nom, erreur, echecs)

    for c in cibles:
        nb_hors = c["stockage"].desactiver_hors_province()
        if nb_hors:
            log.info("[%s] %d annonce(s) hors province désactivée(s)", c["nom"], nb_hors)
        # Une annonce n'est désactivée que si un passage PROFOND réussi de chacune de ses
        # sources a eu lieu depuis qu'on l'a vue : un passage léger (3 pages) ne voit pas tout.
        nb = c["stockage"].desactiver_anciennes(config.JOURS_AVANT_DESACTIVATION)
        if nb:
            log.info("[%s] %d annonce(s) désactivée(s) (plus vues depuis %d jours)",
                     c["nom"], nb, config.JOURS_AVANT_DESACTIVATION)
        if reparer or profond:
            from .reparation import reparer_stockage
            bilan = reparer_stockage(c["stockage"], geocodeur)
            log.info("[%s] réparation : %s", c["nom"], bilan)
        if isinstance(c["stockage"], StockageFichier):
            c["stockage"].sauvegarder()
            # C9 : un seul export par passage (et non après chaque source).
            n = c["stockage"].exporter(c["stockage"].chemin.with_name("annonces.json"), geocodeur)
            log.info("[%s] export annonces.json : %d annonces actives", c["nom"], n)
        c["notificateur"].clore()
        log.info("[%s] %d notification(s) envoyée(s).", c["nom"], c["notificateur"].nb_envoyees)
    return code_sortie


def main(argv: list[str] | None = None) -> int:
    parseur = argparse.ArgumentParser(description="Collecteur d'annonces ImmoJ")
    parseur.add_argument("--source", action="append", dest="sources",
                         help="ne lancer que cette source (répétable)")
    parseur.add_argument("--pages", type=int, default=None,
                         help=f"pages de résultats par source (défaut {config.PAGES_MAX})")
    parseur.add_argument("--transaction", choices=("location", "achat"), action="append",
                         help="limiter à location ou achat (répétable)")
    parseur.add_argument("--fichier", default=None,
                         help="stockage JSON (ex. donnees/base.json), cumulable avec Supabase")
    parseur.add_argument("--simulation", action="store_true",
                         help="n'écrit rien dans Supabase, n'envoie rien à ntfy")
    parseur.add_argument("--forcer", action="store_true",
                         help="ignorer la plage horaire 7h–23h")
    parseur.add_argument("--profond", action="store_true",
                         help="passage profond : toutes les pages des portails")
    parseur.add_argument("--leger", action="store_true",
                         help="passage léger même à l'heure du passage profond")
    parseur.add_argument("--reparer", action="store_true",
                         help="nettoyer la base : orphelines, doublons, annonceur, types")
    parseur.add_argument("--verbeux", action="store_true")
    args = parseur.parse_args(argv)

    logging.basicConfig(level=logging.DEBUG if args.verbeux else logging.INFO,
                        format="%(asctime)s %(levelname)-7s %(message)s", datefmt="%H:%M:%S")
    profond = True if args.profond else (False if args.leger else None)
    return executer(args.sources, args.pages, args.simulation, args.forcer,
                    tuple(args.transaction) if args.transaction else None, args.fichier,
                    profond=profond, reparer=args.reparer)


if __name__ == "__main__":
    sys.exit(main())
