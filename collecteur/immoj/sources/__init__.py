"""Registre des sources disponibles.

Pour ajouter une source : créer un module dans ce dossier avec une classe qui
hérite de `Source`, puis l'inscrire ici. L'ordre est l'ordre d'exécution.
"""

from __future__ import annotations

from .alertes_mail import AlertesMail
from .base import ResultatCollecte, Source
from .deuxiememain import DeuxiemeMain
from .immovlan import Immovlan
from .immoweb import Immoweb

REGISTRE: dict[str, type[Source]] = {
    Immovlan.nom: Immovlan,
    Immoweb.nom: Immoweb,
    DeuxiemeMain.nom: DeuxiemeMain,
    AlertesMail.nom: AlertesMail,
}

__all__ = ["REGISTRE", "ResultatCollecte", "Source"]
