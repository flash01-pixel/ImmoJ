"""Rapprochement approximatif : le même bien publié sur plusieurs sites.

Deux annonces sont considérées comme le même bien si TOUTES ces conditions
tiennent :
- même transaction (location / achat) ;
- même commune officielle (ou même code postal) ;
- prix proches (± TOLERANCE_PRIX) ;
- même nombre de chambres, ou l'un des deux inconnu ;
- surfaces proches (± TOLERANCE_SURFACE), ou l'une inconnue ;
- titre OU début de description suffisamment similaires (rapidfuzz).

Les seuils sont dans config.py. En cas de doute (peu d'informations des deux
côtés), on exige une similarité de texte plus forte pour éviter de fusionner
deux appartements différents du même immeuble.
"""

from __future__ import annotations

from dataclasses import dataclass

from rapidfuzz import fuzz

from . import config
from .geocodage import normaliser


@dataclass
class Candidat:
    """Vue minimale d'une annonce déjà en base, pour la comparaison."""

    id: str
    transaction: str
    commune: str | None
    code_postal: str | None
    prix: int | None
    chambres: int | None
    surface: int | None
    titre: str
    description: str


def _proche(a: int | None, b: int | None, tolerance: float) -> bool | None:
    """True/False si comparables, None si l'une des valeurs manque."""
    if a is None or b is None:
        return None
    if a == 0 or b == 0:
        return a == b
    return abs(a - b) / max(a, b) <= tolerance


def _similarite(a: str, b: str) -> float:
    a, b = normaliser(a), normaliser(b)
    if not a or not b:
        return 0.0
    # token_sort_ratio : « appartement à arlon » n'est pas identique à
    # « appartement 2 chambres à arlon » (contrairement à token_set_ratio).
    return fuzz.token_sort_ratio(a, b)


def score(nouvelle: Candidat, existante: Candidat) -> float:
    """Retourne 0 si incompatibles, sinon une similarité de texte (0-100)."""
    if nouvelle.transaction != existante.transaction:
        return 0.0
    meme_lieu = (
        (nouvelle.commune and existante.commune
         and normaliser(nouvelle.commune) == normaliser(existante.commune))
        or (nouvelle.code_postal and nouvelle.code_postal == existante.code_postal)
    )
    if not meme_lieu:
        return 0.0
    if _proche(nouvelle.prix, existante.prix, config.TOLERANCE_PRIX) is False:
        return 0.0
    if (nouvelle.chambres is not None and existante.chambres is not None
            and nouvelle.chambres != existante.chambres):
        return 0.0
    if _proche(nouvelle.surface, existante.surface, config.TOLERANCE_SURFACE) is False:
        return 0.0

    sim_titre = _similarite(nouvelle.titre, existante.titre)
    sim_desc = _similarite(nouvelle.description[:300], existante.description[:300])
    sim = max(sim_titre, sim_desc)

    # Peu d'informations structurées des deux côtés → on est plus exigeant.
    infos = sum(x is not None for x in (nouvelle.chambres, nouvelle.surface,
                                         existante.chambres, existante.surface))
    seuil = config.SEUIL_SIMILARITE_TEXTE + (15 if infos < 2 else 0)
    return sim if sim >= seuil else 0.0


def trouver_doublon(nouvelle: Candidat, candidats: list[Candidat]) -> Candidat | None:
    """Renvoie le meilleur candidat compatible, ou None."""
    meilleur, meilleur_score = None, 0.0
    for c in candidats:
        s = score(nouvelle, c)
        if s > meilleur_score:
            meilleur, meilleur_score = c, s
    return meilleur
