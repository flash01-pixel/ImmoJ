"""Module « wordpress » : sites WordPress dont la liste des biens est un type de
contenu exposé par l'API REST (`/wp-json/wp/v2/<type>`), ex. Alison Immo
(`property`), Home Sweet Gaume (`bien`).

L'API donne le titre, le lien, la photo, la date et des « classes » (type de
bien, localité, statut), mais pas le prix ni les chambres : on les lit sur la
fiche du bien. Pour rester léger, une fiche n'est visitée qu'une fois : les
biens déjà connus de la base (`self.connues`, remplie par main.py) sont
reconstruits sans requête, et au plus DETAILS_MAX fiches nouvelles sont lues
par passage (les autres attendront le passage suivant).

Options (agences.yaml) :
  type_contenu: property            # type de contenu REST
  classes: {statut: property_status, type: property_type, lieu: property_location}
  detail:                           # où lire, sur la fiche :
    prix: ".property-price"         #   sélecteur CSS (1er élément avec un montant en €)
    chambres: "label:Ch."           #   « label:Texte » = valeur qui suit ce libellé
    surface: "label:Surface habitable"
    lieu: ".property-address"       #   texte contenant « 6700 Arlon » (défaut : regex sur la page)
  transaction_par_defaut: achat     # si ni statut ni texte ne la donnent
"""

from __future__ import annotations

import html as html_lib
import logging
import re
from datetime import date
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from ...modele import Annonce, deviner_transaction, deviner_type, nombre
from .base import SourceAgences
from .generique import _lieu, _surface

log = logging.getLogger(__name__)

DETAILS_MAX = 12      # fiches nouvelles lues par passage (≈ 1 minute à 2–5 s par requête)
PAR_PAGE = 100


class WordPress(SourceAgences):
    logiciel = "wordpress"
    frequence_heures = 3

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        base = agence["url"]
        type_contenu = options.get("type_contenu", "property")
        url = urljoin(base, f"/wp-json/wp/v2/{type_contenu}?per_page={PAR_PAGE}&_fields="
                            "id,date,link,title,content,class_list,yoast_head_json")
        r = self.client.get(url, headers={"Accept": "application/json"})
        entrees = r.json()
        if not isinstance(entrees, list):
            raise RuntimeError(f"réponse REST inattendue : {str(entrees)[:100]}")
        annonces: list[Annonce] = []
        nb_details = 0
        for entree in entrees:
            brute = depuis_rest(entree, base, agence, options)
            if brute is None:
                continue
            connue = self.connues.get(brute.id_externe)
            if connue and connue.get("prix") is not None:
                a = _depuis_cache(brute, connue)
            elif nb_details < DETAILS_MAX:
                nb_details += 1
                try:
                    page = self.client.get(brute.url).text
                except Exception as e:  # noqa: BLE001 - une fiche en panne n'arrête pas la liste
                    log.warning("[%s] fiche illisible %s : %s", self.nom, brute.url, e)
                    continue
                a = completer_depuis_fiche(brute, page, options)
            else:
                continue   # fiche à lire lors d'un prochain passage
            if a.transaction == transaction:
                annonces.append(a)
        return annonces


def depuis_rest(entree: dict, base: str, agence: dict, options: dict) -> Annonce | None:
    """Annonce partielle (sans prix) d'après une entrée de l'API REST."""
    lien = entree.get("link")
    if not lien or entree.get("status", "publish") != "publish":
        return None
    hote = re.sub(r"^https?://", "", base).split("/")[0]
    titre = html_lib.unescape(str((entree.get("title") or {}).get("rendered") or "")).strip()
    classes = options.get("classes") or {}
    valeurs = {cle: _classe(entree.get("class_list") or [], prefixe)
               for cle, prefixe in classes.items()}
    statut = valeurs.get("statut") or ""
    transaction = deviner_transaction(statut.replace("-", " ")) \
        or deviner_transaction(titre) or options.get("transaction_par_defaut") or "achat"
    contenu = str((entree.get("content") or {}).get("rendered") or "")
    description = html_lib.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", contenu))).strip()
    yoast = entree.get("yoast_head_json") or {}
    photos = [img["url"] for img in yoast.get("og_image") or [] if img.get("url")]
    publication = None
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", str(entree.get("date") or ""))
    if m:
        publication = date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
    lieu = valeurs.get("lieu")
    return Annonce(
        source=f"agences:{WordPress.logiciel}",
        id_externe=f"{hote}:{entree.get('id')}",
        url=lien,
        titre=titre[:150] or agence["nom"],
        transaction=transaction,
        type=deviner_type(f"{valeurs.get('type') or ''} {titre}"),
        commune=lieu.replace("-", " ").title() if lieu else None,
        date_publication=publication,
        photos=photos[:5],
        description=description[:1000],
        agence=agence["nom"],
        telephone=agence.get("telephone"),
    )


def completer_depuis_fiche(a: Annonce, page: str, options: dict) -> Annonce:
    """Lit prix, chambres, surface, lieu et PEB sur la fiche du bien."""
    spec = options.get("detail") or {}
    soup = BeautifulSoup(page, "lxml")
    for el in soup.select("script, style, noscript"):
        el.decompose()
    texte = soup.get_text(" ", strip=True)

    prix = _prix(soup, spec.get("prix"))
    if prix is None:
        m = re.search(r"(?<![\d.])(\d{1,3}(?:[ .  ]\d{3})+|\d{3,7})\s*€", texte)
        prix = nombre(m.group(1)) if m else None

    chambres = _nombre_spec(soup, spec.get("chambres"))
    if chambres is None:
        m = re.search(r"(\d{1,2})\s*(?:chambres?|ch\.|chbre)", texte, re.I)
        chambres = int(m.group(1)) if m else None

    surface = _surface(_valeur_spec(soup, spec.get("surface")) or "")
    if surface is None:
        surface = _surface(texte)

    cp, localite = _lieu(_valeur_spec(soup, spec.get("lieu")) or "")
    if not cp:
        m = re.search(r"\b(6[6-9]\d{2})\s+([A-Za-zÀ-ÿ'’-]+(?:\s[A-Za-zÀ-ÿ'’-]+){0,3})", texte)
        cp, localite = _lieu(m.group(0)) if m else (None, None)

    peb = None
    m = re.search(r"\bPEB\s*:?\s*([A-G](?:\+\+|\+)?)\b", texte)
    if m:
        peb = m.group(1).upper()

    transaction = a.transaction
    if not (options.get("classes") or {}).get("statut"):
        # Sans statut explicite, le montant tranche (un loyer reste sous 5 000 €) ;
        # à défaut de prix, un « à louer / loyer » dans le titre ou la fiche.
        if prix is not None:
            transaction = "location" if prix < 5000 else "achat"
        else:
            transaction = deviner_transaction(a.titre) or deviner_transaction(texte[:3000]) \
                or transaction

    if not a.description:
        meta = soup.select_one("meta[property='og:description'], meta[name='description']")
        if meta and meta.get("content"):
            a.description = meta["content"][:1000]
    a.transaction = transaction
    a.prix, a.chambres, a.surface, a.peb = prix, chambres, surface, peb
    a.code_postal = cp or a.code_postal
    a.commune = localite or a.commune
    return a


def _depuis_cache(a: Annonce, connue: dict) -> Annonce:
    """Reconstruit les champs lus jadis sur la fiche, sans nouvelle requête."""
    a.transaction = connue.get("transaction") or a.transaction
    a.prix = connue.get("prix")
    a.chambres = connue.get("chambres")
    a.surface = connue.get("surface")
    a.peb = connue.get("peb")
    a.code_postal = connue.get("code_postal") or a.code_postal
    a.commune = connue.get("localite") or connue.get("commune") or a.commune
    return a


def _classe(classes: list[str], prefixe: str) -> str | None:
    for c in classes:
        if c.startswith(prefixe + "-"):
            return c[len(prefixe) + 1:]
    return None


def _valeur_spec(soup, spec: str | None) -> str | None:
    """Texte désigné par la spec : « label:Libellé » (valeur suivant le libellé) ou CSS."""
    if not spec:
        return None
    if spec.startswith("label:"):
        libelle = spec[6:].strip().lower().rstrip(":")
        for el in soup.find_all(string=True):
            if el.strip().lower().rstrip(":") == libelle:
                suivant = el.find_next(string=lambda s: s and s.strip())
                return suivant.strip() if suivant else None
        return None
    el = soup.select_one(spec)
    return el.get_text(" ", strip=True) if el else None


def _nombre_spec(soup, spec: str | None) -> int | None:
    return nombre(_valeur_spec(soup, spec))


def _prix(soup, selecteur: str | None) -> int | None:
    if not selecteur:
        return None
    for el in soup.select(selecteur):
        t = el.get_text(" ", strip=True)
        if re.search(r"charge|cadastr", t, re.I):
            continue
        if "€" in t or re.fullmatch(r"[\d .  ]{3,}", t):
            n = nombre(t)
            if n:
                return n
    return None
