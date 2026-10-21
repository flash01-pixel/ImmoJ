"""Module générique pour les sites « maison » : sélecteurs CSS déclarés dans le YAML.

Exemple dans agences.yaml :

  - nom: Immo Exemple
    url: https://www.immo-exemple.be
    logiciel: generique
    actif: true
    options:
      liste_location: https://www.immo-exemple.be/a-louer     # page des locations
      liste_achat: https://www.immo-exemple.be/a-vendre       # page des ventes
      carte: "article.bien"      # un élément par annonce
      lien: "a"                  # lien vers la fiche ("." = la carte est elle-même le lien)
      titre: "h3"                # optionnels : titre, prix, lieu, type, chambres, surface, photo
      prix: ".prix"
      lieu: ".adresse"           # texte contenant « 6700 Arlon » ou juste la localité
      chambres: ".chambres"      # si l'élément n'a pas de texte, on lit son parent (icônes)
      surface: ".surface"
      photo: "img"
      pages: 1                   # pages à lire (ajoute ?page=N ou /page/N, voir pagination)
      pagination: "?page={n}"    # gabarit ajouté à l'URL de liste pour n ≥ 2
      exclure: "vendu|loué"      # optionnel : regex (insensible à la casse) sur le texte
                                 # de la carte pour ignorer des cartes. Par défaut, seule
                                 # une carte dont le PRIX affiche « vendu/loué » est ignorée.

Un attribut `data-filter` JSON sur la carte (ex. DG-Immo) est lu automatiquement :
postal_code, price, bedrooms, liveable_area, garden, terrace, furnished.
"""

from __future__ import annotations

import json
import re
from urllib.parse import urljoin, urlsplit

from bs4 import BeautifulSoup

from ...modele import Annonce, deviner_type, nombre
from .base import SourceAgences


def _texte(el, selecteur: str | None) -> str:
    if el is None or not selecteur:
        return ""
    trouve = el.select_one(selecteur)
    if not trouve:
        return ""
    t = trouve.get_text(" ", strip=True)
    if not t and trouve.parent is not None:      # icône seule : le nombre est à côté
        t = trouve.parent.get_text(" ", strip=True)
    return t


def _lieu(texte: str) -> tuple[str | None, str | None]:
    """« 6830 Bouillon », « B-6700 ARLON », « Gouvy (Cherain) (6670) » → (code, localité)."""
    if not texte:
        return None, None
    m = re.search(r"\b(6[6-9]\d{2})\b", texte)
    if m:
        # Localité = ce qui suit le code postal (« 6700 Arlon »), sinon ce qui précède
        # (« Gouvy (Cherain) (6670) »), sans parenthèses ni adresse.
        apres = re.sub(r"[()]", " ", texte[m.end():]).strip(" -,")
        avant = re.sub(r"\(\s*$", "", texte[:m.start()]).strip(" -,(")
        reste = apres or avant
        return m.group(1), (re.sub(r"\s+", " ", reste)[:40] or None)
    reste = re.sub(r"^.*?,\s*", "", texte) if "," in texte else texte   # « Rue X 1, Virton »
    reste = re.sub(r"\s+", " ", reste).strip(" -,")
    return None, (reste[:40] or None)


def _prix_selecteur(carte, selecteur: str) -> int | None:
    """Premier élément du sélecteur qui contient un montant en € hors « charges »."""
    for el in carte.select(selecteur):
        t = el.get_text(" ", strip=True)
        if "charge" in t.lower():
            continue
        m = re.search(r"(\d[\d .\u202f\u00a0]{0,12})\s*€", t)
        if m:
            return nombre(m.group(1))
    return None


def _surface(texte: str) -> int | None:
    """« 163.04 m² » → 163 (partie entière, sans confondre avec les milliers)."""
    m = re.search(r"(\d{2,4})(?:[.,]\d+)?\s*m[²2]", texte or "")
    return int(m.group(1)) if m else None


def _peb(carte) -> str | None:
    img = carte.select_one("img[alt*=PEB], img[alt*=peb], img[src*=peb]")
    if not img:
        return None
    m = re.search(r"peb[-_ :]*([a-g](?:\+\+|\+)?)\b",
                  img.get("alt", "") + " " + img.get("src", ""), re.I)
    return m.group(1).upper() if m else None


class Generique(SourceAgences):
    logiciel = "generique"

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        url_liste = options.get(f"liste_{transaction}")
        if not url_liste:
            return []
        annonces: list[Annonce] = []
        for page in range(1, int(options.get("pages", 1)) + 1):
            gabarit = options.get("pagination", "?page={n}")
            url = url_liste if page == 1 else url_liste + gabarit.format(n=page)
            reponse = self.client.get(url)
            trouvees = analyser_liste(reponse.text, url_liste, agence, transaction)
            if not trouvees:
                break
            annonces.extend(trouvees)
        return annonces


def analyser_liste(html: str, url_liste: str, agence: dict, transaction: str) -> list[Annonce]:
    options = agence.get("options") or {}
    soup = BeautifulSoup(html, "lxml")
    annonces: list[Annonce] = []
    vus: set[str] = set()
    exclure = re.compile(options["exclure"], re.I) if options.get("exclure") else None
    vendu = re.compile(r"\bvendu\b|\blou[ée]e?\b|sous option|sous compromis", re.I)
    for carte in soup.select(options.get("carte", "article")):
        if exclure and exclure.search(carte.get_text(" ", strip=True)[:400]):
            continue
        # Bien déjà vendu/loué : le prix est remplacé par une mention.
        if options.get("prix") and any(vendu.search(el.get_text(" ", strip=True))
                                        for el in carte.select(options["prix"])):
            continue
        sel_lien = options.get("lien", "a[href]")
        lien = carte if sel_lien == "." else carte.select_one(sel_lien)
        if not lien or not lien.get("href"):
            continue
        url = urljoin(url_liste, lien["href"]).split("#")[0]
        id_externe = re.sub(r"[^a-z0-9]+", "-", url.lower().split("//", 1)[-1]).strip("-")[-80:]
        if id_externe in vus:
            continue
        vus.add(id_externe)

        # Données structurées éventuelles (attribut data-filter JSON, ex. DG-Immo)
        extra: dict = {}
        brut = carte.get("data-filter")
        if brut:
            try:
                extra = json.loads(brut)
            except json.JSONDecodeError:
                extra = {}

        titre = _texte(carte, options.get("titre")) or lien.get("title") or \
            lien.get_text(" ", strip=True) or agence["nom"]
        code_postal, localite = _lieu(_texte(carte, options.get("lieu")))
        if not code_postal and extra.get("postal_code"):
            code_postal = str(extra["postal_code"])
        img = carte.select_one(options.get("photo", "img"))
        photo = (img.get("data-src") or img.get("src")) if img else None
        if photo and re.search(r"peb|icon|logo|\.svg", photo, re.I):
            photo = None
        texte_carte = carte.get_text(" ", strip=True)
        type_texte = _texte(carte, options.get("type")) if options.get("type") else ""
        prix = _prix_selecteur(carte, options.get("prix")) if options.get("prix") else None
        if prix is None and extra.get("price"):
            prix = int(float(extra["price"]))
        if prix is None:   # pas de sélecteur : premier montant en € de la carte
            m = re.search(r"(\d[\d .\u202f\u00a0]{1,12})\s*€", texte_carte)
            prix = nombre(m.group(1)) if m else None
        chambres = (nombre(_texte(carte, options.get("chambres")))
                    if options.get("chambres") else None)
        if chambres is None and isinstance(extra.get("bedrooms"), int):
            chambres = extra["bedrooms"]
        surface = _surface(_texte(carte, options.get("surface"))) if options.get("surface") \
            else None
        if surface is None:
            surface = _surface(texte_carte)
        aire = extra.get("liveable_area")
        if surface is None and isinstance(aire, int | float) and aire:
            surface = int(aire)

        annonces.append(Annonce(
            source=f"agences:{Generique.logiciel}",
            id_externe=f"{urlsplit(url).netloc}:{id_externe}",
            url=url,
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(f"{type_texte} {titre} {texte_carte[:200]}"),
            prix=prix,
            commune=localite or agence.get("commune"),
            code_postal=code_postal,
            chambres=chambres,
            surface=surface,
            peb=_peb(carte),
            jardin=True if extra.get("garden") else None,
            terrasse=True if extra.get("terrace") else None,
            meuble=True if extra.get("furnished") else None,
            photos=[urljoin(url_liste, photo)] if photo else [],
            description=texte_carte[:500],
            agence=agence["nom"],
            telephone=agence.get("telephone"),
        ))
    return annonces
