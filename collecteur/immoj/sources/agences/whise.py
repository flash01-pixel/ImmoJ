"""Module Whise : sites d'agences construits sur les modèles Whise/Webulous.

Deux variantes rencontrées dans la province :
- `mode: scroll` : la page de liste charge ses annonces via
  `/fr/List/InfiniteScroll?json=...` (fragments HTML, cartes « T7 ») ;
  ex. Groupe Arcade, Picard Immo, Immo Plainchamp.
- `mode: html`   : la page `/fr/7/chercher-bien/a-louer` contient déjà les
  cartes (« T11 ») ; ex. Neufcoeur.

Dans les deux cas, chaque carte est un lien `/fr/bien/<a-louer|a-vendre>/<type>/
<code postal>-<localité>/<id>` : l'URL seule donne déjà transaction, type, lieu
et identifiant. Le reste (prix, surface, chambres, PEB, photo) est lu dans la
carte avec des sélecteurs tolérants aux deux modèles.
"""

from __future__ import annotations

import json
import re
from urllib.parse import quote, urljoin, urlsplit

from bs4 import BeautifulSoup

from ...modele import Annonce, deviner_type, nombre
from .base import SourceAgences

PURPOSE = {"location": 2, "achat": 1}          # identifiants Whise
CHEMIN_HTML = {"location": "/fr/7/chercher-bien/a-louer", "achat": "/fr/2/chercher-bien/a-vendre"}
MOTIF_BIEN = re.compile(r"/fr/bien/(a-louer|a-vendre)/([^/]+)/(\d{4})-([^/]+)/(\d+)")


def url_scroll(base: str, transaction: str, par_page: int = 48) -> str:
    """Construit l'URL InfiniteScroll (format observé sur les sites Whise)."""
    requete = {
        "SliderList": False, "SliderMultiAgencies": False, "IsProject": False,
        "PageMaximum": 0, "FirstPage": True, "CanGetNextPage": False, "CMSListType": 2,
        "SortParameter": 5, "MaxItemsPerPage": par_page, "PageNumber": 0,
        "EstateSearchParams": [
            {"FieldName": "StatusIDList", "FieldValue": [1]},
            {"FieldName": "PurposeIDList", "FieldValue": [PURPOSE[transaction]]},
            {"FieldName": "ShowDetails", "FieldValue": True},
            {"FieldName": "ShowRepresentatives", "FieldValue": True},
            {"FieldName": "CanHaveChildren", "FieldValue": False},
        ],
        "CustomQuery": None, "jsonEstateParams": None, "BaseEstateID": 0,
    }
    return f"{base.rstrip('/')}/fr/List/InfiniteScroll?json=" + quote(
        json.dumps(requete, separators=(",", ":")), safe="")


class Whise(SourceAgences):
    logiciel = "whise"

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        base = agence["url"]
        if options.get("mode", "scroll") == "scroll":
            url = url_scroll(base, transaction)
        else:
            url = urljoin(base, CHEMIN_HTML[transaction])
        reponse = self.client.get(url)
        return analyser_html(reponse.text, base, agence, transaction)


def analyser_html(html: str, base: str, agence: dict, transaction: str) -> list[Annonce]:
    soup = BeautifulSoup(html, "lxml")
    annonces: dict[str, Annonce] = {}
    for lien in soup.find_all("a", href=True):
        m = MOTIF_BIEN.search(lien["href"])
        if not m:
            continue
        purpose, type_url, code_postal, localite_slug, identifiant = m.groups()
        trans_url = "location" if purpose == "a-louer" else "achat"
        if trans_url != transaction or identifiant in annonces:
            continue
        carte = _carte(lien)
        texte = carte.get_text(" ", strip=True)
        titre = _texte(carte, "[class*=title], [class*=titre]") or \
            f"{type_url.replace('-', ' ').capitalize()} à {localite_slug.replace('-', ' ').title()}"
        localite = _texte(carte, "[class*=town], [class*=city], [class*=locality]") \
            or localite_slug.replace("-", " ").title()
        img = carte.find("img", src=True)
        photo = img["src"] if img and not _est_picto(img["src"]) else None
        annonces[identifiant] = Annonce(
            source=f"agences:{Whise.logiciel}",
            id_externe=f"{urlsplit(base).netloc}:{identifiant}",
            url=urljoin(base, lien["href"]).split("#")[0],
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(type_url.replace("-", " ") + " "
                              + _texte(carte, "[class*=type], [class*=category]")),
            prix=_prix(carte, texte),
            commune=localite,
            code_postal=code_postal,
            chambres=_fait(carte, "bed"),
            surface=_surface(carte, texte),
            peb=_peb(carte),
            photos=[photo] if photo else [],
            description=texte[:500],
            agence=agence["nom"],
            telephone=agence.get("telephone"),
        )
    return list(annonces.values())


def _carte(lien):
    """Remonte au conteneur de la carte (li ou article), sinon le lien lui-même."""
    noeud = lien
    for _ in range(4):
        if noeud.parent is None or noeud.parent.name in ("body", "html", "ul", "section"):
            break
        noeud = noeud.parent
        if noeud.name in ("li", "article"):
            break
    return noeud


def _texte(el, selecteur: str) -> str:
    trouve = el.select_one(selecteur)
    return trouve.get_text(" ", strip=True) if trouve else ""


def _est_picto(src: str) -> bool:
    return any(x in src.lower() for x in ("peb", "icon", "picto", "logo", ".svg"))


def _prix(carte, texte: str) -> int | None:
    bloc = _texte(carte, "[class*=price], [class*=prix]")
    m = re.search(r"(\d[\d .  ]{1,12})\s*€", bloc or texte)
    return nombre(m.group(1)) if m else None


def _surface(carte, texte: str) -> int | None:
    m = re.search(r"(\d{2,4})(?:[.,]\d+)?\s*m[²2]", texte)
    return int(m.group(1)) if m else None


def _fait(carte, icone: str) -> int | None:
    """Nombre affiché à côté d'une icône (fa-bed → chambres)."""
    i = carte.select_one(f"i[class*={icone}]")
    if not i:
        return None
    frere = i.find_next_sibling("span") or i.parent
    return nombre(frere.get_text(" ", strip=True)) if frere else None


def _peb(carte) -> str | None:
    img = carte.select_one("img[alt*=PEB], img[alt*=peb], img[src*=peb]")
    if not img:
        return None
    m = re.search(r"peb[-_ :]*([a-g](?:\+\+|\+)?)",
                  img.get("alt", "") + " " + img.get("src", ""), re.I)
    return m.group(1).upper() if m else None
