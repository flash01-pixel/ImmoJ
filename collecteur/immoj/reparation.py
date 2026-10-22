"""Réparation de la base : orphelines, doublons, annonceur, types, précision.

Tourne à chaque passage profond (et avec `--reparer`). Idempotent : quand il n'y a
rien à corriger, rien n'est écrit. Les deux stockages exposent la même petite
interface : `annonces_actives_brutes()`, `modifier_annonce()`, `desactiver()`,
`fusionner_annonces()`.
"""

from __future__ import annotations

import logging
from collections import defaultdict

from .dedoublonnage import Candidat, correspondance_forte, score
from .geocodage import Geocodeur
from .modele import deviner_annonceur, deviner_type

log = logging.getLogger(__name__)


def annonceur_depuis_sources(ligne: dict, agences_connues: frozenset[str] = frozenset()) -> str:
    """Déduit l'annonceur d'un bien d'après ses publications (source + nom affiché)."""
    avis = {deviner_annonceur(s.get("source") or "", s.get("agence"), agences_connues)
            for s in ligne.get("sources") or []}
    if "notaire" in avis:
        return "notaire"
    if "agence" in avis:
        return "agence"
    if "particulier" in avis:
        return "particulier"
    if ligne.get("agence") and str(ligne["agence"]).upper() != "PRIVATE":
        return "agence"
    return "inconnu"


def precision_depuis_position(ligne: dict, geocodeur: Geocodeur) -> str | None:
    """« commune » si la position est le centre d'une commune du référentiel, sinon « exacte »."""
    lat, lon = ligne.get("latitude"), ligne.get("longitude")
    if lat is None or lon is None:
        return None
    for c in geocodeur.communes:
        if abs(c.lat - lat) < 1e-6 and abs(c.lon - lon) < 1e-6:
            return "commune"
    return "exacte"


def _candidat(ligne: dict) -> Candidat:
    return Candidat(id=ligne["id"], transaction=ligne["transaction"], commune=ligne.get("commune"),
                    code_postal=ligne.get("code_postal"), prix=ligne.get("prix"),
                    chambres=ligne.get("chambres"), surface=ligne.get("surface"),
                    titre=ligne.get("titre") or "", description=ligne.get("description") or "",
                    agence=ligne.get("agence"),
                    reference=(ligne.get("details") or {}).get("Référence"))


def doublons_structurels(lignes: list[dict]) -> list[tuple[dict, dict]]:
    """Paires (cible, doublon) : même bien publié par des sources DIFFÉRENTES, reconnu
    par la correspondance structurée forte ou la référence (jamais par le seul texte).
    La cible est la plus ancienne."""
    paires: list[tuple[dict, dict]] = []
    groupes: dict[tuple, list[dict]] = defaultdict(list)
    for ligne in lignes:
        cle = (ligne["transaction"], ligne.get("commune") or ligne.get("code_postal"))
        groupes[cle].append(ligne)
    absorbees: set[str] = set()
    for groupe in groupes.values():
        groupe.sort(key=lambda x: x.get("premiere_detection") or "")
        for i, cible in enumerate(groupe):
            if cible["id"] in absorbees:
                continue
            sources_cible = {s["source"] for s in cible.get("sources") or []}
            for autre in groupe[i + 1:]:
                if autre["id"] in absorbees:
                    continue
                sources_autre = {s["source"] for s in autre.get("sources") or []}
                if sources_cible & sources_autre:
                    continue   # même site : deux biens distincts (appartements jumeaux)
                ca, cb = _candidat(autre), _candidat(cible)
                if score(ca, cb) >= 100 or correspondance_forte(ca, cb):
                    paires.append((cible, autre))
                    absorbees.add(autre["id"])
                    sources_cible |= sources_autre
    return paires


def reparer_stockage(stockage, geocodeur: Geocodeur) -> dict[str, int]:
    """Applique toutes les réparations ; renvoie un bilan chiffré."""
    bilan = {"orphelines": 0, "doublons": 0, "annonceur": 0, "types": 0, "precision": 0}
    lignes = stockage.annonces_actives_brutes()
    try:
        from .sources.deuxiememain import agences_connues
        connues = agences_connues()
    except Exception:  # noqa: BLE001
        connues = frozenset()

    # 1. Orphelines (C4) : actives sans aucune publication → désactivées.
    orphelines = [x["id"] for x in lignes if not x.get("sources")]
    if orphelines:
        stockage.desactiver(orphelines)
        bilan["orphelines"] = len(orphelines)
        lignes = [x for x in lignes if x.get("sources")]

    # 2. Annonceur, « PRIVATE », type « autre », précision de la position.
    for x in lignes:
        maj: dict = {}
        if x.get("annonceur", "inconnu") == "inconnu":
            ann = annonceur_depuis_sources(x, connues)
            if ann != "inconnu":
                maj["annonceur"] = ann
        annonceur = maj.get("annonceur") or x.get("annonceur")
        agence = x.get("agence")
        if agence and (str(agence).upper() == "PRIVATE" or annonceur == "particulier"):
            maj["agence"] = None
        if x.get("type") == "autre":
            urls = " ".join(s.get("url") or "" for s in x.get("sources") or [])
            devine = deviner_type(x.get("titre"), urls)
            if devine != "autre":
                maj["type"] = devine
        if not x.get("precision_position"):
            precision = precision_depuis_position(x, geocodeur)
            if precision:
                maj["precision_position"] = precision
        if maj:
            stockage.modifier_annonce(x["id"], maj)
            x.update(maj)
            bilan["annonceur"] += "annonceur" in maj or "agence" in maj
            bilan["types"] += "type" in maj
            bilan["precision"] += "precision_position" in maj

    # 3. Doublons entre sources (C5).
    for cible, doublon in doublons_structurels(lignes):
        log.info("fusion : %s absorbe %s (%s %s € %s ch.)", cible["id"], doublon["id"],
                 cible.get("commune"), cible.get("prix"), cible.get("chambres"))
        stockage.fusionner_annonces(cible["id"], doublon["id"])
        bilan["doublons"] += 1
    return bilan
