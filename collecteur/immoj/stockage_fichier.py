"""Stockage « fichier » : toute la base dans un JSON, sans Supabase.

Même interface que `Stockage` (Supabase), mais tout est en mémoire et
sauvegardé dans un fichier (`donnees/base.json`) que le workflow de collecte
pousse sur la branche `donnees` du dépôt. L'appli Android lit l'export
`annonces.json` (annonces actives + communes + état des sources) et gère
elle-même statuts, notes, recherches sauvegardées et notifications.
"""

from __future__ import annotations

import json
import logging
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

from . import config
from .dedoublonnage import Candidat
from .geocodage import Geocodeur
from .modele import Annonce, deviner_annonceur
from .stockage import a_ete_revue_en_profondeur, complements_connus, fusionner_photos, maintenant

log = logging.getLogger(__name__)

VERSION_FORMAT = 1


class StockageFichier:
    def __init__(self, chemin: str | Path) -> None:
        self.chemin = Path(chemin)
        self.base: dict[str, Any] = {
            "version": VERSION_FORMAT, "annonces": {}, "sources": {},
            "historique_prix": [], "collectes": [],
        }
        if self.chemin.exists():
            try:
                self.base.update(json.loads(self.chemin.read_text(encoding="utf-8")))
            except (OSError, json.JSONDecodeError) as e:
                log.warning("base JSON illisible (%s) : on repart de zéro", e)
        self.annonces: dict[str, dict] = self.base["annonces"]
        # clé "source:id_externe" → {annonce_id, prix_affiche, url, ...}
        self.sources: dict[str, dict] = self.base["sources"]
        self._migrer()

    def _migrer(self) -> None:
        """Bases écrites avant le lot 4 : champs annonceur / précision absents."""
        par_annonce: dict[str, list[dict]] = {}
        for src in self.sources.values():
            par_annonce.setdefault(src["annonce_id"], []).append(src)
        for aid, a in self.annonces.items():
            if "annonceur" not in a:
                avis = {deviner_annonceur(x["source"], x.get("agence"))
                        for x in par_annonce.get(aid, [])}
                if (a.get("agence") or "").upper() == "PRIVATE":
                    avis.add("particulier")
                a["annonceur"] = ("notaire" if "notaire" in avis else "agence" if "agence" in avis
                                  else "particulier" if "particulier" in avis else "inconnu")
            if a.get("annonceur") == "particulier" or (a.get("agence") or "").upper() == "PRIVATE":
                a["agence"] = None
            a.setdefault("precision_position", None)

    def _par_annonce(self) -> dict[str, list[dict]]:
        """Index annonce_id → publications (C9 : calculé une fois, pas par annonce)."""
        index: dict[str, list[dict]] = {}
        for src in self.sources.values():
            index.setdefault(src["annonce_id"], []).append(src)
        return index

    # --- persistance -----------------------------------------------------
    def sauvegarder(self) -> None:
        self.chemin.parent.mkdir(parents=True, exist_ok=True)
        # On ne garde que 30 jours de journal des collectes.
        limite = (datetime.now(UTC) - timedelta(days=30)).isoformat()
        self.base["collectes"] = [c for c in self.base["collectes"] if c["debut"] >= limite]
        self.chemin.write_text(json.dumps(self.base, ensure_ascii=False, separators=(",", ":")),
                               encoding="utf-8")

    def exporter(self, chemin: str | Path, geocodeur: Geocodeur | None = None) -> int:
        """Écrit l'export lu par l'appli : annonces actives, communes, état des sources."""
        geocodeur = geocodeur or Geocodeur()
        actives = []
        par_annonce = self._par_annonce()
        historiques: dict[str, list[dict]] = {}
        for h in self.base["historique_prix"]:
            historiques.setdefault(h["annonce_id"], []).append(h)
        for aid, a in self.annonces.items():
            if not a.get("actif", True):
                continue
            pubs = sorted(par_annonce.get(aid, []), key=lambda s: s["premiere_vue"])
            historique = historiques.get(aid, [])
            actives.append({
                **a, "id": aid,
                "photo": (a.get("photos") or [None])[0],
                "sources": [s["source"] for s in pubs],
                "urls": [s["url"] for s in pubs],
                "historique_prix": [{"prix": h["prix"], "date": h["date"]} for h in historique],
            })
        actives.sort(key=lambda a: a["premiere_detection"], reverse=True)
        export = {
            "genere_le": maintenant(),
            "communes": [{"nom": c.nom, "codes": list(c.codes), "latitude": c.lat,
                          "longitude": c.lon} for c in geocodeur.communes],
            "sources": self.etat_sources(),
            "annonces": actives,
        }
        Path(chemin).parent.mkdir(parents=True, exist_ok=True)
        Path(chemin).write_text(json.dumps(export, ensure_ascii=False, separators=(",", ":")),
                                encoding="utf-8")
        return len(actives)

    def etat_sources(self) -> list[dict]:
        """Équivalent de la vue v_etat_sources."""
        par_source: dict[str, dict] = {}
        for c in sorted(self.base["collectes"], key=lambda c: c["debut"]):
            e = par_source.setdefault(c["source"],
                                      {"source": c["source"], "derniere_reussite": None})
            e.update({"derniere_collecte": c["fin"], "derniere_ok": c["ok"], "bloque": c["bloque"],
                      "erreur": c["erreur"], "nb_trouvees": c["nb_trouvees"],
                      "nb_nouvelles": c["nb_nouvelles"],
                      "nb_disponibles": c.get("nb_disponibles"),
                      "nb_particuliers": c.get("nb_particuliers", 0),
                      "profond": c.get("profond", False)})
            if c["ok"]:
                e["derniere_reussite"] = c["fin"]
                if c.get("profond"):
                    e["derniere_profonde"] = c["fin"]
        actives: dict[str, set] = {}
        for s in self.sources.values():
            if self.annonces.get(s["annonce_id"], {}).get("actif", True):
                actives.setdefault(s["source"], set()).add(s["annonce_id"])
        for nom, e in par_source.items():
            e["nb_actives"] = len(actives.get(nom, ()))
        return sorted(par_source.values(), key=lambda e: e["source"])

    # --- interface Stockage ----------------------------------------------
    def lire_recherches(self) -> list[dict]:
        return []   # les recherches sauvegardées vivent dans l'appli

    def sources_connues(self, source: str) -> dict[str, dict]:
        connues = {}
        for s in self.sources.values():
            if s["source"] == source:
                # « annonce » pointe sur la fiche elle-même : l'intégrateur y lit (et y
                # écrit via modifier_annonce) type, date de publication, annonceur…
                connues[s["id_externe"]] = {**s, "annonce": self.annonces.get(s["annonce_id"])}
        return connues

    def modifier_annonce(self, annonce_id: str, maj: dict) -> None:
        a = self.annonces.get(annonce_id)
        if a:
            a.update(maj)

    def annonces_connues(self, source: str) -> dict[str, dict]:
        """id_externe → champs de l'annonce (prix, chambres...) déjà en base pour une source."""
        return {s["id_externe"]: self.annonces[s["annonce_id"]]
                for s in self.sources.values()
                if s["source"] == source and s["annonce_id"] in self.annonces}

    def candidats_doublon(self, transaction: str, commune: str | None,
                          code_postal: str | None) -> list[Candidat]:
        return [
            Candidat(id=aid, transaction=a["transaction"], commune=a["commune"],
                     code_postal=a["code_postal"], prix=a["prix"], chambres=a["chambres"],
                     surface=a["surface"], titre=a["titre"] or "",
                     description=a["description"] or "", agence=a.get("agence"),
                     reference=(a.get("details") or {}).get("Référence"))
            for aid, a in self.annonces.items()
            if a.get("actif", True) and a["transaction"] == transaction
            and ((commune and a["commune"] == commune)
                 or (code_postal and a["code_postal"] == code_postal))
        ]

    def creer_annonce(self, a: Annonce, commune_officielle: str | None,
                      latitude: float | None, longitude: float | None) -> str:
        aid = uuid.uuid4().hex
        self.annonces[aid] = {
            "type": a.type, "transaction": a.transaction, "titre": a.titre,
            "prix": a.prix, "prix_precedent": None, "prix_baisse_le": None, "charges": a.charges,
            "commune": commune_officielle, "localite": a.commune, "code_postal": a.code_postal,
            "latitude": latitude, "longitude": longitude,
            "chambres": a.chambres, "surface": a.surface, "peb": a.peb,
            "jardin": a.jardin, "terrasse": a.terrasse, "animaux": a.animaux, "meuble": a.meuble,
            "date_publication": a.date_publication.isoformat() if a.date_publication else None,
            "photos": a.photos, "description": a.description, "details": dict(a.details),
            "agence": a.agence, "telephone": a.telephone, "annonceur": a.annonceur,
            "precision_position": ("exacte" if a.latitude is not None
                                   else "commune" if latitude is not None else None),
            "premiere_detection": maintenant(), "derniere_vue": maintenant(), "actif": True,
        }
        if a.prix is not None:
            self.base["historique_prix"].append({"annonce_id": aid, "prix": a.prix,
                                                 "source": a.source, "date": maintenant()})
        return aid

    def ajouter_source(self, annonce_id: str, a: Annonce) -> None:
        cle = f"{a.source}:{a.id_externe}"
        existante = self.sources.get(cle)
        self.sources[cle] = {
            "annonce_id": annonce_id, "source": a.source, "id_externe": a.id_externe,
            "url": a.url, "titre": a.titre, "prix_affiche": a.prix,
            "agence": a.agence, "telephone": a.telephone,
            "date_publication": a.date_publication.isoformat() if a.date_publication else None,
            "premiere_vue": existante["premiere_vue"] if existante else maintenant(),
            "derniere_vue": maintenant(),
        }

    def completer_annonce(self, annonce_id: str, a: Annonce) -> None:
        e = self.annonces.get(annonce_id)
        if not e:
            return
        e["photos"] = fusionner_photos(e.get("photos") or [], a.photos)
        e["details"] = {**a.details, **(e.get("details") or {})}
        for champ in ("peb", "surface", "chambres", "telephone", "agence"):
            if e.get(champ) in (None, "") and getattr(a, champ) not in (None, ""):
                e[champ] = getattr(a, champ)
        if len(a.description) > len(e.get("description") or "") + 100:
            e["description"] = a.description
        e.update(complements_connus(e, a))

    def changer_prix(self, annonce_id: str, source: str, id_externe: str,
                     ancien: int | None, nouveau: int) -> None:
        self.base["historique_prix"].append({"annonce_id": annonce_id, "prix": nouveau,
                                             "source": source, "date": maintenant()})
        a = self.annonces[annonce_id]
        a["prix"], a["prix_precedent"] = nouveau, ancien
        if ancien is not None and nouveau < ancien:
            a["prix_baisse_le"] = maintenant()
        self.sources[f"{source}:{id_externe}"]["prix_affiche"] = nouveau

    def toucher(self, source: str, ids_externes: list[str], annonce_ids: list[str]) -> None:
        for ide in ids_externes:
            s = self.sources.get(f"{source}:{ide}")
            if s:
                s["derniere_vue"] = maintenant()
        for aid in annonce_ids:
            if aid in self.annonces:
                self.annonces[aid]["derniere_vue"] = maintenant()
                self.annonces[aid]["actif"] = True

    def dernieres_profondes(self) -> dict[str, str]:
        """source → fin du dernier passage profond réussi."""
        dernieres: dict[str, str] = {}
        for c in self.base["collectes"]:
            if c["ok"] and c.get("profond"):
                dernieres[c["source"]] = max(dernieres.get(c["source"], ""), c["fin"])
        return dernieres

    def desactiver_anciennes(self, jours: int) -> int:
        limite = (datetime.now(UTC) - timedelta(days=jours)).isoformat()
        profondes = self.dernieres_profondes()
        par_annonce = self._par_annonce()
        n = 0
        for aid, a in self.annonces.items():
            if not a.get("actif", True) or a["derniere_vue"] >= limite:
                continue
            if a_ete_revue_en_profondeur(a["derniere_vue"], par_annonce.get(aid, []), profondes):
                a["actif"] = False
                n += 1
        return n

    def desactiver(self, ids: list[str]) -> None:
        for aid in ids:
            if aid in self.annonces:
                self.annonces[aid]["actif"] = False

    def annonces_actives_brutes(self) -> list[dict]:
        par_annonce = self._par_annonce()
        return [{**a, "id": aid, "sources": par_annonce.get(aid, [])}
                for aid, a in self.annonces.items() if a.get("actif", True)]

    def fusionner_annonces(self, cible_id: str, doublon_id: str) -> None:
        """Le doublon disparaît : ses publications, photos, détails et historique
        de prix passent à la cible."""
        cible, doublon = self.annonces.get(cible_id), self.annonces.get(doublon_id)
        if not cible or not doublon:
            return
        for src in self.sources.values():
            if src["annonce_id"] == doublon_id:
                src["annonce_id"] = cible_id
        for h in self.base["historique_prix"]:
            if h["annonce_id"] == doublon_id:
                h["annonce_id"] = cible_id
        cible["photos"] = fusionner_photos(cible.get("photos") or [], doublon.get("photos") or [])
        cible["details"] = {**(doublon.get("details") or {}), **(cible.get("details") or {})}
        for champ in ("peb", "surface", "chambres", "telephone", "agence", "date_publication",
                      "charges"):
            if cible.get(champ) in (None, "") and doublon.get(champ) not in (None, ""):
                cible[champ] = doublon[champ]
        if cible.get("annonceur", "inconnu") == "inconnu":
            cible["annonceur"] = doublon.get("annonceur", "inconnu")
        if len(doublon.get("description") or "") > len(cible.get("description") or "") + 100:
            cible["description"] = doublon["description"]
        doublon["actif"] = False

    def desactiver_hors_province(self) -> int:
        n = 0
        for a in self.annonces.values():
            cp = a.get("code_postal")
            if a.get("actif", True) and cp and str(cp).isdigit() and not 6600 <= int(cp) <= 6999:
                a["actif"] = False
                n += 1
        return n

    def marquer_notification(self, annonce_id: str, type_notif: str,
                             recherche_id: str | None) -> bool:
        return False   # les notifications sont gérées par l'appli en mode fichier

    def a_deja_reussi(self, source: str) -> bool:
        return any(c["source"] == source and c["ok"] for c in self.base["collectes"])

    def echecs_consecutifs(self, source: str) -> int:
        n = 0
        # Le journal est dans l'ordre d'exécution : on le remonte depuis la fin.
        for c in reversed([c for c in self.base["collectes"] if c["source"] == source]):
            if c["ok"]:
                break
            n += 1
        return n

    def enregistrer_collecte(self, source: str, debut: str, ok: bool, nb_trouvees: int,
                             nb_nouvelles: int, erreur: str, bloque: bool, duree_s: float,
                             profond: bool = False, nb_disponibles: int | None = None,
                             nb_particuliers: int = 0) -> None:
        self.base["collectes"].append({
            "source": source, "debut": debut, "fin": maintenant(), "ok": ok,
            "nb_trouvees": nb_trouvees, "nb_nouvelles": nb_nouvelles,
            "erreur": erreur[:1000] if erreur else None, "bloque": bloque,
            "duree_s": round(duree_s, 1), "profond": profond,
            "nb_disponibles": nb_disponibles, "nb_particuliers": nb_particuliers,
        })
        self.sauvegarder()   # l'export (annonces.json) n'a lieu qu'une fois, en fin de passage


__all__ = ["StockageFichier", "config"]
