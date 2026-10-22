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
    #: passage « profond » (toutes les pages) : sert à la désactivation et aux notifications
    profond: bool = False
    #: annonces disponibles sur le site par transaction (« totalItems »…), si le site le dit
    disponibles: dict[str, int] = field(default_factory=dict)

    @property
    def nb_disponibles(self) -> int | None:
        return sum(self.disponibles.values()) if self.disponibles else None

    @property
    def nb_particuliers(self) -> int:
        return sum(a.annonceur == "particulier" for a in self.annonces)


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
    def est_configuree(cls) -> bool:
        """False si la source a besoin de secrets absents (elle est alors ignorée)."""
        return True

    @classmethod
    def doit_tourner(cls, heure: int) -> bool:
        """Décide si la source tourne à cette heure (cadence réduite pour certaines)."""
        return heure % cls.frequence_heures == 0

    #: True quand la source lit toujours tout son catalogue (sites d'agences, e-mails) :
    #: chacun de ses passages vaut un passage profond
    toujours_complet: bool = False

    def __init__(self, pages_max: int | None = None, profond: bool = False) -> None:
        self.client = ClientHttp(self.nom, respecter_robots=self.respecter_robots)
        self.profond = profond
        self.pages_max = pages_max
        #: total d'annonces annoncé par le site, par transaction (rempli par collecter)
        self.disponibles: dict[str, int] = {}
        #: annonces déjà en base pour cette source (id_externe → champs), remplies par
        #: main.py ; permet d'éviter de relire des fiches déjà connues.
        self.connues: dict[str, dict] = {}

    @abstractmethod
    def collecter(self, transaction: str) -> list[Annonce]:
        """Visite le site et renvoie les annonces d'une transaction donnée."""

    # --- lecture des fiches (toutes les photos, détails) ---------------------
    #: fiches lues au maximum par source et par exécution (les autres attendront)
    fiches_max: int = 8
    #: False pour les sources dont la fiche n'apporte rien (ou est interdite)
    lire_fiches: bool = True
    #: budget de temps global consacré aux fiches sur une exécution (secondes
    #: effectivement passées à les lire, toutes sources confondues) ; None = sans limite
    budget_fiches_s: float | None = None
    #: temps déjà consommé (compteur partagé, remis à zéro par main.py)
    temps_fiches_s: float = 0.0

    def enrichir(self, a: Annonce) -> None:
        """Complète une annonce avec sa fiche : toutes les photos et le maximum
        d'informations (salles de bain, garage, terrain...). Par défaut, lecture
        générique de la page ; une source peut la remplacer."""
        from ..fiche import enrichir_depuis_html
        r = self.client.get(a.url)
        enrichir_depuis_html(a, r.text, r.url)

    def _enrichir_nouvelles(self, annonces: list[Annonce]) -> None:
        """Lit la fiche des annonces encore inconnues (au plus `fiches_max`)."""
        if not self.lire_fiches:
            return
        lues = 0
        # Les fiches des locations et des particuliers d'abord (budget limité).
        ordre = sorted(annonces, key=lambda a: (a.transaction != "location",
                                                a.annonceur != "particulier"))
        for a in ordre:
            if lues >= self.fiches_max or (
                    Source.budget_fiches_s is not None
                    and Source.temps_fiches_s >= Source.budget_fiches_s):
                log.info("[%s] %d fiche(s) lue(s), les suivantes attendront le prochain passage",
                         self.nom, lues)
                break
            connue = self.connues.get(a.id_externe)
            if connue is not None:
                # Déjà en base : on garde ce qu'on en sait (photos, détails) sans relire.
                if len(connue.get("photos") or []) > len(a.photos):
                    a.photos = list(connue["photos"])
                for cle, valeur in (connue.get("details") or {}).items():
                    a.details.setdefault(cle, valeur)
                continue
            lues += 1
            debut = time.monotonic()
            try:
                self.enrichir(a)
            except Blocage as e:
                log.warning("[%s] fiches bloquées (%s) : on garde les données de la liste",
                            self.nom, e)
                self.lire_fiches = False
                return
            except Exception as e:  # noqa: BLE001 - une fiche illisible n'est pas grave
                log.warning("[%s] fiche illisible %s : %s", self.nom, a.url, e)
            finally:
                Source.temps_fiches_s += time.monotonic() - debut

    def pages_a_lire(self, pages_defaut: int | None = None) -> int:
        """Nombre de pages à parcourir : --pages, sinon profond/léger."""
        from .. import config
        if self.pages_max:
            return self.pages_max
        if self.profond:
            return config.PAGES_PROFOND
        return min(config.PAGES_MAX, pages_defaut) if pages_defaut else config.PAGES_MAX

    def executer(self, transactions: tuple[str, ...] | None = None) -> ResultatCollecte:
        """Collecte toutes les transactions demandées sans jamais lever d'exception."""
        debut = time.monotonic()
        annonces: list[Annonce] = []
        profond = self.profond or self.toujours_complet
        for transaction in transactions or self.transactions:
            if transaction not in self.transactions:
                continue
            try:
                trouvees = self.collecter(transaction)
                log.info("[%s] %s : %d annonces", self.nom, transaction, len(trouvees))
                self._enrichir_nouvelles(trouvees)
                annonces.extend(trouvees)
            except Blocage as e:
                log.error("[%s] BLOQUÉ : %s", self.nom, e)
                return ResultatCollecte(self.nom, ok=False, annonces=annonces, erreur=str(e),
                                        bloque=True, duree_s=time.monotonic() - debut,
                                        nb_requetes=self.client.nb_requetes, profond=profond,
                                        disponibles=dict(self.disponibles))
            except Exception as e:  # noqa: BLE001 - on veut tout attraper ici
                log.exception("[%s] erreur pendant la collecte %s", self.nom, transaction)
                return ResultatCollecte(self.nom, ok=False, annonces=annonces,
                                        erreur=f"{type(e).__name__}: {e}",
                                        duree_s=time.monotonic() - debut,
                                        nb_requetes=self.client.nb_requetes, profond=profond,
                                        disponibles=dict(self.disponibles))
        return ResultatCollecte(self.nom, ok=True, annonces=annonces,
                                duree_s=time.monotonic() - debut,
                                nb_requetes=self.client.nb_requetes, profond=profond,
                                disponibles=dict(self.disponibles))
