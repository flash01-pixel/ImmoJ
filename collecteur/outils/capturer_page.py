"""Enregistre des pages HTML dans collecteur/tests/fixtures/html/.

Utilisation locale :
    python collecteur/outils/capturer_page.py "https://site.be/page | immovlan/liste.html"

Chaque argument (ou chaque ligne de --lignes) a la forme « URL | source/fichier.html ».
La page est téléchargée avec des en-têtes de navigateur classiques, puis écrite
telle quelle. Le code HTTP et le début du contenu sont affichés pour vérifier
qu'on n'a pas reçu une page de blocage (captcha, 403...).
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import requests

DOSSIER_FIXTURES = Path(__file__).resolve().parents[1] / "tests" / "fixtures" / "html"

ENTETES = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "fr-BE,fr;q=0.9,en;q=0.5",
}


def capturer(ligne: str) -> None:
    """Télécharge une page et l'écrit dans le dossier des fixtures."""
    if "|" not in ligne:
        print(f"Ligne ignorée (format attendu « URL | source/fichier ») : {ligne}")
        return
    url, chemin = (p.strip() for p in ligne.split("|", 1))
    if not url or not chemin:
        print(f"Ligne ignorée : {ligne}")
        return
    cible = DOSSIER_FIXTURES / chemin
    cible.parent.mkdir(parents=True, exist_ok=True)

    print(f"→ {url}")
    try:
        reponse = requests.get(url, headers=ENTETES, timeout=30)
    except requests.RequestException as erreur:
        print(f"   ERREUR réseau : {erreur}")
        return
    print(f"   HTTP {reponse.status_code}, {len(reponse.text)} caractères, "
          f"URL finale : {reponse.url}")
    cible.write_text(reponse.text, encoding="utf-8")
    print(f"   Enregistré dans {cible.relative_to(DOSSIER_FIXTURES.parents[2])}")
    # Un aperçu pour repérer une page de blocage sans ouvrir le fichier.
    apercu = reponse.text[:600].replace("\n", " ")
    print(f"   Aperçu : {apercu}")


def main() -> int:
    parseur = argparse.ArgumentParser(description=__doc__)
    parseur.add_argument("lignes", nargs="*", help="« URL | source/fichier.html »")
    parseur.add_argument("--lignes", dest="bloc", default="",
                         help="Plusieurs lignes dans une seule chaîne")
    parseur.add_argument("--fichier", default="",
                         help="Fichier texte contenant une ligne par page (les lignes "
                              "commençant par # sont ignorées)")
    args = parseur.parse_args()
    toutes = list(args.lignes) + [l for l in args.bloc.splitlines() if l.strip()]
    if args.fichier:
        for ligne in Path(args.fichier).read_text(encoding="utf-8").splitlines():
            if ligne.strip() and not ligne.lstrip().startswith("#"):
                toutes.append(ligne)
    if not toutes:
        parseur.print_help()
        return 1
    for ligne in toutes:
        capturer(ligne)
    return 0


if __name__ == "__main__":
    sys.exit(main())
