"""Client HTTP partagé : poli, lent et prudent.

- un seul `requests.Session` par source (cookies, keep-alive) ;
- un délai aléatoire entre deux requêtes ;
- respect de robots.txt (lu une fois par site, mis en cache) ;
- 2 tentatives sur erreur réseau ;
- détection des blocages (403, 429, page captcha) → exception `Blocage` que
  la source attrape pour se désactiver proprement.
"""

from __future__ import annotations

import logging
import random
import time
import urllib.robotparser
from urllib.parse import urlsplit

import requests

from . import config

log = logging.getLogger(__name__)


def corriger_encodage(r: requests.Response) -> None:
    """Sans charset dans l'en-tête, requests décode en latin-1 : « € » devient « â¬ ».
    Si le contenu est de l'UTF-8 valide, on le lit comme tel."""
    if "charset" in r.headers.get("Content-Type", "").lower():
        return
    try:
        r.content.decode("utf-8")
    except UnicodeDecodeError:
        return
    r.encoding = "utf-8"


class Blocage(Exception):
    """Le site refuse de nous servir (anti-bot, interdiction robots.txt...)."""


class ClientHttp:
    def __init__(self, nom_source: str, respecter_robots: bool = True) -> None:
        self.nom_source = nom_source
        self.respecter_robots = respecter_robots
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": config.USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "Accept-Language": "fr-BE,fr;q=0.9,nl;q=0.5,en;q=0.3",
        })
        self._robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
        self._derniere_requete = 0.0
        self.nb_requetes = 0

    # --- robots.txt -------------------------------------------------------
    def _autorise(self, url: str) -> bool:
        if not self.respecter_robots:
            return True
        parts = urlsplit(url)
        racine = f"{parts.scheme}://{parts.netloc}"
        if racine not in self._robots:
            rp = urllib.robotparser.RobotFileParser()
            try:
                r = self.session.get(f"{racine}/robots.txt", timeout=config.TIMEOUT_S)
                if r.status_code == 200:
                    rp.parse(r.text.splitlines())
                    self._robots[racine] = rp
                else:
                    self._robots[racine] = None  # pas de robots.txt : tout est permis
            except requests.RequestException:
                self._robots[racine] = None
        rp = self._robots[racine]
        return rp is None or rp.can_fetch(config.USER_AGENT, url) or rp.can_fetch("*", url)

    # --- requête ----------------------------------------------------------
    def _attendre(self) -> None:
        """Impose un délai aléatoire depuis la requête précédente."""
        ecoule = time.monotonic() - self._derniere_requete
        delai = random.uniform(config.DELAI_MIN_S, config.DELAI_MAX_S)
        if ecoule < delai:
            time.sleep(delai - ecoule)
        self._derniere_requete = time.monotonic()

    def get(self, url: str, **kwargs) -> requests.Response:
        """GET avec délai, tentatives et détection de blocage."""
        return self._requete("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> requests.Response:
        """POST (formulaires « ajax » de certains sites d'agences), mêmes garde-fous."""
        return self._requete("POST", url, **kwargs)

    def _requete(self, methode: str, url: str, **kwargs) -> requests.Response:
        if not self._autorise(url):
            raise Blocage(f"robots.txt interdit {url}")
        derniere_erreur: Exception | None = None
        for tentative in range(1, config.NB_TENTATIVES + 1):
            self._attendre()
            self.nb_requetes += 1
            try:
                r = self.session.request(methode, url, timeout=config.TIMEOUT_S, **kwargs)
            except requests.RequestException as e:
                derniere_erreur = e
                log.warning("[%s] erreur réseau (%d/%d) sur %s : %s",
                            self.nom_source, tentative, config.NB_TENTATIVES, url, e)
                continue
            if r.status_code in (401, 403, 429) or self._ressemble_a_un_captcha(r):
                raise Blocage(f"HTTP {r.status_code} sur {url} (anti-bot probable)")
            if r.status_code >= 500:
                derniere_erreur = RuntimeError(f"HTTP {r.status_code} sur {url}")
                log.warning("[%s] %s", self.nom_source, derniere_erreur)
                continue
            corriger_encodage(r)
            return r
        raise RuntimeError(f"échec après {config.NB_TENTATIVES} tentatives : {derniere_erreur}")

    @staticmethod
    def _ressemble_a_un_captcha(r: requests.Response) -> bool:
        if "text/html" not in r.headers.get("Content-Type", ""):
            return False
        debut = r.text[:4000].lower()
        indices = ("captcha", "cf-challenge", "just a moment", "access denied",
                   "datadome", "perimeterx", "are you a human")
        return any(i in debut for i in indices)
