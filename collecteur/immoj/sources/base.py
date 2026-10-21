"""Classe de base de toutes les sources.

Une source sait produire des `Annonce` pour la location et/ou l'achat. La
méthode `executer()` enveloppe la collecte dans une gestion d'erreurs : quoi
qu'il arrive (site en panne, blocage anti-bot, bug de parser), elle renvoie un
`ResultatCollecte` et ne fait jamais planter les autres sources.
"""

from __future__ import annotations

import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from ..http import Blocage, ClientHttp
from ..modele import Annonce

log = logging.getLogger(__name__)


@dataclass
class ResultatCollecte:
    source: str
    ok: bool
    annonces: list[Annonce] = field(default_factory=list)
    erreur: str = ""
    bloque: bool = False
    duree_s: float = 0.0
    nb_requetes: int = 0


class Source(ABC):
    #: identifiant court, stable, en minuscules (utilisé en base de données)
    nom: str = ""
    #: libellé affiché dans l'appli
    libelle: str = ""
    #: transactions couvertes
    transactions: tuple[str, ...] = ("location", "achat")
    #: certaines sources (API JSON officielles) n'ont pas de robots.txt pertinent
    respecter_robots: bool = True
    #: 1 = à chaque exécution horaire, 3 = une exécution sur trois (sites sensibles)
    frequence_heures: int = 1

    @classmethod
    def doit_tourner(cls, heure: int) -> bool:
        """Décide si la source tourne à cette heure (cadence réduite pour certaines)."""
        return heure % cls.frequence_heures == 0

    def __init__(self, pages_max: int | None = None) -> None:
        self.client = ClientHttp(self.nom, respecter_robots=self.respecter_robots)
        self.pages_max = pages_max

    @abstractmethod
    def collecter(self, transaction: str) -> list[Annonce]:
        """Visite le site et renvoie les annonces d'une transaction donnée."""

    def executer(self, transactions: tuple[str, ...] | None = None) -> ResultatCollecte:
        """Collecte toutes les transactions demandées sans jamais lever d'exception."""
        debut = time.monotonic()
        annonces: list[Annonce] = []
        for transaction in transactions or self.transactions:
            if transaction not in self.transactions:
                continue
            try:
                trouvees = self.collecter(transaction)
                log.info("[%s] %s : %d annonces", self.nom, transaction, len(trouvees))
                annonces.extend(trouvees)
            except Blocage as e:
                log.error("[%s] BLOQUÉ : %s", self.nom, e)
                return ResultatCollecte(self.nom, ok=False, annonces=annonces, erreur=str(e),
                                        bloque=True, duree_s=time.monotonic() - debut,
                                        nb_requetes=self.client.nb_requetes)
            except Exception as e:  # noqa: BLE001 - on veut tout attraper ici
                log.exception("[%s] erreur pendant la collecte %s", self.nom, transaction)
                return ResultatCollecte(self.nom, ok=False, annonces=annonces,
                                        erreur=f"{type(e).__name__}: {e}",
                                        duree_s=time.monotonic() - debut,
                                        nb_requetes=self.client.nb_requetes)
        return ResultatCollecte(self.nom, ok=True, annonces=annonces,
                                duree_s=time.monotonic() - debut,
                                nb_requetes=self.client.nb_requetes)
