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
from .notaire import Notaire

REGISTRE: dict[str, type[Source]] = {
    Immovlan.nom: Immovlan,
    Immoweb.nom: Immoweb,
    DeuxiemeMain.nom: DeuxiemeMain,
    AlertesMail.nom: AlertesMail,
    Notaire.nom: Notaire,
}

# Sources « agences » (une par logiciel ayant des agences actives dans agences.yaml).
try:
    from .agences import charger_sources_agences
    REGISTRE.update(charger_sources_agences())
except Exception as _e:  # noqa: BLE001 - un YAML cassé ne doit pas bloquer les portails
    import logging
    logging.getLogger(__name__).error("agences.yaml illisible : %s", _e)

__all__ = ["REGISTRE", "ResultatCollecte", "Source"]
