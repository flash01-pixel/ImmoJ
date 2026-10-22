"""Classe de base des modules « agences »."""

from __future__ import annotations

import logging

from ...modele import Annonce, deviner_type
from ..base import Source

log = logging.getLogger(__name__)


class SourceAgences(Source):
    #: les sites d'agences changent lentement : une exécution sur deux suffit
    #: (les portails, eux, tournent toutes les heures)
    frequence_heures = 2
    toujours_complet = True
    #: identifiant du logiciel, tel qu'écrit dans agences.yaml
    logiciel: str = ""
    #: liste des agences (dicts du YAML) attribuée par charger_sources_agences()
    agences: list[dict] = []

    def collecter(self, transaction: str) -> list[Annonce]:
        annonces: list[Annonce] = []
        for agence in self.agences:
            try:
                trouvees = self.collecter_agence(agence, transaction)
                for a in trouvees:
                    a.annonceur = "agence"
                    if a.type == "autre":
                        a.type = deviner_type(a.titre, a.url)
                log.info("[%s] %s (%s) : %d annonces", self.nom, agence["nom"], transaction,
                         len(trouvees))
                annonces.extend(trouvees)
            except Exception as e:  # noqa: BLE001 - une agence en panne n'arrête pas les autres
                log.warning("[%s] %s : %s", self.nom, agence["nom"], e)
        return annonces

    def collecter_agence(self, agence: dict, transaction: str) -> list[Annonce]:
        raise NotImplementedError
