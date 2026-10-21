"""Module « whise_webulous » : sites WordPress équipés de l'extension Whise/Webulous.

Exemple : Honesty (réseau Arlon, Virton, Libramont, Bertrix, Marche...).
La page de liste (/biens-a-louer/) est vide côté serveur : les annonces sont
chargées par JavaScript via un POST sur
`/wp-content/plugins/whise-webulous/ajax.php` qui renvoie du JSON (24 biens
par page, tous les champs utiles : prix, ville, code postal, chambres,
surface, PEB, photos, description).

L'URL de la fiche n'est pas dans le JSON : elle est reconstruite comme le fait
le site (fonction `renderURIDetailSEO` de son thème) :
    <base>/bien/<catégorie>/<à-louer|à-vendre>/<ville>/<id>
"""

from __future__ import annotations

import logging
import re
import unicodedata
from datetime import date
from urllib.parse import urljoin

from ...modele import Annonce, deviner_type, nombre
from .base import SourceAgences

log = logging.getLogger(__name__)

PURPOSE = {"location": 2, "achat": 1}      # identifiants Whise
#: purposeStatus à écarter : 3 vendu, 4 loué, 5 option vente, 14 option location
STATUTS_FINIS = {3, 4, 5, 14}
PAR_PAGE = 24
PAGES_MAX = 8                               # 8 × 24 = 192 biens par transaction, large


def slugifier(texte: str) -> str:
    """Reproduit `webulous_slugify` du site : accents retirés, minuscules, tirets."""
    s = unicodedata.normalize("NFKD", str(texte))
    s = "".join(c for c in s if not unicodedata.combining(c)).strip().lower()
    s = s.replace("/", "_")
    s = re.sub(r"[^a-z0-9 -]", "", s)
    s = re.sub(r"\s+", "-", s)
    return re.sub(r"-+", "-", s)


def url_fiche(base: str, bien: dict) -> str:
    categorie = slugifier(str(bien.get("category", {}).get("name", "bien")).replace("/", "_"))
    purpose = slugifier(bien.get("purpose", {}).get("name", ""))
    ville = slugifier(bien.get("city") or "")
    return urljoin(base.rstrip("/") + "/", f"bien/{categorie}/{purpose}/{ville}/{bien['id']}")


class WhiseWebulous(SourceAgences):
    logiciel = "whise_webulous"

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        options = agence.get("options") or {}
        base = agence["url"]
        ajax = urljoin(base, options.get("ajax", "/wp-content/plugins/whise-webulous/ajax.php"))
        defaut = "/biens-a-louer/" if transaction == "location" else "/biens-a-vendre/"
        page_liste = urljoin(base, options.get("liste_" + transaction, defaut))
        annonces: list[Annonce] = []
        vus: set[str] = set()
        pages_max = min(PAGES_MAX, self.pages_max or PAGES_MAX)
        for page in range(1, pages_max + 1):
            donnees = {
                "isajax": "1", "estateaction": "getestates",
                "purpose": str(PURPOSE[transaction]),
                "displayStatusIdList[]": "2", "rooms": "", "minprice": "", "maxprice": "",
                "pagenumber": str(page),
            }
            reponse = self.client.post(ajax, data=donnees, headers={
                "X-Requested-With": "XMLHttpRequest", "Referer": page_liste})
            try:
                biens = reponse.json()
            except ValueError as e:
                raise RuntimeError(f"réponse ajax illisible ({e})") from e
            if not isinstance(biens, list) or not biens:
                break
            for bien in biens:
                a = convertir(bien, base, agence, transaction)
                if a and a.id_externe not in vus:
                    vus.add(a.id_externe)
                    annonces.append(a)
            if len(biens) < PAR_PAGE:
                break
        return annonces


def convertir(bien: dict, base: str, agence: dict, transaction: str) -> Annonce | None:
    """Transforme un bien du JSON Whise en Annonce (None s'il est vendu/loué ou hors sujet)."""
    purpose = (bien.get("purpose") or {}).get("id")
    if purpose != PURPOSE[transaction]:
        return None
    if (bien.get("purposeStatus") or {}).get("id") in STATUTS_FINIS:
        return None
    categorie = str((bien.get("category") or {}).get("name") or "")
    sous_categorie = str((bien.get("subCategory") or {}).get("name") or "")
    ville = re.sub(r"\s+", " ", str(bien.get("city") or "")).strip()
    commune = ville.split(",")[0].strip() or None
    description = (bien.get("sms") or {}).get("content") or bien.get("shortDescription") or ""
    nature = sous_categorie or categorie or "Bien"
    titre = f"{nature} à {ville}" if ville else nature
    photos = [p["urlLarge"] for p in bien.get("pictures") or [] if p.get("urlLarge")]
    peb = _peb(bien.get("energyClass"))
    return Annonce(
        source=f"agences:{WhiseWebulous.logiciel}",
        id_externe=f"{_hote(base)}:{bien['id']}",
        url=url_fiche(base, bien),
        titre=titre[:150].strip(),
        transaction=transaction,
        type=deviner_type(f"{sous_categorie} {categorie}"),
        prix=_entier(bien.get("price")) if bien.get("displayPrice", True) else None,
        commune=commune,
        code_postal=str(bien["zip"]) if bien.get("zip") else None,
        chambres=_entier(bien.get("rooms")),
        surface=_entier(bien.get("area")),
        peb=peb,
        jardin=_bool(bien.get("garden")),
        terrasse=_bool(bien.get("terrace")),
        meuble=_bool(bien.get("furnished")),
        date_publication=_date(bien.get("putOnlineDateTime") or bien.get("createDateTime")),
        photos=photos[:10],
        description=str(description)[:1000],
        agence=agence["nom"],
        telephone=agence.get("telephone"),
    )


def _hote(base: str) -> str:
    return re.sub(r"^https?://", "", base).split("/")[0]


def _entier(valeur) -> int | None:
    """Entier depuis un nombre JSON ou un texte (« 1 250 € »)."""
    if valeur is None or valeur == "":
        return None
    if isinstance(valeur, int | float):
        return int(valeur) if valeur > 0 else None
    return nombre(str(valeur))


def _bool(valeur) -> bool | None:
    if valeur is None:
        return None
    return bool(valeur)


def _date(texte) -> date | None:
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", str(texte or ""))
    return date(int(m.group(1)), int(m.group(2)), int(m.group(3))) if m else None


def _peb(classe) -> str | None:
    """« Ap » → A+, « App » → A++, « B » → B."""
    if not classe:
        return None
    s = str(classe).strip()
    m = re.match(r"^([A-Ga-g])(p{0,2}|\+{0,2})$", s)
    if not m:
        return None
    return m.group(1).upper() + "+" * len(m.group(2))
