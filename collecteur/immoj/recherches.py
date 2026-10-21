"""Recherches sauvegardées : une annonce correspond-elle à mes critères ?

Les recherches sont créées dans l'appli Android et stockées dans Supabase
(table `recherches_sauvegardees`). Le collecteur les lit à chaque exécution
pour décider quelles annonces méritent une notification.
"""

from __future__ import annotations

import math
from datetime import date

from .modele import PEB, Annonce


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Distance à vol d'oiseau (formule de haversine)."""
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def correspond(annonce: Annonce, recherche: dict, commune_officielle: str | None,
               latitude: float | None, longitude: float | None) -> bool:
    """True si l'annonce satisfait tous les critères de la recherche.

    `recherche` est la ligne brute de Supabase (dict). Un critère absent ou
    NULL n'est pas appliqué.
    """
    if recherche.get("transaction") and recherche["transaction"] != annonce.transaction:
        return False

    # Zone : liste de communes ET/OU rayon autour d'un point. Si les deux sont
    # définis, il suffit que l'un des deux soit satisfait.
    communes = recherche.get("communes") or []
    centre_lat, centre_lon = recherche.get("centre_lat"), recherche.get("centre_lon")
    rayon = recherche.get("rayon_km")
    zone_definie = bool(communes) or (centre_lat is not None and centre_lon is not None and rayon)
    if zone_definie:
        dans_communes = bool(communes) and commune_officielle in communes
        dans_rayon = False
        if centre_lat is not None and centre_lon is not None and rayon \
                and latitude is not None and longitude is not None:
            dans_rayon = distance_km(centre_lat, centre_lon, latitude, longitude) <= float(rayon)
        if not (dans_communes or dans_rayon):
            return False

    if recherche.get("prix_min") is not None and (annonce.prix is None
                                                  or annonce.prix < recherche["prix_min"]):
        return False
    if recherche.get("prix_max") is not None and (annonce.prix is None
                                                  or annonce.prix > recherche["prix_max"]):
        return False

    types = recherche.get("types") or []
    if types and annonce.type not in types:
        return False
    if recherche.get("chambres_min") is not None and (
            annonce.chambres is None or annonce.chambres < recherche["chambres_min"]):
        return False
    if recherche.get("surface_min") is not None and (
            annonce.surface is None or annonce.surface < recherche["surface_min"]):
        return False
    # Pour jardin / animaux / meublé : « True » dans la recherche = exigé.
    # Une annonce qui ne précise pas (None) n'est pas exclue : on préfère
    # une notification de trop qu'une annonce manquée.
    for critere in ("jardin", "animaux", "meuble"):
        if recherche.get(critere) is True and getattr(annonce, critere) is False:
            return False
    peb_max = recherche.get("peb_max")
    if peb_max and annonce.peb and peb_max in PEB and annonce.peb in PEB:
        if PEB.index(annonce.peb) > PEB.index(peb_max):
            return False
    sources = recherche.get("sources") or []
    if sources and annonce.source not in sources:
        return False
    return True


def age_en_jours(annonce: Annonce, aujourd_hui: date | None = None) -> int | None:
    if annonce.date_publication is None:
        return None
    return ((aujourd_hui or date.today()) - annonce.date_publication).days
