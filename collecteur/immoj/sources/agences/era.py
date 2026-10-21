"""Module « era » : le site national ERA (era.be, Drupal) et son API JSON:API.

Les agences ERA de la province (ERA B-Lux : Bastogne, Marche, Libramont...)
publient sur era.be. L'index `/fr/jsonapi/index/property_index` renvoie, par
pages de 36, un extrait HTML (« teaser ») par bien, plus ses coordonnées.
On filtre sur les communes de la province grâce à `/api/locations`, qui donne
les identifiants des communes (et de leurs sections) : « Arlon (6700) » → 181.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urlencode, urljoin

from bs4 import BeautifulSoup

from ...modele import Annonce, deviner_type, nombre
from .base import SourceAgences

log = logging.getLogger(__name__)

PAR_PAGE = 36            # imposé par l'API (« multiple de 36 »)
PAGES_MAX = 15           # 15 × 36 = 540 biens par transaction
SALE_OR_RENT = {"location": "rent", "achat": "sale"}
MOTIF_CP_PROVINCE = re.compile(r"\((6[6-9]\d\d)\)")


def identifiants_province(locations: list[dict]) -> tuple[list[str], list[str]]:
    """(communes, sections) de la province, d'après la réponse de /api/locations.

    Une commune apparaît deux fois : « Arlon (6700) » (id numérique) et
    « Arlon + sections de commune (6700) » (id « 181-sub », avec la liste des
    sections). On prend l'id numérique de la première et les sections de la seconde.
    """
    communes: list[str] = []
    sections: list[str] = []
    for loc in locations:
        if loc.get("type") != "municipality" or not MOTIF_CP_PROVINCE.search(loc.get("name", "")):
            continue
        tid = str(loc.get("tid") or loc.get("id") or "").replace("-sub", "")
        if not tid:
            continue
        if tid not in communes:
            communes.append(tid)
        for s in loc.get("sub_municipalities") or []:
            if str(s) not in sections:
                sections.append(str(s))
    return communes, sections


def url_index(base: str, transaction: str, communes: list[str], sections: list[str],
              offset: int = 0) -> str:
    params = {
        "filter[sale_or_rent]": SALE_OR_RENT[transaction],
        "filter[location][municipalities]": " ".join(communes),
        "filter[location][sub_municipalities]": " ".join(sections),
        "sort": "-field_start_date",
        "pager[limit]": PAR_PAGE,
        "pager[offset]": offset,
    }
    return urljoin(base, "/fr/jsonapi/index/property_index") + "?" + urlencode(params)


class Era(SourceAgences):
    logiciel = "era"
    # API officielle du site : pas de robots.txt pertinent pour ce chemin JSON.
    respecter_robots = False

    def __init__(self, pages_max: int | None = None) -> None:
        super().__init__(pages_max)
        self._ids: tuple[list[str], list[str]] | None = None

    def _identifiants(self, base: str) -> tuple[list[str], list[str]]:
        if self._ids is None:
            r = self.client.get(urljoin(base, "/api/locations"),
                                headers={"Accept": "application/json"})
            donnees = r.json()
            self._ids = identifiants_province(donnees.get("data") or [])
            if not self._ids[0]:
                raise RuntimeError("aucune commune de la province dans /api/locations")
        return self._ids

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        base = agence["url"]
        communes, sections = self._identifiants(base)
        annonces: list[Annonce] = []
        vus: set[str] = set()
        pages = min(PAGES_MAX, self.pages_max or PAGES_MAX)
        for page in range(pages):
            url = url_index(base, transaction, communes, sections, page * PAR_PAGE)
            r = self.client.get(url, headers={"Accept": "application/vnd.api+json"})
            donnees = r.json()
            if donnees.get("errors"):
                raise RuntimeError(f"API ERA : {donnees['errors'][0].get('detail')}")
            for a in analyser_index(donnees, base, agence, transaction):
                if a.id_externe not in vus:
                    vus.add(a.id_externe)
                    annonces.append(a)
            if not (donnees.get("links") or {}).get("next") or not donnees.get("data"):
                break
        return annonces


def analyser_index(donnees: dict, base: str, agence: dict, transaction: str) -> list[Annonce]:
    annonces: list[Annonce] = []
    for item in donnees.get("data") or []:
        attrs = item.get("attributes") or {}
        a = analyser_teaser(attrs.get("teaser") or "", base, agence, transaction,
                            attrs.get("coordinates") or {})
        if a:
            annonces.append(a)
    return annonces


def analyser_teaser(html: str, base: str, agence: dict, transaction: str,
                    coordonnees: dict | None = None) -> Annonce | None:
    soup = BeautifulSoup(html, "lxml")
    article = soup.select_one("article[about]")
    if article is None:
        return None
    classes = " ".join(article.get("class", []))
    if "property-sold" in classes or "property-rented" in classes:
        return None
    chemin = article["about"]
    m = re.match(r"/(?:fr|nl|en)/(a-louer|a-vendre|te-huur|te-koop)/([^/]+)/([^/]+)/([^/]+)",
                 chemin)
    if not m:
        return None
    trans_url = "location" if m.group(1) in ("a-louer", "te-huur") else "achat"
    if trans_url != transaction:
        return None
    type_url = m.group(3)
    titre = _texte(article, "h3") or _texte(article, ".property-teaser__title") or chemin
    prix_txt = _texte(article, ".field--price")
    adresse = _texte(article, ".field--address")
    cp, localite = None, None
    mcp = re.search(r"\b(\d{4})\s+(.+)$", adresse)
    if mcp:
        cp, localite = mcp.group(1), mcp.group(2).strip()
    chambres = nombre(_texte(article, ".field--bedrooms"))
    surface = nombre(_texte(article, ".field--habitable-space"))
    peb = _texte(article, ".epc-label__middle").upper() or None
    img = article.select_one("img.image, img[src]")
    photo = urljoin(base, img["src"]) if img and img.get("src") else None
    lat = lon = None
    if coordonnees:
        try:
            lat = float(coordonnees.get("lat"))
            lon = float(coordonnees.get("long") or coordonnees.get("lon"))
        except (TypeError, ValueError):
            lat = lon = None
    return Annonce(
        source=f"agences:{Era.logiciel}",
        id_externe=f"era.be:{chemin.rstrip('/').rsplit('/', 1)[-1][:120]}",
        url=urljoin(base, chemin),
        titre=titre[:150],
        transaction=transaction,
        type=deviner_type(f"{type_url} {titre}"),
        prix=nombre(prix_txt) if "€" in prix_txt else None,
        commune=localite or m.group(2).replace("-", " ").title(),
        code_postal=cp,
        latitude=lat,
        longitude=lon,
        chambres=chambres,
        surface=surface,
        peb=peb if peb in ("A++", "A+", "A", "B", "C", "D", "E", "F", "G") else None,
        photos=[photo] if photo else [],
        description=article.get_text(" ", strip=True)[:500],
        agence=agence["nom"],
        telephone=agence.get("telephone"),
    )


def _texte(el, selecteur: str) -> str:
    trouve = el.select_one(selecteur)
    return trouve.get_text(" ", strip=True) if trouve else ""
