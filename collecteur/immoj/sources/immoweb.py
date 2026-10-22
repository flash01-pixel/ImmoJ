"""Source Immoweb (immoweb.be) — collecte directe à basse fréquence.

Immoweb expose, pour chaque page de recherche, un point d'entrée JSON
(`/fr/search-results/...`) qui renvoie 30 annonces structurées : type, localité,
code postal, GPS, prix, chambres, surface, PEB, photos, agence. C'est bien plus
fiable que le HTML, et une seule requête par page.

Immoweb a une forte protection anti-bot : on ne lit qu'une page ou deux, toutes
les 3 heures (voir `frequence_heures`). En cas de blocage (403, captcha), le
client HTTP lève `Blocage` et la source se désactive proprement pour cette
exécution ; la lecture des alertes e-mail (immoweb_mail.py) prend le relais.
"""

from __future__ import annotations

import json
import re
from datetime import datetime
from urllib.parse import urlencode

from ..modele import Annonce, deviner_annonceur, deviner_type
from .base import Source

BASE = "https://www.immoweb.be"

# Segments d'URL par transaction : (chemin de recherche, slug pour les liens)
CHEMINS = {
    "location": ("maison-et-appartement/a-louer/luxembourg/province", "a-louer"),
    "achat": ("maison-et-appartement/a-vendre/luxembourg/province", "a-vendre"),
}

# Types Immoweb → nos types
TYPES = {
    "APARTMENT": "appartement", "HOUSE": "maison", "FLAT_STUDIO": "studio",
    "KOT": "kot", "LAND": "terrain", "APARTMENT_GROUP": "appartement",
    "HOUSE_GROUP": "maison",
}
SOUS_TYPES_STUDIO = {"FLAT_STUDIO", "STUDIO"}


class Immoweb(Source):
    nom = "immoweb"
    libelle = "Immoweb"
    frequence_heures = 3   # ne tourne qu'une exécution sur trois
    pages_defaut = 2

    def url_api(self, transaction: str, page: int) -> str:
        chemin, _ = CHEMINS[transaction]
        params = {"countries": "BE", "page": str(page), "orderBy": "newest"}
        return f"{BASE}/fr/search-results/{chemin}?{urlencode(params)}"

    def enrichir(self, a: Annonce) -> None:
        """La fiche Immoweb embarque `window.classified = {...}` : toutes les photos
        et des dizaines de champs. Sinon, lecture générique de la page."""
        from ..fiche import enrichir_depuis_html
        r = self.client.get(a.url)
        donnees = extraire_classified(r.text)
        if donnees:
            completer_depuis_classified(a, donnees)
        else:
            enrichir_depuis_html(a, r.text, r.url)

    def collecter(self, transaction: str) -> list[Annonce]:
        annonces: list[Annonce] = []
        vues: set[str] = set()
        pages_max = self.pages_a_lire(self.pages_defaut)
        for page in range(1, pages_max + 1):
            reponse = self.client.get(self.url_api(transaction, page),
                                      headers={"Accept": "application/json"})
            donnees = reponse.json()
            trouvees = analyser_json(donnees, transaction)
            total = donnees.get("totalItems") or 0
            if total and page == 1:
                self.disponibles[transaction] = int(total)
            nouvelles = [a for a in trouvees if a.id_externe not in vues]
            if not nouvelles:
                break
            annonces.extend(nouvelles)
            vues.update(a.id_externe for a in nouvelles)
            if page * 30 >= total:
                break
        return annonces


# ---------------------------------------------------------------------------
# Analyse du JSON (fonctions pures, testées sur des réponses enregistrées)
# ---------------------------------------------------------------------------

def analyser_json(donnees: dict, transaction: str) -> list[Annonce]:
    annonces = []
    for r in donnees.get("results") or []:
        a = _analyser_resultat(r, transaction)
        if a:
            annonces.append(a)
    return annonces


def _slug(texte: str) -> str:
    t = texte.lower()
    t = re.sub(r"[àâä]", "a", t)
    t = re.sub(r"[éèêë]", "e", t)
    t = re.sub(r"[îï]", "i", t)
    t = re.sub(r"[ôö]", "o", t)
    t = re.sub(r"[ùûü]", "u", t)
    t = re.sub(r"[^a-z0-9]+", "-", t).strip("-")
    return t


def _analyser_resultat(r: dict, transaction: str) -> Annonce | None:
    identifiant = r.get("id")
    if not identifiant:
        return None
    prop = r.get("property") or {}
    loc = prop.get("location") or {}
    trans = r.get("transaction") or {}
    prix_bloc = r.get("price") or {}

    # Les « clusters » (projets neufs regroupant plusieurs biens) ont un prix
    # min/max au lieu d'un prix : on les ignore, comme les projets Immovlan.
    cluster = r.get("cluster") or {}
    if cluster.get("minPrice") is not None and cluster.get("maxPrice") is not None \
            and cluster.get("minPrice") != cluster.get("maxPrice"):
        return None

    type_iw = (prop.get("type") or "").upper()
    sous_type = (prop.get("subtype") or "").upper()
    if sous_type in SOUS_TYPES_STUDIO:
        type_bien = "studio"
    else:
        type_bien = TYPES.get(type_iw) or deviner_type(prop.get("title"))

    if transaction == "location":
        loyer = (trans.get("rental") or {}).get("monthlyRentalPrice")
        prix = loyer if isinstance(loyer, int | float) else prix_bloc.get("mainValue")
        charges = (trans.get("rental") or {}).get("monthlyRentalCosts")
    else:
        vente = (trans.get("sale") or {}).get("price")
        prix = vente if isinstance(vente, int | float) else prix_bloc.get("mainValue")
        charges = None
    prix = int(prix) if isinstance(prix, int | float) and prix > 0 else None
    charges = int(charges) if isinstance(charges, int | float) and charges > 0 else None

    localite = loc.get("locality") or ""
    code_postal = str(loc.get("postalCode") or "") or None
    _, slug_transaction = CHEMINS[transaction]
    url = (f"{BASE}/fr/annonce/{_slug(type_iw or 'bien')}/{slug_transaction}/"
           f"{_slug(localite or 'belgique')}/{code_postal or '0000'}/{identifiant}")

    photos = []
    for p in (r.get("media") or {}).get("pictures") or []:
        u = p.get("largeUrl") or p.get("mediumUrl") or p.get("smallUrl")
        if u:
            photos.append(u)

    date_publication = None
    pub = r.get("publication") or {}
    brut = pub.get("creationDate") or pub.get("lastModificationDate")
    if isinstance(brut, str) and len(brut) >= 10:
        try:
            date_publication = datetime.fromisoformat(brut[:19]).date()
        except ValueError:
            pass

    titre = prop.get("title") or f"{type_bien.capitalize()} à {localite}".strip()
    # salesPitch est parfois un objet {"text": "...", ...} plutôt qu'une chaîne.
    argumentaire = prop.get("salesPitch")
    if isinstance(argumentaire, dict):
        argumentaire = argumentaire.get("text") or ""
    peb = trans.get("certificate")
    return Annonce(
        source=Immoweb.nom,
        id_externe=str(identifiant),
        url=url,
        titre=titre,
        transaction=transaction,
        type=type_bien,
        prix=prix,
        charges=charges,
        commune=localite or None,
        code_postal=code_postal,
        latitude=loc.get("latitude"),
        longitude=loc.get("longitude"),
        chambres=prop.get("bedroomCount") if isinstance(prop.get("bedroomCount"), int) else None,
        surface=int(prop["netHabitableSurface"]) if isinstance(
            prop.get("netHabitableSurface"), int | float) and prop["netHabitableSurface"] > 0
        else None,
        peb=peb if isinstance(peb, str) else None,
        date_publication=date_publication,
        photos=photos,
        description=str(argumentaire or ""),
        agence=r.get("customerName") or None,
        annonceur=deviner_annonceur(Immoweb.nom, r.get("customerName") or None),
        reference=str(identifiant),
        details=details_immoweb(prop, trans),
    )


#: champs Immoweb (property.*) → libellé affiché
CHAMPS_PROPRIETE = {
    "bathroomCount": "Salles de bain", "showerRoomCount": "Salles de douche",
    "toiletCount": "Toilettes", "landSurface": "Terrain", "roomCount": "Pièces",
    "floor": "Étage", "floorCount": "Étages", "parkingCountIndoor": "Parkings intérieurs",
    "parkingCountOutdoor": "Parkings extérieurs", "hasGarden": "Jardin", "gardenSurface": "Jardin",
    "hasTerrace": "Terrasse", "terraceSurface": "Terrasse", "hasLift": "Ascenseur",
    "hasBasement": "Cave", "hasAttic": "Grenier", "hasSwimmingPool": "Piscine",
    "hasGarage": "Garage", "gardenOrientation": "Orientation du jardin",
    "terraceOrientation": "Orientation de la terrasse", "constructionYear": "Année de construction",
    "buildingCondition": "État", "facadeCount": "Façades", "kitchenType": "Cuisine",
    "heatingType": "Chauffage", "isFurnished": "Meublé", "availableAt": "Disponibilité",
    "cadastralIncome": "Revenu cadastral",
    "primaryEnergyConsumptionPerSqm": "Consommation (kWh/m²/an)",
    # clés imbriquées de la fiche (window.classified)
    "building.condition": "État", "building.constructionYear": "Année de construction",
    "building.facadeCount": "Façades", "building.floorCount": "Étages",
    "kitchen.type": "Cuisine", "energy.heatingType": "Chauffage",
    "energy.primaryEnergyConsumptionPerSqm": "Consommation (kWh/m²/an)",
    "land.surface": "Terrain", "outdoor.garden.surface": "Jardin",
    "outdoor.terrace.surface": "Terrasse",
    "outdoor.terrace.orientation": "Orientation de la terrasse",
    "outdoor.garden.orientation": "Orientation du jardin",
    "wellnessEquipment.hasSwimmingPool": "Piscine",
    "location.floor": "Étage", "specificities.SME.office.floor": "Étage",
    "epcScore": "PEB", "hasDoubleGlazing": "Double vitrage", "hasSolarPanels": "Panneaux solaires",
    "monthlyRentalCosts": "Charges", "rentalDepositMonths": "Garantie locative (mois)",
    "leaseDurationMonths": "Bail (mois)", "isPetsAllowed": "Animaux",
}
_VALEURS = {"GOOD": "bon", "AS_NEW": "comme neuf", "TO_RENOVATE": "à rénover",
            "TO_BE_DONE_UP": "à rafraîchir", "JUST_RENOVATED": "rénové",
            "TO_RESTORE": "à restaurer", "HYPER_EQUIPPED": "hyper équipée",
            "SEMI_EQUIPPED": "semi-équipée", "INSTALLED": "équipée", "NOT_INSTALLED": "non équipée",
            "USA_HYPER_EQUIPPED": "américaine hyper équipée", "USA_INSTALLED": "américaine équipée",
            "GAS": "gaz", "FUELOIL": "mazout", "ELECTRIC": "électrique", "WOOD": "bois",
            "PELLET": "pellets", "SOLAR": "solaire", "CARBON": "charbon"}


def _plat(d: dict, prefixe: str = "") -> dict:
    """Aplatis un dict imbriqué : {"building": {"condition": x}} → {"building.condition": x}."""
    plat = {}
    for k, v in (d or {}).items():
        if isinstance(v, dict):
            plat.update(_plat(v, f"{prefixe}{k}."))
        else:
            plat[f"{prefixe}{k}"] = v
    return plat


def details_immoweb(prop: dict, trans: dict) -> dict:
    """Détails lisibles d'après les blocs `property` et `transaction` d'Immoweb."""
    details: dict = {}
    plat = {**_plat(prop), **_plat(trans)}
    for cle, valeur in plat.items():
        nom = cle.rsplit(".", 1)[-1]
        libelle = CHAMPS_PROPRIETE.get(cle) or CHAMPS_PROPRIETE.get(nom)
        if not libelle or valeur in (None, "", [], {}) or libelle in details:
            continue
        if isinstance(valeur, bool):
            details[libelle] = valeur
        elif isinstance(valeur, int | float):
            if nom.endswith(("Surface", "landSurface")):
                details[libelle] = f"{int(valeur)} m²"
            elif valeur > 0:
                details[libelle] = int(valeur) if float(valeur).is_integer() else valeur
        elif isinstance(valeur, str):
            details[libelle] = _VALEURS.get(valeur, valeur.replace("_", " ").lower())[:60]
    return details


def extraire_classified(html: str) -> dict | None:
    """Objet `window.classified = {...};` de la fiche Immoweb (None s'il est absent)."""
    m = re.search(r"window\.classified\s*=\s*(\{.*?\});\s*(?:\n|</script>)", html, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except ValueError:
        return None


def completer_depuis_classified(a: Annonce, donnees: dict) -> None:
    photos = []
    for p in (donnees.get("media") or {}).get("pictures") or []:
        u = p.get("largeUrl") or p.get("mediumUrl") or p.get("smallUrl")
        if u:
            photos.append(u)
    vues = set(a.photos)
    a.photos = a.photos + [u for u in photos if u not in vues]
    for cle, valeur in details_immoweb(donnees.get("property") or {},
                                       donnees.get("transaction") or {}).items():
        a.details.setdefault(cle, valeur)
    desc = (donnees.get("property") or {}).get("description")
    if isinstance(desc, str) and len(desc) > len(a.description or ""):
        a.description = re.sub(r"<[^>]+>", " ", desc)[:3000]
