"""Source immo.notaire.be — biens vendus par les notaires (achat uniquement).

La page « biens à vendre dans la province de Luxembourg » est rendue côté
serveur : 25 biens par page, triés par date de publication. Chaque carte
indique le type de vente : « Vente de gré à gré », « Vente online » (enchères
en ligne sur Biddit) ou « Vente publique » (enchères en salle). Biddit lui-même protège son API
de recherche par un pare-feu applicatif qui rejette les serveurs GitHub ;
passer par immo.notaire.be couvre les mêmes ventes publiques.
"""

from __future__ import annotations

import re
from urllib.parse import urlencode, urljoin

from bs4 import BeautifulSoup

from ..modele import Annonce, deviner_type, nombre
from .base import Source

BASE = "https://immo.notaire.be"
CHEMIN = "/fr/biens-a-vendre-dans-la-province/luxembourg"


class Notaire(Source):
    nom = "notaire"
    libelle = "Notaires (immo.notaire.be)"
    transactions = ("achat",)
    frequence_heures = 3

    def url_liste(self, page: int) -> str:
        params = {"province": "WLX", "order_by": "publicationStartDate:DESC"}
        if page > 1:
            params["page"] = str(page)
        return f"{BASE}{CHEMIN}?{urlencode(params)}"

    def collecter(self, transaction: str) -> list[Annonce]:
        annonces: list[Annonce] = []
        vues: set[str] = set()
        for page in range(1, self.pages_a_lire() + 1):
            trouvees = analyser_liste(self.client.get(self.url_liste(page)).text)
            nouvelles = [a for a in trouvees if a.id_externe not in vues]
            if not nouvelles:
                break
            annonces.extend(nouvelles)
            vues.update(a.id_externe for a in nouvelles)
            if len(trouvees) < 25:
                break
        return annonces


def analyser_liste(html: str) -> list[Annonce]:
    soup = BeautifulSoup(html, "lxml")
    annonces: list[Annonce] = []
    for carte in soup.select("li.property__item"):
        lien = carte.select_one("a[href]")
        if not lien:
            continue
        identifiant = carte.get("id") or lien["href"].rstrip("/").rsplit("/", 1)[-1]
        if not identifiant:
            continue
        titre = _texte(carte, "h2") or "Bien notarial"
        adresse = _texte(carte, "h3")
        m = re.search(r"\b(\d{4})\s+([^,]+)$", adresse)
        code_postal, localite = (m.group(1), m.group(2).strip()) if m else (None, None)
        type_vente = _texte(carte, ".property__item--selling-type")
        # Les ventes aux enchères (online = Biddit, publique = en salle) sont signalées
        # dans le titre pour être repérables d'un coup d'œil dans l'appli.
        encheres = any(m in type_vente.lower() for m in ("online", "publique", "enchère"))
        infos = carte.get_text(" ", strip=True)
        chambres = None
        img_lit = carte.select_one("img[src*='icon-bed']")
        if img_lit and img_lit.parent:
            chambres = nombre(img_lit.parent.get_text(" ", strip=True))
        surface = None
        m_surf = re.search(r"(\d{2,4})\s*m[²2]", infos)
        if m_surf:
            surface = int(m_surf.group(1))
        img = carte.select_one("img.property--img")
        annonces.append(Annonce(
            source=Notaire.nom,
            id_externe=str(identifiant),
            url=urljoin(BASE, lien["href"]),
            titre=(f"[{type_vente}] {titre}" if encheres else titre)[:150],
            transaction="achat",
            type=deviner_type(_texte(carte, ".property__item--property-type") + " " + titre,
                              urljoin(BASE, lien["href"])),
            prix=nombre(_texte(carte, ".property__item--price")),
            commune=localite,
            code_postal=code_postal,
            chambres=chambres,
            surface=surface,
            photos=[img["src"]] if img and img.get("src", "").startswith("http") else [],
            description=f"{type_vente}. {adresse}".strip(". "),
            agence="Notaire",
            annonceur="notaire",
        ))
    return annonces


def _texte(el, selecteur: str) -> str:
    trouve = el.select_one(selecteur)
    return trouve.get_text(" ", strip=True) if trouve else ""
