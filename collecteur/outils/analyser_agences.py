"""Analyse les sites des agences listées dans agences.yaml.

Pour chaque agence, teste l'URL (ou les candidats), suit les redirections, puis
cherche dans le code source des signatures de logiciels immobiliers connus et
des liens vers les pages d'annonces. Imprime un rapport lisible (lancé par le
workflow « Analyser les agences » ; le rapport est dans le journal).

    python collecteur/outils/analyser_agences.py [--seulement "Nom"]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import urljoin, urlsplit

import requests
import yaml

FICHIER = Path(__file__).resolve().parents[1] / "agences.yaml"
ENTETES = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"),
    "Accept-Language": "fr-BE,fr;q=0.9",
}

# Signatures (texte cherché dans le HTML en minuscules) → logiciel
SIGNATURES = [
    ("whise", "whise"), ("sweepbright", "sweepbright"), ("omnicasa", "omnicasa"),
    ("apimo", "apimo"), ("immo-connect", "immoconnect"), ("immoconnect", "immoconnect"),
    ("webimmo", "webimmo"), ("zabun", "zabun"), ("skarabee", "skarabee"),
    ("immotools", "immotools"), ("immo-tools", "immotools"), ("realadvice", "realadvice"),
    ("era.be", "era"), ("century21.be", "century21"), ("trevi.be", "trevi"),
    ("immoweb.be/fr/agence", "immoweb_agence"), ("wp-content", "wordpress"),
    ("wix.com", "wix"), ("squarespace", "squarespace"), ("jimdo", "jimdo"),
    ("shopify", "shopify"), ("webflow", "webflow"), ("cloudflare", "cloudflare"),
    ("captcha", "captcha"), ("just a moment", "cloudflare_challenge"),
]
MOTS_LISTE = ("louer", "location", "huur", "vendre", "vente", "koop", "bien", "propert",
              "annonce", "estate", "immo", "offre", "aanbod")


def tester(url: str) -> tuple[int, str, str, str]:
    """Renvoie (code, url finale, titre, html minuscules) ; code 0 si injoignable."""
    try:
        r = requests.get(url, headers=ENTETES, timeout=20, allow_redirects=True)
    except requests.RequestException as e:
        return 0, url, str(e)[:80], ""
    html = r.text
    m = re.search(r"<title[^>]*>(.*?)</title>", html, re.S | re.I)
    titre = re.sub(r"\s+", " ", m.group(1)).strip()[:80] if m else ""
    return r.status_code, r.url, titre, html.lower()


def analyser(html: str, base: str) -> tuple[list[str], list[str]]:
    logiciels = sorted({nom for sig, nom in SIGNATURES if sig in html})
    liens: list[str] = []
    for href in re.findall(r'href=["\']([^"\']+)["\']', html):
        bas = href.lower()
        if any(m in bas for m in MOTS_LISTE) and not bas.startswith(
                ("mailto:", "tel:", "#", "javascript")):
            complet = urljoin(base, href)
            if urlsplit(complet).netloc == urlsplit(base).netloc and complet not in liens:
                liens.append(complet)
    return logiciels, liens[:8]


def main() -> int:
    parseur = argparse.ArgumentParser()
    parseur.add_argument("--seulement", default="",
                         help="ne tester que les agences dont le nom contient ce texte")
    args = parseur.parse_args()
    agences = yaml.safe_load(FICHIER.read_text(encoding="utf-8"))["agences"]
    for a in agences:
        if args.seulement and args.seulement.lower() not in a["nom"].lower():
            continue
        candidats = [a["url"]] if a.get("url") else list(a.get("candidats") or [])
        print(f"\n=== {a['nom']} ({a['commune']}) ===")
        if not candidats:
            print("   (aucune URL ni candidat)")
            continue
        for url in candidats:
            code, finale, titre, html = tester(url)
            if code == 0:
                print(f"   ✗ {url} : injoignable ({titre})")
                continue
            logiciels, liens = analyser(html, finale) if html else ([], [])
            print(f"   {'✓' if code == 200 else '?'} {url} → HTTP {code} {finale}")
            print(f"      titre    : {titre}")
            print(f"      logiciel : {', '.join(logiciels) or 'aucune signature'}")
            for lien in liens:
                print(f"      lien     : {lien}")
            if code == 200 and html:
                break
    return 0


if __name__ == "__main__":
    sys.exit(main())
