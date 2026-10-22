"""Structure de données commune à toutes les sources.

Chaque parser (Immovlan, Zimmo, agence...) transforme une page web en objets
`Annonce`. Le reste du collecteur (dédoublonnage, stockage, notifications) ne
connaît que cette structure : ajouter une source ne touche jamais au reste.
"""

from __future__ import annotations

import hashlib
import re
import unicodedata
from dataclasses import dataclass, field
from datetime import date

# Valeurs normalisées. Les parsers doivent s'y tenir.
TYPES = ("appartement", "maison", "studio", "kot", "chambre", "terrain", "immeuble", "commerce",
         "garage", "autre")
TRANSACTIONS = ("location", "achat")
#: qui publie l'annonce : un particulier, une agence, un notaire, ou on ne sait pas
ANNONCEURS = ("particulier", "agence", "notaire", "inconnu")
PEB = ("A++", "A+", "A", "B", "C", "D", "E", "F", "G")


@dataclass
class Annonce:
    """Une annonce telle que vue sur UNE source (avant dédoublonnage)."""

    source: str                 # ex. "immovlan"
    id_externe: str             # identifiant de l'annonce chez la source
    url: str
    titre: str
    transaction: str            # "location" ou "achat"
    type: str = "autre"         # voir TYPES
    prix: int | None = None     # en euros (loyer mensuel ou prix de vente)
    charges: int | None = None  # charges mensuelles en euros, si connues
    commune: str | None = None  # commune (ou localité) telle qu'affichée
    code_postal: str | None = None
    latitude: float | None = None
    longitude: float | None = None
    chambres: int | None = None
    surface: int | None = None  # surface habitable en m²
    peb: str | None = None
    jardin: bool | None = None
    terrasse: bool | None = None
    animaux: bool | None = None
    meuble: bool | None = None
    date_publication: date | None = None
    photos: list[str] = field(default_factory=list)
    description: str = ""
    agence: str | None = None
    telephone: str | None = None
    #: particulier / agence / notaire / inconnu (voir `deviner_annonceur`)
    annonceur: str = "inconnu"
    #: référence du bien chez l'annonceur (« VBE80830 », « REF 1234 »), utile au dédoublonnage
    reference: str | None = None
    #: informations complémentaires libres (libellé français → valeur), ex.
    #: {"Salles de bain": 1, "Garage": "oui", "Terrain": "631 m²", "Année": 1998}
    details: dict[str, str | int | float | bool] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if self.transaction not in TRANSACTIONS:
            raise ValueError(f"transaction inconnue : {self.transaction}")
        # Garde-fou : description et titre sont toujours des chaînes (jamais None ni dict).
        if not isinstance(self.description, str):
            self.description = "" if self.description is None else str(self.description)
        if not isinstance(self.titre, str):
            self.titre = str(self.titre or "")
        if self.type not in TYPES:
            self.type = "autre"
        if self.annonceur not in ANNONCEURS:
            self.annonceur = "inconnu"
        # Un particulier n'a pas d'« agence » : on ne montre jamais « PRIVATE » ni un prénom.
        if self.annonceur == "particulier":
            self.agence = None
        if self.peb and self.peb.upper() not in PEB:
            self.peb = None
        elif self.peb:
            self.peb = self.peb.upper()

    @property
    def cle(self) -> str:
        """Clé unique (source, id_externe) sous forme de chaîne."""
        return f"{self.source}:{self.id_externe}"

    @property
    def empreinte(self) -> str:
        """Empreinte courte et stable, utile dans les logs."""
        return hashlib.sha1(self.cle.encode()).hexdigest()[:10]


# --- Petits utilitaires de normalisation partagés par les parsers --------

def nombre(texte: str | None) -> int | None:
    """Extrait le premier entier d'un texte (« 1 250 € » → 1250, « 3 ch. » → 3)."""
    if not texte:
        return None
    propre = re.sub(r"[\s  .]", "", texte)
    m = re.search(r"\d+", propre)
    return int(m.group()) if m else None


def _sans_accents(texte: str | None) -> str:
    t = unicodedata.normalize("NFKD", (texte or "").lower())
    return "".join(c for c in t if not unicodedata.combining(c)).replace("-", " ")


# Motifs par type, du plus spécifique au plus général. Le premier qui correspond gagne :
# « appartement avec garage » est un appartement, « maison avec bureau » une maison.
_MOTIFS_TYPES: tuple[tuple[str, str], ...] = (
    ("kot", r"\bkots?\b|chambres? (d.? ?)?etudiant|studenten"),
    ("chambre", r"\bcolocation\b|\bcoloc\b|^\W*chambres?\b(?! (a|à) coucher)|chambre (a|à) louer|"
                r"chambre meubl|chambre chez|chambre dans"),
    ("maison", r"\bvillas?\b|fermette|bungalow|\bchalets?\b|cottage|\bmanoir\b|\bchateau\b|"
               r"\bgites?\b|maison de maitre"),
    ("studio", r"\bstudios?\b"),
    ("appartement", r"appart|apartment|appartment|\bduplex|\btriplex|penthouse|"
                    r"rez( |-)de( |-)chauss|\brez\b|\bloft\b|\bflat\b|\bappt\b|\bapp\.|\bapp\b"),
    ("maison", r"\bmaisons?\b|\bferme (a|à|avec|de|du|renov|equestre|sur)|\bhouse\b|"
               r"\bhabitation\b|\bpavillon\b|\bwoning\b|\bhuis\b|\bhoeve\b|demeure"),
    ("terrain", r"\bterrains?\b|\bparcelle|\bprairie|\bbois\b|\bbouwgrond|\bgrond\b|\bpature"),
    ("immeuble", r"immeuble|maison de rapport|b[aâ]timent de rapport|ensemble immobilier|"
                 r"\bbuilding\b|\bopbrengst"),
    ("commerce", r"\bcommerce|\bcommercial|\bbureau|\bindustriel|\bentrep[oô]t|\bhall\b|\bhalls\b|"
                 r"\batelier|\bhoreca|\bhotel|\brestaurant|\bcafe\b|\bmagasin|\bshop\b|"
                 r"\bkantoor|\bhandels|\bexploitation|\bcamping|\bsalon\b|\bprofession"),
    ("garage", r"\bgarages?\b|\bparkings?\b|\bbox(es)?\b|emplacement|\bcarport|\bhangar"),
    ("appartement", r"\blogement|\bbien\b.{0,20}\betage"),
)
_MOTIFS_COMPILES = tuple((t, re.compile(m)) for t, m in _MOTIFS_TYPES)


def deviner_type(texte: str | None, url: str | None = None) -> str:
    """Devine le type de bien d'après un libellé libre (titre, sous-type, texte de la carte)
    et, en secours, l'URL de la fiche (« /bien/a-louer/bureau/… »)."""
    t = _sans_accents(texte)
    for type_bien, motif in _MOTIFS_COMPILES:
        if motif.search(t):
            return type_bien
    if url:
        u = _sans_accents(url.split("//", 1)[-1].replace("/", " ").replace("_", " "))
        for type_bien, motif in _MOTIFS_COMPILES:
            if motif.search(u):
                return type_bien
    return "autre"


_MOTS_PRO = re.compile(
    r"\bimmo|\bagence|\bagency|\bsrl\b|\bsprl\b|\bbv\b|\bbvba\b|\bsa\b|\bnv\b|\bsas\b|"
    r"\bsarl\b|century|\bera\b|\btrevi|\bhonesty|real estate|\bvastgoed|\bmakelaar|"
    r"\bnotaire|\bnotari|\bgroupe\b|\bgroup\b|\bconseils?\b|\bpartners?\b|\bconsult|"
    r"\binvest|\bgestion\b|\bpromot|\bconstruct|\bbatiment|\bhabitat\b|\bhome\b|\bhouse\b|"
    r"\bimmobili|\bproperty|\bproperties|\bbureau\b|\bcabinet\b|\bsyndic|\bservice|\boffice\b|"
    r"\b(cva|gerca|we invest|era b lux)\b|\bbaijot|\bthomas ?& ?piron|\bdewaele|\bbelux\b", re.I)


def nom_pro(nom: str | None, agences_connues: set[str] | frozenset[str] = frozenset()) -> bool:
    """Vrai si ce nom d'annonceur ressemble à un professionnel (« Immo X », « SRL »,
    nom d'agence connue…) plutôt qu'à un prénom de particulier."""
    if not nom:
        return False
    n = _sans_accents(nom).replace("'", " ").replace("&", " ")
    n = " ".join(n.split())
    if n in agences_connues or any(a and a in n for a in agences_connues):
        return True
    return bool(_MOTS_PRO.search(n))


def deviner_annonceur(source: str, agence: str | None,
                      agences_connues: set[str] | frozenset[str] = frozenset()) -> str:
    """Règles par source : Immoweb « PRIVATE » → particulier ; sites d'agences → agence ;
    notaires → notaire ; portails (Immovlan, 2ememain…) → particulier si aucun indice pro."""
    if source.startswith("agences:"):
        return "agence"
    if source == "notaire" or (agence or "").strip().lower() == "notaire":
        return "notaire"
    if (agence or "").strip().upper() in ("PRIVATE", "PARTICULIER", "PRIVÉ", "PRIVE", "PRIVAAT"):
        return "particulier"
    if source in ("immoweb", "immoweb_mail", "zimmo", "logicimmo"):
        # Les portails pros : sans nom d'agence on ne sait pas, avec un nom c'est une agence.
        return "agence" if agence else "inconnu"
    if source in ("immovlan", "2ememain", "realo", "vivastreet", "manuel"):
        if not agence:
            return "particulier" if source != "immovlan" else "particulier"
        return "agence" if nom_pro(agence, agences_connues) else "particulier"
    return "inconnu"


def normaliser_agence(nom: str | None) -> str:
    """« HONESTY BERTRIX », « Honesty Bertrix - 7 bureaux proches de chez vous »,
    « ERA B-LUX SRL - BASTOGNE » → forme courte comparable (« honesty bertrix », « era b lux »)."""
    if not nom:
        return ""
    n = _sans_accents(nom).replace("'", " ").replace("&", " ").replace(".", " ")
    n = re.split(r"\s[-–|/]\s|\(", n)[0]                     # « - 7 bureaux… », « (Double V) »
    n = re.sub(r"\b(srl|sprl|sa|nv|bv|bvba|sas|sarl|scrl|asbl)\b", " ", n)
    return " ".join(n.split())


def meme_agence(a: str | None, b: str | None) -> bool:
    """Deux noms désignent-ils la même agence (l'un est le début de l'autre) ?"""
    na, nb = normaliser_agence(a), normaliser_agence(b)
    if not na or not nb:
        return False
    court, long_ = (na, nb) if len(na) <= len(nb) else (nb, na)
    return long_ == court or long_.startswith(court + " ")


def deviner_transaction(texte: str | None) -> str | None:
    t = (texte or "").lower()
    if "louer" in t or "location" in t or "loyer" in t:
        return "location"
    if "vendre" in t or "vente" in t or "achat" in t:
        return "achat"
    return None
