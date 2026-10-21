"""Géocodage : trouver la commune officielle et des coordonnées GPS.

1. On cherche d'abord dans communes.yaml (commune, localité ou code postal).
   C'est instantané, hors-ligne et couvre toute la province.
2. Si rien ne correspond et qu'un code postal est hors province, l'annonce est
   quand même gardée (commune inconnue) mais sans coordonnées.

Nominatim (OpenStreetMap) n'est volontairement pas appelé à chaque exécution :
inutile pour une recherche limitée à une province et son usage est limité.
"""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass

import yaml

from . import config


@dataclass(frozen=True)
class Commune:
    nom: str
    codes: tuple[str, ...]
    lat: float
    lon: float
    localites: tuple[str, ...]


def normaliser(texte: str | None) -> str:
    """Minuscule, sans accents ni ponctuation : « Érezée » → « erezee »."""
    if not texte:
        return ""
    t = unicodedata.normalize("NFKD", str(texte))
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = t.lower().replace("-", " ").replace("'", " ")
    return " ".join(t.split())


class Geocodeur:
    def __init__(self, fichier=config.FICHIER_COMMUNES) -> None:
        with open(fichier, encoding="utf-8") as f:
            brut = yaml.safe_load(f)["communes"]
        self.communes: list[Commune] = [
            Commune(
                nom=c["nom"],
                codes=tuple(str(x) for x in c["codes"]),
                lat=float(c["lat"]),
                lon=float(c["lon"]),
                localites=tuple(c.get("localites", [])),
            )
            for c in brut
        ]
        # Index « nom normalisé » → commune (noms officiels ET localités).
        self._par_nom: dict[str, Commune] = {}
        for c in self.communes:
            self._par_nom[normaliser(c.nom)] = c
            for loc in c.localites:
                self._par_nom.setdefault(normaliser(loc), c)
        self._par_code: dict[str, Commune] = {}
        for c in self.communes:
            for code in c.codes:
                self._par_code.setdefault(code, c)

    def noms(self) -> list[str]:
        return [c.nom for c in self.communes]

    def trouver(self, commune: str | None = None, code_postal: str | None = None) -> Commune | None:
        """Retrouve la commune officielle à partir d'un nom libre et/ou d'un code postal."""
        if commune:
            n = normaliser(commune)
            if n in self._par_nom:
                return self._par_nom[n]
            # Nom composé : « Marche-en-Famenne (Marloie) », « Arlon Centre »...
            for mot_cle, c in self._par_nom.items():
                if mot_cle and (n.startswith(mot_cle + " ") or f" {mot_cle}" in f" {n} "):
                    return c
        if code_postal and str(code_postal) in self._par_code:
            return self._par_code[str(code_postal)]
        return None

    def est_dans_la_province(self, commune: str | None, code_postal: str | None) -> bool:
        return self.trouver(commune, code_postal) is not None
