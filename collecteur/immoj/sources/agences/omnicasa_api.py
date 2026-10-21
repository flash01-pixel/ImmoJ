"""Module « omnicasa_api » : sites « maison » qui exposent leurs biens Omnicasa
dans une petite API JSON (ex. MCA Immobilier : `/api/biens?goal=0|1`).

Champs observés : id, goal (0 vente, 1 location), etat (nouveau, actif, option,
vendu, loue...), typeNom, titre, ville, cp, adresse, prix, chambres, sdb,
surface, terrain, photo, date. L'URL de la fiche se construit d'après le
gabarit `url_fiche` du YAML ; par défaut celui de MCA :
"{base}/bien-{id}-{type_ville}" (ex. /bien-4991-appartement-marche-en-famenne).
"""

from __future__ import annotations

import re
from datetime import date
from urllib.parse import urljoin

from ...modele import Annonce, deviner_type
from .base import SourceAgences

GOAL = {"location": 1, "achat": 0}
ETATS_FINIS = {"vendu", "loue", "loué", "option", "verkocht", "verhuurd", "optie", "conditions"}


class OmnicasaApi(SourceAgences):
    logiciel = "omnicasa_api"

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        base = agence["url"]
        chemin = options.get("api", "/api/biens?goal={goal}").format(goal=GOAL[transaction])
        r = self.client.get(urljoin(base, chemin), headers={"Accept": "application/json"})
        return analyser_api(r.json(), base, agence, transaction)


def analyser_api(donnees, base: str, agence: dict, transaction: str) -> list[Annonce]:
    options = agence.get("options") or {}
    gabarit = options.get("url_fiche", "{base}/bien-{id}-{type_ville}")
    biens = donnees.get("biens") if isinstance(donnees, dict) else donnees
    hote = re.sub(r"^https?://", "", base).split("/")[0]
    annonces: list[Annonce] = []
    for b in biens or []:
        if not isinstance(b, dict) or not b.get("id"):
            continue
        if b.get("goal") is not None and int(b["goal"]) != GOAL[transaction]:
            continue
        if str(b.get("etat") or "").lower() in ETATS_FINIS:
            continue
        ville = str(b.get("ville") or "").strip()
        titre = str(b.get("titre") or "").strip() or f"{b.get('typeNom') or 'Bien'} à {ville}"
        details: dict = {}
        if b.get("sdb"):
            details["Salles de bain"] = int(b["sdb"])
        if b.get("terrain"):
            details["Terrain"] = f"{int(b['terrain'])} m²"
        if b.get("adresse"):
            details["Adresse"] = str(b["adresse"])
        if b.get("exclusif"):
            details["Exclusivité"] = True
        m = re.match(r"(\d{4})-(\d{2})-(\d{2})", str(b.get("date") or ""))
        annonces.append(Annonce(
            source=f"agences:{OmnicasaApi.logiciel}",
            id_externe=f"{hote}:{b['id']}",
            url=gabarit.format(base=base.rstrip("/"), id=b["id"], slug=_slug(titre),
                               ville=_slug(ville),
                               type_ville=_slug(f"{b.get('typeNom') or ''} {ville}")),
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(f"{b.get('typeNom') or ''} {titre}"),
            prix=int(b["prix"]) if isinstance(b.get("prix"), int | float) and b["prix"] > 0
            else None,
            commune=ville or None,
            code_postal=str(b["cp"]) if b.get("cp") else None,
            chambres=int(b["chambres"]) if b.get("chambres") else None,
            surface=int(b["surface"]) if b.get("surface") else None,
            date_publication=date(int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None,
            photos=[b["photo"]] if b.get("photo") else [],
            description=titre,
            agence=agence["nom"],
            telephone=agence.get("telephone"),
            details=details,
        ))
    return annonces


def _slug(texte: str) -> str:
    import unicodedata
    s = unicodedata.normalize("NFKD", texte)
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", s)).strip("-")
