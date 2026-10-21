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
TYPES = ("appartement", "maison", "studio", "kot", "terrain", "autre")
TRANSACTIONS = ("location", "achat")
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

    def __post_init__(self) -> None:
        if self.transaction not in TRANSACTIONS:
            raise ValueError(f"transaction inconnue : {self.transaction}")
        if self.type not in TYPES:
            self.type = "autre"
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


def deviner_type(texte: str | None) -> str:
    """Devine le type de bien à partir d'un libellé libre (« Rez-de-chaussée », « Villa »...)."""
    t = unicodedata.normalize("NFKD", (texte or "").lower())
    t = "".join(c for c in t if not unicodedata.combining(c)).replace("-", " ")
    if "kot" in t or "chambre d'etudiant" in t or "chambre etudiant" in t:
        return "kot"
    if "studio" in t:
        return "studio"
    if any(m in t for m in ("appartement", "apartment", "appartment", "duplex", "triplex",
                            "penthouse", "rez de chauss", "loft")):
        return "appartement"
    if any(m in t for m in ("maison", "villa", "fermette", "bungalow", "chalet", "ferme",
                            "house", "cottage")):
        return "maison"
    if "terrain" in t:
        return "terrain"
    return "autre"


def deviner_transaction(texte: str | None) -> str | None:
    t = (texte or "").lower()
    if "louer" in t or "location" in t or "loyer" in t:
        return "location"
    if "vendre" in t or "vente" in t or "achat" in t:
        return "achat"
    return None
