"""Configuration du collecteur.

Tout ce qui est secret (clés Supabase, topic ntfy, mot de passe Gmail) vient
des variables d'environnement, elles-mêmes alimentées par les « Secrets »
GitHub (voir docs/03-secrets-github.md). Les réglages non secrets (délais,
seuils du dédoublonnage) sont des constantes ici, faciles à ajuster.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

DOSSIER_COLLECTEUR = Path(__file__).resolve().parents[1]
FICHIER_COMMUNES = DOSSIER_COLLECTEUR / "communes.yaml"
FICHIER_AGENCES = DOSSIER_COLLECTEUR / "agences.yaml"

# --- Collecte -------------------------------------------------------------
# Heures (Bruxelles) entre lesquelles le collecteur travaille. En dehors, il
# s'arrête immédiatement (le cron GitHub est en UTC, d'où ce garde-fou).
HEURE_DEBUT = 7
HEURE_FIN = 23  # inclus : la collecte de 23h a lieu
FUSEAU = "Europe/Brussels"

# Délai (secondes) entre deux requêtes vers un même site : poli et discret.
DELAI_MIN_S = 2.0
DELAI_MAX_S = 5.0
TIMEOUT_S = 30
NB_TENTATIVES = 2

# Nombre maximum de pages de résultats parcourues par source et par transaction.
# Les listes sont triées « plus récent d'abord » ; 3 pages suffisent toutes les heures.
PAGES_MAX = 3

# Une annonce plus vue sur aucune source depuis ce nombre de jours est désactivée.
JOURS_AVANT_DESACTIVATION = 7

# Une annonce est « nouvelle » (badge) pendant ce nombre d'heures.
HEURES_NOUVEAUTE = 48

# Nombre d'échecs consécutifs d'une source avant d'envoyer une alerte ntfy.
ECHECS_AVANT_ALERTE = 3

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

# --- Dédoublonnage --------------------------------------------------------
TOLERANCE_PRIX = 0.03        # ± 3 %
TOLERANCE_SURFACE = 0.10     # ± 10 %
SEUIL_SIMILARITE_TEXTE = 70  # sur 100 (rapidfuzz)


@dataclass(frozen=True)
class Secrets:
    """Valeurs lues dans l'environnement (jamais écrites dans le code)."""

    supabase_url: str
    supabase_cle_service: str
    ntfy_topic: str
    ntfy_serveur: str = "https://ntfy.sh"
    gmail_utilisateur: str = ""
    gmail_mot_de_passe: str = ""

    @property
    def ntfy_actif(self) -> bool:
        return bool(self.ntfy_topic)


def supabase_configure() -> bool:
    return bool(os.environ.get("SUPABASE_URL") and os.environ.get("SUPABASE_SERVICE_KEY"))


def charger_secrets(obligatoire: bool = True) -> Secrets:
    """Lit les variables d'environnement ; vérifie Supabase si `obligatoire`."""
    url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    cle = os.environ.get("SUPABASE_SERVICE_KEY", "")
    manquants = [n for n, v in (("SUPABASE_URL", url), ("SUPABASE_SERVICE_KEY", cle)) if not v]
    if manquants and obligatoire:
        raise SystemExit(
            "Variables d'environnement manquantes : " + ", ".join(manquants)
            + ". Voir docs/00-demarrage-rapide.md."
        )
    return Secrets(
        supabase_url=url,
        supabase_cle_service=cle,
        ntfy_topic=os.environ.get("NTFY_TOPIC", "").strip(),
        ntfy_serveur=os.environ.get("NTFY_SERVEUR", "https://ntfy.sh").rstrip("/"),
        gmail_utilisateur=os.environ.get("GMAIL_UTILISATEUR", ""),
        gmail_mot_de_passe=os.environ.get("GMAIL_MOT_DE_PASSE", ""),
    )
