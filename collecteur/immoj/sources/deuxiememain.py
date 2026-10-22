"""Source 2ememain (2ememain.be), rubrique Immo.

Le site a une API JSON publique (`/lrp/api/search`) utilisée par sa propre page
de résultats. Le filtre de catégorie de l'API (`l2CategoryId`) est ignoré par le
serveur (vérifié par capture le 22/10/2026 : mêmes 185 résultats avec 2143, 2771,
2142 ou 1055) : on interroge donc toute la rubrique Immo autour de PLUSIEURS
centres (Arlon, Bastogne, Libramont, Marche, Vielsalm ; rayon 30 km) pour couvrir
toute la province sans trop déborder, triée des plus récentes aux plus anciennes,
puis on trie côté collecteur : catégorie (maison/appartement à louer, à vendre,
terrain) et commune dans la province (géocodeur). Une seule série de requêtes
sert aux deux transactions (mise en cache). Passage léger : la première page de
chaque centre ; passage profond : toutes les pages.

Les annonces sont souvent des particuliers : moins d'informations structurées
que sur les portails, d'où plusieurs heuristiques sur le titre/description.
"""

from __future__ import annotations

import re
from datetime import date, timedelta
from urllib.parse import urlencode

from .. import config
from ..geocodage import Geocodeur
from ..modele import Annonce, deviner_annonceur, deviner_type, nombre
from .base import Source

BASE = "https://www.2ememain.be"
L1_IMMO = 1032

# Catégories de niveau 2 : identifiant → (transaction, type par défaut)
CATEGORIES = {
    2143: ("location", "maison"),        # Maisons à louer
    2771: ("location", "appartement"),   # Appartements & Studios à louer
    2142: ("achat", "maison"),           # Maisons à vendre
    1055: ("achat", "terrain"),          # Terrains & Terrains à bâtir
}
# Catégories « recherche » (demandes de particuliers) : à exclure si rencontrées
CATEGORIES_EXCLUES = {2145, 2146, 1030, 1041, 1643, 860, 1081, 2147}

#: centres de recherche (code postal → libellé) et rayon : 5 cercles de 30 km couvrent
#: la province (capture du 22/10 : 40 + 40 + 104 + 65 résultats, contre 185 pour un
#: seul cercle de 60 km qui déborde largement sur Namur, Liège et le Grand-Duché)
CENTRES = {"6700": "Arlon", "6600": "Bastogne", "6800": "Libramont", "6900": "Marche",
           "6690": "Vielsalm"}
RAYON_M = 30000
LIMITE = 30


class DeuxiemeMain(Source):
    nom = "2ememain"
    libelle = "2ememain"
    respecter_robots = False   # API JSON du site lui-même (robots.txt vise le HTML)

    def __init__(self, pages_max: int | None = None, profond: bool = False) -> None:
        super().__init__(pages_max, profond)
        self.geocodeur = Geocodeur()

    def url_api(self, offset: int = 0, postcode: str = "6700") -> str:
        params = {
            "l1CategoryId": L1_IMMO,
            "postcode": postcode, "distanceMeters": RAYON_M,
            "limit": LIMITE, "offset": offset,
            "sortBy": "SORT_INDEX", "sortOrder": "DECREASING",   # plus récentes d'abord
        }
        return f"{BASE}/lrp/api/search?{urlencode(params)}"

    def _lire_pages(self) -> list[Annonce]:
        """Lit les premières pages (toutes catégories) une seule fois par exécution."""
        if getattr(self, "_cache", None) is not None:
            return self._cache
        annonces: list[Annonce] = []
        vus: set[str] = set()
        # Léger : 1 page par centre (les plus récentes) ; profond : toutes les pages.
        pages_max = self.pages_max or (config.PAGES_PROFOND if self.profond else 1)
        for postcode in CENTRES:
            for page in range(pages_max):
                reponse = self.client.get(self.url_api(offset=page * LIMITE, postcode=postcode),
                                          headers={"Accept": "application/json"})
                donnees = reponse.json()
                for a in analyser_json(donnees, geocodeur=self.geocodeur):
                    if a.id_externe not in vus:      # les cercles se recouvrent
                        vus.add(a.id_externe)
                        annonces.append(a)
                total = donnees.get("totalResultCount") or 0
                if (page + 1) * LIMITE >= total or not donnees.get("listings"):
                    break
        # Le total par transaction n'est pas connu (rubrique mixte) : on note ce qu'on a lu.
        for a in annonces:
            self.disponibles[a.transaction] = self.disponibles.get(a.transaction, 0) + 1
        self._cache = annonces
        return annonces

    def collecter(self, transaction: str) -> list[Annonce]:
        return [a for a in self._lire_pages() if a.transaction == transaction]


# ---------------------------------------------------------------------------

_AGENCES_CONNUES: frozenset[str] | None = None


def agences_connues() -> frozenset[str]:
    """Noms (normalisés) des agences d'agences.yaml : un vendeur 2ememain qui porte
    l'un de ces noms est une agence, pas un particulier."""
    global _AGENCES_CONNUES
    if _AGENCES_CONNUES is None:
        try:
            from ..modele import normaliser_agence
            from .agences import charger_agences
            _AGENCES_CONNUES = frozenset(normaliser_agence(a["nom"]) for a in charger_agences())
        except Exception:  # noqa: BLE001 - sans YAML, on se contente des mots-clés
            _AGENCES_CONNUES = frozenset()
    return _AGENCES_CONNUES


def analyser_json(donnees: dict, geocodeur: Geocodeur | None = None) -> list[Annonce]:
    """Transforme une réponse de l'API en annonces (province de Luxembourg seulement).

    Les catégories non résidentielles (garages, étranger, demandes...) sont ignorées.
    """
    geocodeur = geocodeur or Geocodeur()
    annonces = []
    for brut in donnees.get("listings") or []:
        categorie = CATEGORIES.get(brut.get("categoryId"))
        if not categorie:
            continue
        transaction, type_defaut = categorie
        a = _analyser_listing(brut, transaction, type_defaut, geocodeur)
        if a:
            annonces.append(a)
    return annonces


def _attributs(brut: dict) -> dict[str, str]:
    return {a.get("key"): str(a.get("value") or "")
            for a in brut.get("extendedAttributes") or [] if isinstance(a, dict)}


def _date(texte: str | None) -> date | None:
    """« Aujourd'hui », « Hier », « 19 oct. '26 » → date."""
    if not texte:
        return None
    t = texte.strip().lower()
    if t.startswith("aujourd"):
        return date.today()
    if t == "hier":
        return date.today() - timedelta(days=1)
    if t == "avant-hier":
        return date.today() - timedelta(days=2)
    mois = {"janv": 1, "févr": 2, "fevr": 2, "mars": 3, "avr": 4, "mai": 5, "juin": 6,
            "juil": 7, "août": 8, "aout": 8, "sept": 9, "oct": 10, "nov": 11, "déc": 12, "dec": 12}
    m = re.match(r"(\d{1,2})\s+([a-zéû]+)\.?\s+'?(\d{2,4})", t)
    if m and m.group(2)[:4] in mois:
        annee = int(m.group(3))
        annee = annee + 2000 if annee < 100 else annee
        try:
            return date(annee, mois[m.group(2)[:4]], int(m.group(1)))
        except ValueError:
            return None
    return None


def _analyser_listing(brut: dict, transaction: str, type_defaut: str,
                      geocodeur: Geocodeur) -> Annonce | None:
    identifiant = brut.get("itemId")
    if not identifiant or brut.get("categoryId") in CATEGORIES_EXCLUES:
        return None
    titre = (brut.get("title") or "").strip()
    bas = titre.lower()
    # Demandes (« cherche », « recherche », « gezocht ») : ce ne sont pas des offres.
    if re.search(r"\b(cherch|recherch|gezocht|zoek)", bas):
        return None

    loc = brut.get("location") or {}
    attrs = _attributs(brut)
    lat = loc.get("latitude") if isinstance(loc.get("latitude"), float) else None
    lon = loc.get("longitude") if isinstance(loc.get("longitude"), float) else None
    commune = geocodeur.trouver(loc.get("cityName"), None, lat, lon)
    if commune is None:
        return None   # hors province (le rayon de 60 km déborde sur Namur, Liège, le Grand-Duché)

    prix_info = brut.get("priceInfo") or {}
    prix = None
    if prix_info.get("priceType") == "FIXED" and isinstance(prix_info.get("priceCents"), int):
        prix = prix_info["priceCents"] // 100
    # Loyers saisis en centimes parfois absurdes (ex. 12 € → prix sur demande)
    if prix is not None and prix < 50:
        prix = None

    type_bien = type_defaut
    if "studio" in bas or "studio" in attrs.get("typeOfProperty", "").lower():
        type_bien = "studio"
    elif "kot" in bas or "chambre" in bas and type_defaut == "appartement":
        type_bien = "kot" if "kot" in bas else type_bien
    elif deviner_type(attrs.get("typeOfProperty")) != "autre":
        type_bien = deviner_type(attrs.get("typeOfProperty"))

    description = (brut.get("description") or "").strip()
    texte = f"{bas} {description.lower()}"
    photos = []
    for u in brut.get("imageUrls") or []:
        if isinstance(u, str):
            photos.append("https:" + u if u.startswith("//") else u)

    chambres = nombre(attrs.get("numberOfRooms")) if "chambre" in attrs.get("numberOfRooms", "") \
        else None
    surface = nombre(attrs.get("livingArea"))

    vendeur = (brut.get("sellerInformation") or {}).get("sellerName")
    # Particulier sauf indice pro : nom d'agence connue, « immo », « srl »…, ou
    # mots d'agence dans la description (« notre agence », « honoraires »).
    annonceur = deviner_annonceur(DeuxiemeMain.nom, vendeur, agences_connues())
    if annonceur == "particulier" and re.search(
            r"notre agence|nos honoraires|frais d.agence|agence immobili", texte):
        annonceur = "agence"
    return Annonce(
        source=DeuxiemeMain.nom,
        id_externe=str(identifiant),
        url=BASE + (brut.get("vipUrl") or f"/v/immo/{identifiant}"),
        titre=titre or f"{type_bien.capitalize()} à {commune.nom}",
        transaction=transaction,
        type=type_bien,
        prix=prix,
        commune=loc.get("cityName") or commune.nom,
        code_postal=commune.codes[0] if loc.get("cityName") else None,
        latitude=lat,
        longitude=lon,
        chambres=chambres,
        surface=surface,
        jardin=True if "jardin" in texte else None,
        terrasse=True if "terrasse" in texte else None,
        meuble=True if "meublé" in texte or "meuble" in texte else None,
        animaux=False if re.search(r"pas d'animaux|animaux (non|pas)", texte) else None,
        date_publication=_date(brut.get("date")),
        photos=photos,
        description=description,
        agence=vendeur,
        annonceur=annonceur,
        reference=str(identifiant),
    )
