"""Registre des sources disponibles.

Pour ajouter une source : créer un module dans ce dossier avec une classe qui
hérite de `Source`, puis l'inscrire ici. L'ordre est l'ordre d'exécution.
"""

from __future__ import annotations

from .base import ResultatCollecte, Source
from .immovlan import Immovlan

REGISTRE: dict[str, type[Source]] = {
    Immovlan.nom: Immovlan,
}

__all__ = ["REGISTRE", "ResultatCollecte", "Source"]
