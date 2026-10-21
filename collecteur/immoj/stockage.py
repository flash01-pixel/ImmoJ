"""Lecture / écriture dans Supabase via son API REST (PostgREST).

Pas de bibliothèque Supabase : quelques appels HTTP suffisent et on garde la
main sur ce qui est envoyé. Le collecteur utilise la clé `service_role`, qui
contourne les règles de sécurité (RLS) : elle ne doit JAMAIS quitter les
secrets GitHub.

Rappel de la syntaxe PostgREST utilisée ici :
  GET  /rest/v1/table?colonne=eq.valeur&select=a,b     → lecture filtrée
  POST /rest/v1/table  (Prefer: return=representation) → insertion
  PATCH /rest/v1/table?id=eq.X                          → mise à jour
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime, timedelta
from typing import Any

import requests

from .config import Secrets
from .dedoublonnage import Candidat
from .modele import Annonce

log = logging.getLogger(__name__)


def maintenant() -> str:
    return datetime.now(UTC).isoformat()


class Stockage:
    def __init__(self, secrets: Secrets) -> None:
        self.base = f"{secrets.supabase_url}/rest/v1"
        self.session = requests.Session()
        self.session.headers.update({
            "apikey": secrets.supabase_cle_service,
            "Authorization": f"Bearer {secrets.supabase_cle_service}",
            "Content-Type": "application/json",
        })

    # --- primitives -------------------------------------------------------
    def _lire(self, table: str, **params: str) -> list[dict]:
        r = self.session.get(f"{self.base}/{table}", params=params, timeout=30)
        r.raise_for_status()
        return r.json()

    def _inserer(self, table: str, lignes: list[dict] | dict, *,
                 conflit: str | None = None, ignorer_doublons: bool = False) -> list[dict]:
        prefer = ["return=representation"]
        params = {}
        if conflit:
            params["on_conflict"] = conflit
            prefer.append("resolution=ignore-duplicates" if ignorer_doublons
                          else "resolution=merge-duplicates")
        r = self.session.post(f"{self.base}/{table}", json=lignes, params=params,
                              headers={"Prefer": ",".join(prefer)}, timeout=30)
        if r.status_code >= 400:
            raise RuntimeError(f"Supabase {table} : HTTP {r.status_code} {r.text[:300]}")
        return r.json() if r.text else []

    def _modifier(self, table: str, valeurs: dict, **filtres: str) -> None:
        r = self.session.patch(f"{self.base}/{table}", json=valeurs, params=filtres,
                               headers={"Prefer": "return=minimal"}, timeout=30)
        if r.status_code >= 400:
            raise RuntimeError(f"Supabase {table} : HTTP {r.status_code} {r.text[:300]}")

    # --- recherches sauvegardées -----------------------------------------
    def lire_recherches(self) -> list[dict]:
        return self._lire("recherches_sauvegardees", actif="eq.true", select="*")

    # --- annonces et sources ---------------------------------------------
    def sources_connues(self, source: str) -> dict[str, dict]:
        """Toutes les publications déjà vues pour une source : id_externe → ligne."""
        lignes = self._lire("annonce_sources", source=f"eq.{source}",
                            select="id,id_externe,annonce_id,prix_affiche,url")
        return {ligne["id_externe"]: ligne for ligne in lignes}

    def candidats_doublon(self, transaction: str, commune: str | None,
                          code_postal: str | None) -> list[Candidat]:
        """Annonces actives au même endroit, pour le dédoublonnage."""
        conditions = []
        if commune:
            conditions.append(f"commune.eq.{commune}")
        if code_postal:
            conditions.append(f"code_postal.eq.{code_postal}")
        if not conditions:
            return []
        lignes = self._lire(
            "annonces", transaction=f"eq.{transaction}", actif="eq.true",
            **{"or": "(" + ",".join(conditions) + ")"},
            select="id,transaction,commune,code_postal,prix,chambres,surface,titre,description",
        )
        return [
            Candidat(id=ligne["id"], transaction=ligne["transaction"], commune=ligne["commune"],
                     code_postal=ligne["code_postal"], prix=ligne["prix"],
                     chambres=ligne["chambres"], surface=ligne["surface"],
                     titre=ligne["titre"] or "", description=ligne["description"] or "")
            for ligne in lignes
        ]

    def creer_annonce(self, a: Annonce, commune_officielle: str | None,
                      latitude: float | None, longitude: float | None) -> str:
        ligne = {
            "type": a.type, "transaction": a.transaction, "titre": a.titre,
            "prix": a.prix, "charges": a.charges,
            "commune": commune_officielle, "localite": a.commune, "code_postal": a.code_postal,
            "latitude": latitude, "longitude": longitude,
            "chambres": a.chambres, "surface": a.surface, "peb": a.peb,
            "jardin": a.jardin, "terrasse": a.terrasse, "animaux": a.animaux, "meuble": a.meuble,
            "date_publication": a.date_publication.isoformat() if a.date_publication else None,
            "photos": a.photos, "description": a.description,
            "agence": a.agence, "telephone": a.telephone,
            "premiere_detection": maintenant(), "derniere_vue": maintenant(), "actif": True,
        }
        cree = self._inserer("annonces", ligne)[0]
        if a.prix is not None:
            self._inserer("historique_prix", {"annonce_id": cree["id"], "prix": a.prix,
                                              "source": a.source})
        return cree["id"]

    def ajouter_source(self, annonce_id: str, a: Annonce) -> None:
        self._inserer("annonce_sources", {
            "annonce_id": annonce_id, "source": a.source, "id_externe": a.id_externe,
            "url": a.url, "titre": a.titre, "prix_affiche": a.prix,
            "agence": a.agence, "telephone": a.telephone,
            "premiere_vue": maintenant(), "derniere_vue": maintenant(),
        }, conflit="source,id_externe")

    def completer_annonce(self, annonce_id: str, a: Annonce) -> None:
        """Quand un doublon apporte des infos manquantes (photos, PEB...), on complète."""
        existante = self._lire("annonces", id=f"eq.{annonce_id}",
                               select="photos,peb,surface,chambres,telephone,agence,description")
        if not existante:
            return
        e = existante[0]
        maj: dict[str, Any] = {}
        if not e.get("photos") and a.photos:
            maj["photos"] = a.photos
        for champ in ("peb", "surface", "chambres", "telephone", "agence"):
            if e.get(champ) in (None, "") and getattr(a, champ) not in (None, ""):
                maj[champ] = getattr(a, champ)
        if len(a.description) > len(e.get("description") or "") + 100:
            maj["description"] = a.description
        if maj:
            self._modifier("annonces", maj, id=f"eq.{annonce_id}")

    def changer_prix(self, annonce_id: str, source: str, id_externe: str,
                     ancien: int | None, nouveau: int) -> None:
        """Un prix a changé sur une source : historique + mise à jour de la fiche."""
        self._inserer("historique_prix", {"annonce_id": annonce_id, "prix": nouveau,
                                          "source": source})
        maj: dict[str, Any] = {"prix": nouveau, "prix_precedent": ancien}
        if ancien is not None and nouveau < ancien:
            maj["prix_baisse_le"] = maintenant()
        self._modifier("annonces", maj, id=f"eq.{annonce_id}")
        self._modifier("annonce_sources", {"prix_affiche": nouveau},
                       source=f"eq.{source}", id_externe=f"eq.{id_externe}")

    def toucher(self, source: str, ids_externes: list[str], annonce_ids: list[str]) -> None:
        """Marque « vu aujourd'hui » les publications et annonces revues."""
        for i in range(0, len(ids_externes), 100):
            lot = ",".join(f'"{x}"' for x in ids_externes[i:i + 100])
            self._modifier("annonce_sources", {"derniere_vue": maintenant()},
                           source=f"eq.{source}", id_externe=f"in.({lot})")
        uniques = list(dict.fromkeys(annonce_ids))
        for i in range(0, len(uniques), 100):
            lot = ",".join(uniques[i:i + 100])
            self._modifier("annonces", {"derniere_vue": maintenant(), "actif": True},
                           id=f"in.({lot})")

    def desactiver_anciennes(self, jours: int) -> int:
        limite = (datetime.now(UTC) - timedelta(days=jours)).isoformat()
        anciennes = self._lire("annonces", actif="eq.true", derniere_vue=f"lt.{limite}",
                               select="id")
        if anciennes:
            ids = ",".join(ligne["id"] for ligne in anciennes)
            self._modifier("annonces", {"actif": False}, id=f"in.({ids})")
        return len(anciennes)

    def desactiver_hors_province(self) -> int:
        """Désactive les biens dont le code postal connu n'est pas en province (6600–6999)."""
        lignes = self._lire("annonces", actif="eq.true", code_postal="not.is.null",
                            **{"or": "(code_postal.lt.6600,code_postal.gt.6999)"}, select="id")
        if lignes:
            ids = ",".join(ligne["id"] for ligne in lignes)
            self._modifier("annonces", {"actif": False}, id=f"in.({ids})")
        return len(lignes)

    # --- notifications ----------------------------------------------------
    def marquer_notification(self, annonce_id: str, type_notif: str,
                             recherche_id: str | None) -> bool:
        """Enregistre l'envoi ; renvoie False si déjà envoyé (clé unique)."""
        lignes = self._inserer("notifications_envoyees", {
            "annonce_id": annonce_id, "type": type_notif,
            "recherche_id": recherche_id, "envoyee_le": maintenant(),
        }, conflit="annonce_id,type,recherche_cle", ignorer_doublons=True)
        return bool(lignes)

    # --- journal des collectes -------------------------------------------
    def a_deja_reussi(self, source: str) -> bool:
        return bool(self._lire("collectes", source=f"eq.{source}", ok="eq.true",
                               select="id", limit="1"))

    def echecs_consecutifs(self, source: str) -> int:
        lignes = self._lire("collectes", source=f"eq.{source}", select="ok",
                            order="debut.desc", limit="10")
        n = 0
        for ligne in lignes:
            if ligne["ok"]:
                break
            n += 1
        return n

    def enregistrer_collecte(self, source: str, debut: str, ok: bool, nb_trouvees: int,
                             nb_nouvelles: int, erreur: str, bloque: bool, duree_s: float) -> None:
        self._inserer("collectes", {
            "source": source, "debut": debut, "fin": maintenant(), "ok": ok,
            "nb_trouvees": nb_trouvees, "nb_nouvelles": nb_nouvelles,
            "erreur": erreur[:1000] if erreur else None, "bloque": bloque,
            "duree_s": round(duree_s, 1),
        })


class StockageSimulation(Stockage):
    """Mode « simulation » : n'écrit rien, affiche ce qui serait fait.

    Permet de tester le collecteur sans compte Supabase (`--simulation`).
    """

    def __init__(self) -> None:  # noqa: D107 - pas d'appel au parent, pas de session
        self.compteur = 0

    def _lire(self, table, **params):
        return []

    def _inserer(self, table, lignes, *, conflit=None, ignorer_doublons=False):
        self.compteur += 1
        log.info("[simulation] INSERT %s : %s", table, str(lignes)[:160])
        return [{"id": f"sim-{self.compteur}"}]

    def _modifier(self, table, valeurs, **filtres):
        log.info("[simulation] UPDATE %s %s : %s", table, filtres, str(valeurs)[:120])

    def desactiver_hors_province(self):
        return 0
