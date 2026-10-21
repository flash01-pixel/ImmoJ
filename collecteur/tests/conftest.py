"""Outils communs aux tests : chargement des pages HTML enregistrées."""

from pathlib import Path

import pytest

FIXTURES = Path(__file__).parent / "fixtures" / "html"


@pytest.fixture
def lire_fixture():
    """Renvoie une fonction qui lit un fichier de tests/fixtures/html/."""
    def _lire(chemin: str) -> str:
        fichier = FIXTURES / chemin
        if not fichier.exists():
            pytest.skip(f"page d'exemple absente : {chemin} "
                        "(à capturer, voir docs/06-depannage.md)")
        return fichier.read_text(encoding="utf-8")
    return _lire
