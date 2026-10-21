"""Sources « agences » : UN module par logiciel ou par réseau, piloté par agences.yaml.

`charger_sources_agences()` lit agences.yaml, regroupe les agences actives par
`logiciel` et instancie le module correspondant avec la liste de ses agences.
Ajouter une agence = ajouter un bloc dans le YAML ; ajouter un logiciel = un
module dans ce dossier, inscrit dans MODULES.
"""

from __future__ import annotations

import logging

import yaml

from ... import config
from ..base import Source
from .base import SourceAgences

log = logging.getLogger(__name__)


def _modules() -> dict[str, type[SourceAgences]]:
    # Import tardif pour éviter les boucles d'import.
    from .era import Era
    from .generique import Generique
    from .honesty import WhiseWebulous
    from .omnicasa_gatsby import OmnicasaGatsby
    from .whise import Whise
    from .wordpress import WordPress
    return {
        Generique.logiciel: Generique,
        Whise.logiciel: Whise,
        WhiseWebulous.logiciel: WhiseWebulous,
        Era.logiciel: Era,
        OmnicasaGatsby.logiciel: OmnicasaGatsby,
        WordPress.logiciel: WordPress,
    }


def charger_agences() -> list[dict]:
    with open(config.FICHIER_AGENCES, encoding="utf-8") as f:
        return yaml.safe_load(f)["agences"]


def charger_sources_agences() -> dict[str, type[Source]]:
    """Renvoie {nom_source: classe} pour chaque logiciel ayant ≥ 1 agence active."""
    modules = _modules()
    par_logiciel: dict[str, list[dict]] = {}
    for a in charger_agences():
        if not a.get("actif"):
            continue
        logiciel = a.get("logiciel") or "inconnu"
        if logiciel not in modules:
            log.warning("agence « %s » : logiciel %s non géré, ignorée", a["nom"], logiciel)
            continue
        par_logiciel.setdefault(logiciel, []).append(a)

    sources: dict[str, type[Source]] = {}
    for logiciel, agences in par_logiciel.items():
        base = modules[logiciel]
        # Sous-classe dynamique liée à SA liste d'agences (nom de source = "agences:<logiciel>").
        classe = type(f"Agences_{logiciel}", (base,), {
            "nom": f"agences:{logiciel}",
            "libelle": f"Agences ({logiciel})",
            "agences": agences,
        })
        sources[classe.nom] = classe
    return sources
