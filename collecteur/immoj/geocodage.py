"""Géocodage : trouver la commune officielle et des coordonnées GPS.

1. On cherche d'abord dans communes.yaml (commune, localité ou code postal).
   C'est instantané, hors-ligne et couvre toute la province.
2. Les portails écrivent parfois « Hotton + Partie De Soy » ou « Marche-en-Famenne
   (Marloie) » : la commune citée EN TÊTE fait foi ; sinon on prend la
   correspondance la plus longue trouvée dans le texte (jamais un fragment de
   3 lettres comme « Han »).
3. Si l'annonce donne des coordonnées, on vérifie qu'elles tombent dans le
   contour (simplifié) de la province, et on en déduit la commune la plus proche.

Nominatim (OpenStreetMap) n'est volontairement pas appelé à chaque exécution :
inutile pour une recherche limitée à une province et son usage est limité.
"""

from __future__ import annotations

import math
import re
import unicodedata
from dataclasses import dataclass

import yaml

from . import config

#: marge (km) autour du contour simplifié : un bien à moins de cette distance du bord
#: est considéré en province (le contour est approximatif)
MARGE_CONTOUR_KM = 1.0
#: longueur minimale d'un nom pour la recherche « dans le texte » (évite « Han », « On »)
LONGUEUR_MIN_FRAGMENT = 4


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
    t = t.lower().replace("-", " ").replace("'", " ").replace("’", " ")
    return " ".join(t.split())


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Distance à vol d'oiseau (haversine)."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _tete(n: str) -> str:
    """« hotton + partie de soy » → « hotton » ; « marche (marloie) » → « marche »."""
    return re.split(r"\s*\+\s*|\s+partie\s+d\S*\s+|\(|,|\s[-–/]\s", n, maxsplit=1)[0].strip()


class Geocodeur:
    def __init__(self, fichier=config.FICHIER_COMMUNES) -> None:
        with open(fichier, encoding="utf-8") as f:
            brut = yaml.safe_load(f)
        self.communes: list[Commune] = [
            Commune(
                nom=c["nom"],
                codes=tuple(str(x) for x in c["codes"]),
                lat=float(c["lat"]),
                lon=float(c["lon"]),
                localites=tuple(c.get("localites", [])),
            )
            for c in brut["communes"]
        ]
        self.hors_province: set[str] = {normaliser(x) for x in brut.get("hors_province", [])}
        self.contour: list[tuple[float, float]] = [(float(a), float(b))
                                                   for a, b in brut.get("contour", [])]
        # Index « nom normalisé » → commune (noms officiels ET localités).
        self._par_nom: dict[str, Commune] = {}
        for c in self.communes:
            self._par_nom[normaliser(c.nom)] = c
        for c in self.communes:
            for loc in c.localites:
                self._par_nom.setdefault(normaliser(loc), c)
        self._par_code: dict[str, Commune] = {}
        for c in self.communes:
            for code in c.codes:
                self._par_code.setdefault(code, c)
        # Fragments cherchés dans le texte, du plus long au plus court.
        self._fragments = sorted((k for k in self._par_nom if len(k) >= LONGUEUR_MIN_FRAGMENT),
                                 key=len, reverse=True)

    def noms(self) -> list[str]:
        return [c.nom for c in self.communes]

    def trouver(self, commune: str | None = None, code_postal: str | None = None,
                latitude: float | None = None, longitude: float | None = None) -> Commune | None:
        """Retrouve la commune officielle à partir d'un nom libre, d'un code postal
        et/ou de coordonnées. None = hors province (ou inconnue)."""
        if commune:
            n = normaliser(commune)
            if n in self.hors_province:
                return None
            if n in self._par_nom:
                return self._par_nom[n]
            tete = _tete(n)
            if tete != n:
                if tete in self.hors_province:
                    return None
                if tete in self._par_nom:
                    return self._par_nom[tete]
                if "+" in n or re.search(r"\bpartie d", n):
                    # « Somme-Leuze + Partie De Grandhan » : la commune en tête est inconnue,
                    # les sections citées ensuite ne doivent pas tromper.
                    return self._par_code_ou_coordonnees(code_postal, latitude, longitude)
            trouvee = self._fragment_le_plus_long(n)
            if trouvee:
                return trouvee
        return self._par_code_ou_coordonnees(code_postal, latitude, longitude)

    def _fragment_le_plus_long(self, n: str) -> Commune | None:
        """Nom de commune ou de localité le plus long présent (mot entier) dans le texte."""
        texte = f" {n} "
        for fragment in self._fragments:
            if f" {fragment} " in texte:
                return self._par_nom[fragment]
        return None

    def _par_code_ou_coordonnees(self, code_postal, latitude, longitude) -> Commune | None:
        if code_postal and str(code_postal) in self._par_code:
            return self._par_code[str(code_postal)]
        if latitude is not None and longitude is not None:
            return self.par_coordonnees(latitude, longitude)
        return None

    def est_dans_la_province(self, commune: str | None, code_postal: str | None) -> bool:
        return self.trouver(commune, code_postal) is not None

    # --- coordonnées ------------------------------------------------------
    def dans_le_contour(self, lat: float, lon: float) -> bool:
        """Point à l'intérieur du contour simplifié, ou à moins de MARGE_CONTOUR_KM du bord."""
        if not self.contour:
            return True
        if _dans_polygone(lat, lon, self.contour):
            return True
        return _distance_au_contour_km(lat, lon, self.contour) <= MARGE_CONTOUR_KM

    def la_plus_proche(self, lat: float, lon: float) -> Commune:
        return min(self.communes, key=lambda c: distance_km(lat, lon, c.lat, c.lon))

    def par_coordonnees(self, lat: float, lon: float) -> Commune | None:
        """Commune la plus proche si le point est dans la province, sinon None."""
        if not (49.4 <= lat <= 50.5 and 4.8 <= lon <= 6.1):
            return None
        return self.la_plus_proche(lat, lon) if self.dans_le_contour(lat, lon) else None


def _dans_polygone(lat: float, lon: float, contour: list[tuple[float, float]]) -> bool:
    """Test pair-impair (ray casting)."""
    dedans = False
    n = len(contour)
    for i in range(n):
        y1, x1 = contour[i]
        y2, x2 = contour[(i + 1) % n]
        if (y1 > lat) != (y2 > lat):
            x = x1 + (lat - y1) * (x2 - x1) / (y2 - y1)
            if lon < x:
                dedans = not dedans
    return dedans


def _distance_au_contour_km(lat: float, lon: float, contour: list[tuple[float, float]]) -> float:
    """Distance du point au segment le plus proche du contour (approximation plane locale)."""
    k_lat = 111.0
    k_lon = 111.0 * math.cos(math.radians(lat))
    px, py = lon * k_lon, lat * k_lat
    meilleure = float("inf")
    n = len(contour)
    for i in range(n):
        ay, ax = contour[i]
        by, bx = contour[(i + 1) % n]
        ax, ay, bx, by = ax * k_lon, ay * k_lat, bx * k_lon, by * k_lat
        dx, dy = bx - ax, by - ay
        t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy)
                                                        / (dx * dx + dy * dy)))
        d = math.hypot(px - (ax + t * dx), py - (ay + t * dy))
        meilleure = min(meilleure, d)
    return meilleure
