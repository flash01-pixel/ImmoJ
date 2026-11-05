"""Lecture générique d'une fiche de bien : toutes les photos et le maximum
d'informations, quel que soit le site.

Les listes de résultats ne donnent qu'une ou deux photos et quelques chiffres.
La fiche du bien en donne bien plus. Ce module lit une page de fiche « à
l'aveugle » :
  * photos : balises <img> (src, data-src, srcset), liens vers des images,
    images Open Graph et JSON-LD ; on écarte logos, icônes, pictos PEB ;
  * détails : couples « Libellé : valeur » (dl/dt/dd, tableaux, listes,
    « Libellé : valeur » dans le texte) dont le libellé fait partie d'une liste
    de caractéristiques connues ; JSON-LD (numberOfRooms, floorSize...).
Une source peut remplacer `Source.enrichir()` par une lecture dédiée (Immoweb).
"""

from __future__ import annotations

import json
import re
from urllib.parse import urljoin, urlsplit

from bs4 import BeautifulSoup

from .modele import Annonce, nombre

#: libellés reconnus (minuscules, sans accents) → libellé affiché
LIBELLES = {
    "salle de bain": "Salles de bain", "salles de bain": "Salles de bain", "sdb": "Salles de bain",
    "salle de douche": "Salles de douche", "salles de douche": "Salles de douche",
    "toilette": "Toilettes", "toilettes": "Toilettes", "wc": "Toilettes",
    "garage": "Garage", "garages": "Garage", "parking": "Parking", "parkings": "Parking",
    "cave": "Cave", "grenier": "Grenier", "ascenseur": "Ascenseur",
    "terrain": "Terrain", "surface du terrain": "Terrain", "superficie du terrain": "Terrain",
    "surface terrain": "Terrain", "terrain total": "Terrain",
    "surface habitable": "Surface habitable", "superficie habitable": "Surface habitable",
    "surface totale": "Surface totale", "superficie": "Surface totale",
    "annee de construction": "Année de construction", "annee": "Année de construction",
    "construction": "Année de construction",
    "etage": "Étage", "etages": "Étages", "nombre d'etages": "Étages",
    "facades": "Façades", "nombre de facades": "Façades", "facade": "Façades",
    "chauffage": "Chauffage", "type de chauffage": "Chauffage",
    "etat": "État", "etat du bien": "État", "etat general": "État",
    "disponibilite": "Disponibilité", "disponible": "Disponibilité", "libre le": "Disponibilité",
    "revenu cadastral": "Revenu cadastral", "rc": "Revenu cadastral",
    "charges": "Charges", "charges mensuelles": "Charges",
    "peb": "PEB", "consommation": "Consommation (kWh/m²/an)",
    "consommation energetique": "Consommation (kWh/m²/an)", "e spec": "Consommation (kWh/m²/an)",
    "cuisine": "Cuisine", "type de cuisine": "Cuisine",
    "chassis": "Châssis", "vitrage": "Vitrage", "cadre de fenetre": "Châssis",
    "orientation": "Orientation", "jardin": "Jardin", "terrasse": "Terrasse",
    "surface terrasse": "Terrasse", "surface jardin": "Jardin", "piscine": "Piscine",
    "meuble": "Meublé", "animaux": "Animaux", "animaux acceptes": "Animaux",
    "reference": "Référence", "ref": "Référence", "ref.": "Référence",
    "bail": "Bail", "garantie": "Garantie locative", "garantie locative": "Garantie locative",
    "precompte immobilier": "Précompte immobilier", "largeur facade": "Largeur de façade",
    "citerne": "Citerne", "panneaux solaires": "Panneaux solaires", "electricite": "Électricité",
    "conformite electrique": "Électricité", "type": "Type", "sous-type": "Sous-type",
    "quartier": "Quartier", "adresse": "Adresse", "rue": "Adresse",
}
JSON_LD = {
    "numberOfRooms": "Pièces", "numberOfBathroomsTotal": "Salles de bain",
    "numberOfBedrooms": "Chambres", "floorSize": "Surface habitable",
    "yearBuilt": "Année de construction", "floorLevel": "Étage",
}
MOTIF_IMAGE = re.compile(r"\.(jpe?g|png|webp)(\?|$)", re.I)
MOTIF_EXCLU = re.compile(r"logo|icon|picto|peb|epc|avatar|flag|drapeau|sprite|placeholder|loader|"
                         r"banner|banniere|agent|team|equipe|/themes?/|/plugins?/|badge|button|"
                         r"facebook|instagram|google|map|carte|svg|blank|pixel|tracking", re.I)
CDN_PHOTOS = ("storagewhise", "omnicasapictures", "immovlan", "immoweb", "2dehands", "2ememain",
              "alveen", "eraforce", "/pictures/", "/photos/", "/uploads/", "/media/", "/images/",
              "/biens/", "/estate", "/immo")


def enrichir_depuis_html(a: Annonce, html: str, url_page: str | None = None) -> None:
    """Complète `a` (en place) avec les photos et détails trouvés dans la fiche."""
    base = url_page or a.url
    soup = BeautifulSoup(html, "lxml")
    a.photos = fusionner(a.photos, photos_de_la_page(soup, base, a))
    for cle, valeur in details_de_la_page(soup).items():
        a.details.setdefault(cle, valeur)
    # C8 : localité absente de la liste → « 6850 Paliseul » lu sur la fiche.
    if not a.commune:
        texte = soup.get_text(" ", strip=True)[:20000]
        m = re.search(r"\b(6[6-9]\d{2})\s+([A-ZÀ-Ý][A-Za-zÀ-ÿ' -]{2,30}?)(?=[,.()<\d]|\s{2}|$| - )",
                      texte)
        if m:
            a.code_postal = a.code_postal or m.group(1)
            a.commune = m.group(2).strip()
    if not a.description or len(a.description) < 200:
        meta = soup.select_one("meta[property='og:description'], meta[name='description']")
        texte = (meta.get("content") or "").strip() if meta else ""
        if len(texte) > len(a.description or ""):
            a.description = texte[:2000]


def fusionner(existantes: list[str], nouvelles: list[str]) -> list[str]:
    vues: set[str] = set()
    resultat: list[str] = []
    for p in list(existantes) + list(nouvelles):
        cle = _cle_photo(p)
        if cle not in vues:
            vues.add(cle)
            resultat.append(p)
    return resultat


def _cle_photo(url: str) -> str:
    """Identifie une photo indépendamment de sa taille (/640/ vs /1920/, ?width=...)."""
    u = url.split("?")[0].lower()
    u = re.sub(r"/\d{2,4}(x\d{2,4})?/", "/", u)          # .../640/... ou .../1024x768/...
    u = re.sub(r"[-_]\d{2,4}x\d{2,4}(?=\.)", "", u)      # photo-300x200.jpg
    u = re.sub(r"/(small|medium|large|xl|xxl|thumb|thumbnail|mini)/", "/", u)
    return u.rsplit("/", 1)[-1] or u


def photos_de_la_page(soup: BeautifulSoup, base: str, a: Annonce | None = None) -> list[str]:
    """Toutes les URL d'images de la page qui ressemblent à des photos du bien."""
    candidats: list[str] = []
    for meta in soup.select("meta[property='og:image'], meta[property='og:image:secure_url']"):
        if meta.get("content"):
            candidats.append(meta["content"])
    for script in soup.select("script[type='application/ld+json']"):
        try:
            donnees = json.loads(script.string or "")
        except (ValueError, TypeError):
            continue
        for bloc in _blocs(donnees):
            image = bloc.get("image")
            if isinstance(image, str):
                candidats.append(image)
            elif isinstance(image, list):
                candidats += [i if isinstance(i, str) else (i or {}).get("url", "")
                              for i in image]
            elif isinstance(image, dict):
                candidats.append(image.get("url", ""))
    for img in soup.find_all("img"):
        for attr in ("data-src", "data-lazy-src", "data-original", "data-large", "src"):
            if img.get(attr):
                candidats.append(img[attr])
        for attr in ("srcset", "data-srcset"):
            if img.get(attr):
                # On garde la plus grande variante du srcset.
                variantes = [v.strip().split(" ")[0] for v in img[attr].split(",") if v.strip()]
                if variantes:
                    candidats.append(variantes[-1])
    for lien in soup.find_all("a", href=True):
        if MOTIF_IMAGE.search(lien["href"]):
            candidats.append(lien["href"])
    for el in soup.select("[style*='background']"):
        m = re.search(r"url\(['\"]?([^'\")]+)", el.get("style", ""))
        if m:
            candidats.append(m.group(1))
    for el in soup.select("[data-bg], [data-background], [data-image], [data-full], "
                          "[data-fancybox]"):
        for attr in ("data-bg", "data-background", "data-image", "data-full", "href"):
            if el.get(attr) and MOTIF_IMAGE.search(el[attr]):
                candidats.append(el[attr])

    hote = urlsplit(base).netloc
    identifiant = re.sub(r"\D", "", (a.id_externe if a else "").rsplit(":", 1)[-1])
    photos: list[str] = []
    for brut in candidats:
        if not brut or brut.startswith("data:"):
            continue
        url = urljoin(base, brut.strip())
        if not url.startswith("http") or MOTIF_EXCLU.search(url.split("?")[0]):
            continue
        if not MOTIF_IMAGE.search(url) and not any(c in url.lower() for c in CDN_PHOTOS):
            continue
        # Une image d'un autre site n'est gardée que si elle vient d'un CDN de photos
        # ou qu'elle porte l'identifiant du bien.
        meme_site = urlsplit(url).netloc.endswith(hote.replace("www.", ""))
        cdn = any(c in url.lower() for c in CDN_PHOTOS)
        if not (meme_site or cdn or (identifiant and len(identifiant) >= 4 and identifiant in url)):
            continue
        photos.append(url)
    return fusionner([], photos)[:60]


def details_de_la_page(soup: BeautifulSoup) -> dict[str, str | int | float | bool]:
    """Couples « libellé : valeur » reconnus sur la fiche."""
    details: dict[str, str | int | float | bool] = {}
    paires: list[tuple[str, str]] = []
    for dl in soup.find_all("dl"):
        dts, dds = dl.find_all("dt"), dl.find_all("dd")
        paires += [(dt.get_text(" ", strip=True), dd.get_text(" ", strip=True))
                   for dt, dd in zip(dts, dds, strict=False)]
    for tr in soup.find_all("tr"):
        cellules = [c.get_text(" ", strip=True) for c in tr.find_all(["th", "td"])]
        if len(cellules) == 2:
            paires.append((cellules[0], cellules[1]))
    for li in soup.find_all("li"):
        enfants = [c for c in li.find_all(recursive=False)
                   if c.name in ("div", "span", "strong", "b", "p")
                   and c.get_text(strip=True)]                   # les icônes vides ne comptent pas
        if len(enfants) == 2:
            paires.append((enfants[0].get_text(" ", strip=True),
                           enfants[1].get_text(" ", strip=True)))
        else:
            texte = li.get_text(" ", strip=True)
            if ":" in texte and len(texte) < 80:
                lib, _, val = texte.partition(":")
                paires.append((lib, val))
    # « Libellé » dans un élément, valeur dans le suivant (mises en page en colonnes)
    for el in soup.select("[class*=label], [class*=libelle], [class*=title-meta], [class*=text]"):
        texte = el.get_text(" ", strip=True)
        if 2 <= len(texte) <= 30 and _normaliser(texte) in LIBELLES:
            suivant = el.find_next_sibling()
            if suivant is not None:
                paires.append((texte, suivant.get_text(" ", strip=True)))
    for script in soup.select("script[type='application/ld+json']"):
        try:
            donnees = json.loads(script.string or "")
        except (ValueError, TypeError):
            continue
        for bloc in _blocs(donnees):
            for cle, libelle in JSON_LD.items():
                v = bloc.get(cle)
                if isinstance(v, dict):
                    v = v.get("value")
                if isinstance(v, int | float | str) and str(v).strip():
                    details.setdefault(libelle, v if isinstance(v, int | float) else str(v)[:60])

    for el in soup.select("[class*=meta], [class*=feature], [class*=caracteristique], "
                          "[class*=spec]"):
        texte = el.get_text(" ", strip=True)
        m = re.fullmatch(r"(\d{1,3})\s+([A-Za-zÀ-ÿ().' ]{2,30})", texte)
        if m and _normaliser(re.sub(r"\(s\)", "s", m.group(2))) in LIBELLES:
            paires.append((re.sub(r"\(s\)", "s", m.group(2)), m.group(1)))
    for lib, val in paires:
        libelle = LIBELLES.get(_normaliser(lib))
        val = re.sub(r"\s+", " ", val).strip(" :")
        if not libelle or not val or len(val) > 60 or libelle in details:
            continue
        details[libelle] = _typer(val)
    return details


def _normaliser(texte: str) -> str:
    import unicodedata
    t = unicodedata.normalize("NFKD", texte.lower())
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = t.replace("(s)", "s").replace("’", "'")       # « Toilette(s) » → « toilettes »
    return re.sub(r"\s+", " ", t).strip(" :*")


def _typer(val: str) -> str | int | bool:
    bas = val.lower()
    if bas in ("oui", "yes", "ja", "✓"):
        return True
    if bas in ("non", "no", "nee", "✗"):
        return False
    if re.fullmatch(r"\d{1,4}", val):
        return int(val)
    return val


def _blocs(donnees) -> list[dict]:
    """Aplatis un JSON-LD (objet, liste ou @graph) en liste de dicts."""
    if isinstance(donnees, list):
        return [b for d in donnees for b in _blocs(d)]
    if isinstance(donnees, dict):
        blocs = [donnees]
        for cle in ("@graph", "mainEntity", "itemOffered"):
            if cle in donnees:
                blocs += _blocs(donnees[cle])
        return blocs
    return []


def entier(valeur) -> int | None:
    """Petit utilitaire partagé : entier depuis un nombre ou un texte."""
    if valeur is None or valeur == "":
        return None
    if isinstance(valeur, bool):
        return None
    if isinstance(valeur, int | float):
        return int(valeur)
    return nombre(str(valeur))
