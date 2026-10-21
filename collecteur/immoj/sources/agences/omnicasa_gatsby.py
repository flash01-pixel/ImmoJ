"""Module « omnicasa_gatsby » : sites Omnicasa générés avec Gatsby (ex. Immo Bastogne).

La page /fr/a-louer/ est vide côté serveur, mais Gatsby publie ses données à
côté : `/page-data/fr/a-louer/page-data.json` contient la liste complète des
biens (prix, ville, code postal, coordonnées GPS, chambres, surface, PEB,
photos). Chaque bien y est présent en deux langues (fr/nl) : on garde le
français.

L'URL de la fiche (page statique) n'est pas dans ces données : on la lit dans
le plan du site `/sitemap/sitemap-0.xml` (…/<id>/), chargé une fois par agence.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urljoin

from ...modele import Annonce, deviner_type
from .base import SourceAgences

log = logging.getLogger(__name__)

PAGES = {"location": "fr/a-louer", "achat": "fr/a-vendre"}
GOAL = {"location": 1, "achat": 0}
STATUTS_FINIS = {"vendu", "verkocht", "loué", "loue", "verhuurd", "option", "optie"}


class OmnicasaGatsby(SourceAgences):
    logiciel = "omnicasa_gatsby"

    def __init__(self, pages_max: int | None = None) -> None:
        super().__init__(pages_max)
        self._plans: dict[str, dict[str, str]] = {}

    def _plan_du_site(self, base: str) -> dict[str, str]:
        """{id du bien: URL de la fiche} d'après le sitemap (vide si indisponible)."""
        if base not in self._plans:
            try:
                r = self.client.get(urljoin(base, "/sitemap/sitemap-0.xml"))
                self._plans[base] = urls_fiches(r.text)
            except Exception as e:  # noqa: BLE001 - on se rabat sur l'URL de la liste
                log.warning("[%s] plan du site illisible : %s", self.nom, e)
                self._plans[base] = {}
        return self._plans[base]

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        base = agence["url"]
        options = agence.get("options") or {}
        page = (options.get("pages") or {}).get(transaction) or PAGES[transaction]
        r = self.client.get(urljoin(base, f"/page-data/{page.strip('/')}/page-data.json"))
        return analyser_page_data(r.json(), base, agence, transaction, self._plan_du_site(base))


def urls_fiches(xml: str, langue: str = "fr") -> dict[str, str]:
    """Lit les <loc> du sitemap et garde, par identifiant final, l'URL française."""
    plan: dict[str, str] = {}
    for loc in re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml):
        m = re.search(r"/(fr|nl|en|de)/[^/]+/[^/]+/[^/]+/(\d+)/?$", loc)
        if not m:
            continue
        if m.group(2) not in plan or m.group(1) == langue:
            plan[m.group(2)] = loc
    return plan


def liste_biens(donnees: dict) -> list[dict]:
    """Retrouve `propertiesList` dans la structure page-data de Gatsby/Omnicasa."""
    try:
        lignes = donnees["result"]["pageContext"]["data"]["contentRow"]
    except (KeyError, TypeError):
        return []
    for ligne in lignes:
        d = ligne.get("data") if isinstance(ligne, dict) else None
        if isinstance(d, dict) and isinstance(d.get("propertiesList"), list):
            return d["propertiesList"]
    return []


def analyser_page_data(donnees: dict, base: str, agence: dict, transaction: str,
                       plan: dict[str, str] | None = None) -> list[Annonce]:
    plan = plan or {}
    hote = re.sub(r"^https?://", "", base).split("/")[0]
    par_id: dict[str, Annonce] = {}
    for bien in liste_biens(donnees):
        if bien.get("Goal") is not None and bien["Goal"] != GOAL[transaction]:
            continue
        if str(bien.get("SubStatusName") or "").strip().lower() in STATUTS_FINIS:
            continue
        identifiant = str(bien.get("ID") or "")
        if not identifiant:
            continue
        langue = str(bien.get("language") or "fr").lower()
        if identifiant in par_id and langue != "fr":
            continue   # déjà pris (version française prioritaire)
        ville = re.sub(r"\s+", " ", str(bien.get("City") or "")).strip().title()
        type_txt = str(bien.get("TypeDescription") or bien.get("MainTypeName") or "")
        titre = type_txt if len(type_txt) > 25 else f"{type_txt} à {ville}".strip()
        chemin_liste = PAGES[transaction]
        url = plan.get(identifiant) or urljoin(
            base, f"/{chemin_liste}/{slug(ville)}/")
        photos = [p["Url"] for p in bien.get("LargePictureItems") or [] if p.get("Url")]
        if not photos and bien.get("SmallPicture"):
            photos = [bien["SmallPicture"]]
        description = str(bien.get("DescriptionA") or "")
        par_id[identifiant] = Annonce(
            source=f"agences:{OmnicasaGatsby.logiciel}",
            id_externe=f"{hote}:{identifiant}",
            url=url,
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(f"{bien.get('MainTypeName') or ''} {type_txt}"),
            prix=_entier(bien.get("Price")) if bien.get("PublishPrice", True) else None,
            commune=ville or None,
            code_postal=str(bien["Zip"]) if bien.get("Zip") else None,
            latitude=_flottant(bien.get("GoogleX")),
            longitude=_flottant(bien.get("GoogleY")),
            chambres=_entier(bien.get("NumberOfBedRooms")),
            surface=_entier(bien.get("SurfaceTotal")),
            peb=(str(bien.get("EPCLabelText") or "").strip().upper() or None),
            jardin=bool(bien["HasGarden"]) if bien.get("HasGarden") is not None else None,
            terrasse=bool(bien["HasTerrace"]) if bien.get("HasTerrace") is not None else None,
            photos=photos[:10],
            description=description[:1000],
            agence=agence["nom"],
            telephone=agence.get("telephone"),
        )
    return list(par_id.values())


def slug(texte: str) -> str:
    import unicodedata
    s = unicodedata.normalize("NFKD", texte)
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", s)).strip("-")


def _entier(valeur) -> int | None:
    if valeur in (None, "", 0, "0"):
        return None
    try:
        return int(float(valeur))
    except (TypeError, ValueError):
        return None


def _flottant(valeur) -> float | None:
    try:
        f = float(valeur)
    except (TypeError, ValueError):
        return None
    return f if f else None
