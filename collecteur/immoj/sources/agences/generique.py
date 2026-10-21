"""Module générique pour les sites « maison » : sélecteurs CSS déclarés dans le YAML.

Exemple dans agences.yaml :

  - nom: Immo Exemple
    commune: Arlon
    url: https://www.immo-exemple.be
    logiciel: generique
    actif: true
    options:
      liste_location: https://www.immo-exemple.be/a-louer
      liste_achat: https://www.immo-exemple.be/a-vendre
      carte: "div.bien"              # un élément par annonce
      lien: "a"                      # lien vers la fiche (href)
      titre: "h3"
      prix: ".prix"
      lieu: ".localite"              # texte contenant « 6700 Arlon »
      photo: "img"                   # attribut src ou data-src
      chambres: ".chambres"          # optionnel
      surface: ".surface"            # optionnel
"""

from __future__ import annotations

import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from ...modele import Annonce, deviner_type, nombre
from .base import SourceAgences


def _texte(el, selecteur: str | None) -> str:
    if not el or not selecteur:
        return ""
    trouve = el.select_one(selecteur)
    return trouve.get_text(" ", strip=True) if trouve else ""


def _lieu(texte: str) -> tuple[str | None, str | None]:
    m = re.search(r"\b(6[6-9]\d{2})\s+([A-Za-zÀ-ÿ'’ -]{3,40})", texte)
    if m:
        return m.group(1), m.group(2).strip(" -")
    return None, texte.strip()[:40] or None


class Generique(SourceAgences):
    logiciel = "generique"

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        url_liste = options.get(f"liste_{transaction}")
        if not url_liste:
            return []
        reponse = self.client.get(url_liste)
        return analyser_liste(reponse.text, url_liste, agence, transaction)


def analyser_liste(html: str, url_liste: str, agence: dict, transaction: str) -> list[Annonce]:
    options = agence.get("options") or {}
    soup = BeautifulSoup(html, "lxml")
    annonces: list[Annonce] = []
    for carte in soup.select(options.get("carte", "article")):
        lien = carte.select_one(options.get("lien", "a[href]"))
        if not lien or not lien.get("href"):
            continue
        url = urljoin(url_liste, lien["href"]).split("#")[0]
        id_externe = re.sub(r"[^a-z0-9]+", "-", url.lower().split("//", 1)[-1]).strip("-")[-80:]
        titre = (_texte(carte, options.get("titre")) or lien.get_text(" ", strip=True)
                 or agence["nom"])
        code_postal, localite = _lieu(_texte(carte, options.get("lieu")))
        img = carte.select_one(options.get("photo", "img"))
        photo = (img.get("data-src") or img.get("src")) if img else None
        texte_carte = carte.get_text(" ", strip=True)
        annonces.append(Annonce(
            source=f"agences:{Generique.logiciel}",
            id_externe=f"{agence['nom']}:{id_externe}",
            url=url,
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(f"{titre} {texte_carte[:200]}"),
            prix=nombre(_texte(carte, options.get("prix"))),
            commune=localite or agence.get("commune"),
            code_postal=code_postal,
            chambres=nombre(_texte(carte, options.get("chambres"))),
            surface=nombre(_texte(carte, options.get("surface"))),
            photos=[urljoin(url_liste, photo)] if photo else [],
            description=texte_carte[:500],
            agence=agence["nom"],
            telephone=agence.get("telephone"),
        ))
    return annonces
