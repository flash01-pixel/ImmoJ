"""Envoi des notifications push via ntfy.sh.

On publie en JSON (et non via des en-têtes HTTP) pour que les accents passent
sans encodage spécial. Le topic doit être long et imprévisible : quiconque le
connaît peut lire les notifications.
"""

from __future__ import annotations

import logging

import requests

from .config import Secrets
from .modele import Annonce

log = logging.getLogger(__name__)


def _prix(valeur: int | None, transaction: str) -> str:
    if valeur is None:
        return "prix non communiqué"
    texte = f"{valeur:,}".replace(",", " ") + " €"
    return texte + "/mois" if transaction == "location" else texte


class Notificateur:
    def __init__(self, secrets: Secrets, simulation: bool = False) -> None:
        self.secrets = secrets
        self.simulation = simulation
        self.nb_envoyees = 0

    def _publier(self, titre: str, message: str, *, clic: str | None = None,
                 tags: list[str] | None = None, priorite: int = 3,
                 image: str | None = None) -> bool:
        if not self.secrets.ntfy_actif:
            log.info("ntfy désactivé (NTFY_TOPIC vide) : %s", titre)
            return False
        corps: dict = {"topic": self.secrets.ntfy_topic, "title": titre,
                       "message": message, "priority": priorite}
        if clic:
            corps["click"] = clic
        if tags:
            corps["tags"] = tags
        if image:
            corps["attach"] = image
        if self.simulation:
            log.info("[simulation] notification : %s — %s", titre, message)
            return True
        try:
            r = requests.post(self.secrets.ntfy_serveur, json=corps, timeout=15)
            r.raise_for_status()
        except requests.RequestException as e:
            log.warning("échec d'envoi ntfy : %s", e)
            return False
        self.nb_envoyees += 1
        return True

    def nouvelle_annonce(self, annonce: Annonce, nom_recherche: str | None = None) -> bool:
        lieu = " ".join(x for x in (annonce.code_postal, annonce.commune) if x)
        details = [annonce.type.capitalize(), _prix(annonce.prix, annonce.transaction)]
        if annonce.chambres is not None:
            details.append(f"{annonce.chambres} ch.")
        if annonce.surface:
            details.append(f"{annonce.surface} m²")
        titre = f"Nouveau : {lieu}" if not nom_recherche else f"{nom_recherche} : {lieu}"
        message = " · ".join(details) + f"\n{annonce.titre} ({annonce.source})"
        return self._publier(titre, message, clic=annonce.url, tags=["house"],
                             image=annonce.photos[0] if annonce.photos else None)

    def baisse_de_prix(self, annonce: Annonce, ancien: int, nouveau: int) -> bool:
        lieu = " ".join(x for x in (annonce.code_postal, annonce.commune) if x)
        titre = f"Prix baissé : {lieu}"
        message = (f"{_prix(ancien, annonce.transaction)} → {_prix(nouveau, annonce.transaction)}"
                   f"\n{annonce.titre} ({annonce.source})")
        return self._publier(titre, message, clic=annonce.url, tags=["chart_with_downwards_trend"],
                             priorite=4)

    def source_en_panne(self, source: str, erreur: str, nb_echecs: int) -> bool:
        return self._publier(f"Source en panne : {source}",
                             f"{nb_echecs} échecs consécutifs.\n{erreur[:300]}",
                             tags=["warning"], priorite=2)

    def import_initial(self, source: str, nb: int) -> bool:
        return self._publier(f"ImmoJ : {source} connecté",
                             f"Import initial de {nb} annonces (sans notification individuelle).",
                             tags=["white_check_mark"], priorite=2)
