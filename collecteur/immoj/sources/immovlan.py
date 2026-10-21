"""Source Immovlan (immovlan.be).

Stratégie : on lit uniquement les pages de résultats (20 annonces par page),
triées par défaut des plus récentes aux plus anciennes après quelques annonces
« mises en avant ». Chaque page contient, pour chaque annonce :
- une carte HTML `<article class="v3-search-card">` (prix, description,
  chambres, surface, terrasse, téléphone de l'agence, photo) ;
- un bloc JSON-LD `RealEstateListing` (date de publication, PEB, agence).
Il est donc inutile d'ouvrir chaque fiche : une requête par page suffit.

robots.txt : les URL de recherche sont autorisées tant qu'elles ne contiennent
pas `noindex` (vérifié par le client HTTP à chaque exécution).
"""

from __future__ import annotations

import json
import re
from datetime import datetime
from urllib.parse import urlencode

from bs4 import BeautifulSoup

from .. import config
from ..modele import Annonce, deviner_type, nombre
from .base import Source

BASE = "https://immovlan.be"

# Paramètres de recherche : province de Luxembourg, types de biens résidentiels.
# ("studio" n'est pas une valeur acceptée : les studios sont des appartements.)
PARAMS = {
    "location": {"transactiontypes": "a-louer", "propertytypes": "maison,appartement,kot",
                 "provinces": "luxembourg"},
    "achat": {"transactiontypes": "a-vendre", "propertytypes": "maison,appartement,terrain",
              "provinces": "luxembourg"},
}


class Immovlan(Source):
    nom = "immovlan"
    libelle = "Immovlan"

    def url_liste(self, transaction: str, page: int) -> str:
        params = dict(PARAMS[transaction])
        if page > 1:
            params["page"] = str(page)
        return f"{BASE}/fr/immobilier?{urlencode(params, safe=',')}"

    def collecter(self, transaction: str) -> list[Annonce]:
        annonces: list[Annonce] = []
        vues: set[str] = set()
        pages_max = self.pages_max or config.PAGES_MAX
        for page in range(1, pages_max + 1):
            reponse = self.client.get(self.url_liste(transaction, page))
            trouvees = analyser_liste(reponse.text, transaction)
            nouvelles = [a for a in trouvees if a.id_externe not in vues]
            if not nouvelles:
                break  # plus de résultats (ou page identique) : on s'arrête
            annonces.extend(nouvelles)
            vues.update(a.id_externe for a in nouvelles)
            if len(trouvees) < 20:
                break  # dernière page
        return annonces


# ---------------------------------------------------------------------------
# Analyse du HTML (fonctions pures, testées sur des pages enregistrées)
# ---------------------------------------------------------------------------

def analyser_liste(html: str, transaction: str) -> list[Annonce]:
    """Transforme une page de résultats Immovlan en liste d'annonces."""
    soup = BeautifulSoup(html, "lxml")
    infos_ld = _lire_json_ld(soup)
    annonces: list[Annonce] = []
    for carte in soup.select("article.v3-search-card"):
        url = carte.get("data-url") or ""
        if not url:
            lien = carte.select_one("a[href*='/detail/']")
            url = lien["href"] if lien else ""
        if "/detail/" not in url:
            continue
        url = url.split("#")[0]
        annonce = _analyser_carte(carte, url, transaction, infos_ld.get(url, {}))
        if annonce:
            annonces.append(annonce)
    return annonces


def _lire_json_ld(soup: BeautifulSoup) -> dict[str, dict]:
    """Indexe les blocs JSON-LD `RealEstateListing` par URL d'annonce."""
    resultat: dict[str, dict] = {}
    for script in soup.find_all("script", type="application/ld+json"):
        if not script.string:
            continue
        try:
            donnees = json.loads(script.string)
        except json.JSONDecodeError:
            continue
        if isinstance(donnees, dict) and donnees.get("@type") == "RealEstateListing":
            url = (donnees.get("url") or "").split("#")[0]
            if url:
                resultat[url] = donnees
    return resultat


def _texte(element, selecteur: str) -> str:
    trouve = element.select_one(selecteur)
    return trouve.get_text(" ", strip=True) if trouve else ""


def _analyser_carte(carte, url: str, transaction: str, ld: dict) -> Annonce | None:
    # Identifiant : dernier segment de l'URL (ex. vbe80830 → VBE80830).
    id_externe = url.rstrip("/").rsplit("/", 1)[-1].upper()
    if not id_externe:
        return None

    # Type de bien : segment de l'URL (/detail/<type>/a-louer/...).
    m = re.search(r"/detail/([^/]+)/", url)
    segment_type = m.group(1).replace("-", " ") if m else ""
    sous_type = _propriete_ld(ld, "propertySubType")
    type_bien = deviner_type(f"{segment_type} {sous_type}")

    titre = _texte(carte, ".v3-search-card-title") or ld.get("name") or segment_type.title()
    prix = nombre(_texte(carte, ".v3-search-card-price"))
    if prix is None and isinstance(ld.get("offers"), dict):
        prix = ld["offers"].get("price")
        prix = int(prix) if isinstance(prix, (int, float)) else None

    code_postal = _texte(carte, "[itemprop=postalCode]") or None
    localite = _texte(carte, "[itemprop=addressLocality]") or None
    adresse = (ld.get("mainEntity") or {}).get("address") or {}
    code_postal = code_postal or adresse.get("postalCode")
    localite = localite or adresse.get("addressLocality")

    description = _texte(carte, ".v3-search-card-description")

    # Pastilles : « 2 Chambre(s) », « 92 m² », « 8 m² Terrasse », « Jardin »...
    chambres = surface = None
    terrasse = jardin = None
    for pastille in carte.select(".v3-search-card-pill"):
        t = pastille.get_text(" ", strip=True)
        bas = t.lower()
        if "chambre" in bas:
            chambres = nombre(t)
        elif "terrasse" in bas:
            terrasse = True
        elif "jardin" in bas:
            jardin = True
        elif bas.endswith("m²") or bas.endswith("m2"):
            surface = surface or nombre(t)
    if chambres is None:
        meta = carte.select_one("meta[itemprop=numberOfBedrooms]")
        chambres = nombre(meta["content"]) if meta and meta.get("content") else None
    if chambres is None:
        valeur = (ld.get("mainEntity") or {}).get("numberOfBedrooms")
        chambres = int(valeur) if isinstance(valeur, int) else None
    if surface is None:
        m_surface = re.search(r"(\d{2,4})\s*m[²2]", description)
        surface = int(m_surface.group(1)) if m_surface else None

    # Photo principale (chargement différé → data-src).
    photos: list[str] = []
    img = carte.select_one(".v3-search-card-image img")
    if img:
        src = img.get("data-src") or img.get("content") or img.get("src") or ""
        if src.startswith("http"):
            photos.append(src)
    if not photos and isinstance(ld.get("image"), list):
        photos = [u for u in ld["image"] if isinstance(u, str) and u.startswith("http")]

    # Agence et téléphone.
    agence = None
    logo = carte.select_one(".v3-search-card-owner img")
    if logo:
        agence = logo.get("title") or logo.get("alt")
    if not agence:
        vendeur = (ld.get("offers") or {}).get("offeredBy") or {}
        agence = vendeur.get("name")
    bouton_tel = carte.select_one("[data-value-phone]")
    telephone = (bouton_tel.get("data-value-phone") or "").strip() or None if bouton_tel else None

    # Date de publication (JSON-LD : "2026-10-17T04:14:54.0000000").
    date_publication = None
    brut = ld.get("datePosted")
    if isinstance(brut, str) and len(brut) >= 10:
        try:
            date_publication = datetime.fromisoformat(brut[:19]).date()
        except ValueError:
            date_publication = None

    peb = _propriete_ld(ld, "energyPerformanceCertificate") or None
    if peb is None:
        filigrane = carte.select_one(".v3-epc-watermark")
        if filigrane:
            m_peb = re.search(r"Single([ABCDEFG])", " ".join(filigrane.get("class", [])))
            peb = m_peb.group(1) if m_peb else None

    description_bas = description.lower()
    return Annonce(
        source=Immovlan.nom,
        id_externe=id_externe,
        url=url,
        titre=titre,
        transaction=transaction,
        type=type_bien,
        prix=prix,
        charges=_charges(description),
        commune=localite,
        code_postal=code_postal,
        chambres=chambres,
        surface=surface,
        peb=peb,
        jardin=jardin if jardin is not None else _mentionne(description_bas, "jardin"),
        terrasse=terrasse if terrasse is not None else _mentionne(description_bas, "terrasse"),
        animaux=_animaux(description_bas),
        meuble=_meuble(description_bas),
        date_publication=date_publication,
        photos=photos,
        description=description,
        agence=agence,
        telephone=telephone,
    )


def _mentionne(texte: str, mot: str) -> bool | None:
    """True si le mot apparaît dans la description, None sinon (on ne sait pas)."""
    return True if mot in texte else None


def _propriete_ld(ld: dict, identifiant: str) -> str:
    """Lit une `additionalProperty` du JSON-LD (propertySubType, PEB...)."""
    for prop in (ld.get("mainEntity") or {}).get("additionalProperty") or []:
        if isinstance(prop, dict) and prop.get("propertyID") == identifiant:
            return str(prop.get("value") or "")
    return ""


def _charges(description: str) -> int | None:
    """Repère « 145 € de provisions », « charges : 100€ », « + 80 € charges »."""
    m = re.search(r"(\d{2,4})\s*(?:€|eur)[^.\d]{0,25}(?:charges|provision)",
                  description, re.I)
    if not m:
        m = re.search(r"(?:charges|provisions?)[^.\d]{0,25}(\d{2,4})\s*(?:€|eur)",
                      description, re.I)
    return int(m.group(1)) if m else None


def _animaux(texte: str) -> bool | None:
    if re.search(r"(pas d'animaux|animaux (non|pas) (admis|accept|autoris)|sans animaux)", texte):
        return False
    if re.search(r"animaux (admis|accept|autoris|bienvenus)", texte):
        return True
    return None


def _meuble(texte: str) -> bool | None:
    if "non meublé" in texte or "non-meublé" in texte:
        return False
    if "meublé" in texte:
        return True
    return None
