"""Administration de Supabase via son API de gestion (api.supabase.com).

Objectif : zéro configuration manuelle. Avec un seul secret GitHub,
SUPABASE_ACCESS_TOKEN (jeton personnel créé dans le compte Supabase), ce
script sait :

    installer    créer le projet « immoj » s'il n'existe pas, attendre qu'il
                 soit prêt, puis exécuter supabase/schema.sql et policies.sql ;
    env          écrire SUPABASE_URL, SUPABASE_SERVICE_KEY, SUPABASE_ANON_KEY
                 et NTFY_TOPIC dans le fichier $GITHUB_ENV (pour le collecteur) ;
    gradle       afficher les arguments Gradle pour compiler l'APK préconfiguré.

Le topic ntfy est dérivé de l'identifiant (aléatoire) du projet : stable,
difficile à deviner, identique pour le collecteur et l'appli.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import secrets
import string
import sys
import time
from pathlib import Path

import requests

API = "https://api.supabase.com/v1"
NOM_PROJET = "immoj"
REGION = "eu-west-1"   # Irlande (la plus proche de la Belgique sur l'offre gratuite)
DOSSIER_SQL = Path(__file__).resolve().parents[2] / "supabase"


class Admin:
    def __init__(self, jeton: str) -> None:
        if not jeton:
            raise SystemExit("SUPABASE_ACCESS_TOKEN manquant (voir docs/00-demarrage-rapide.md)")
        self.session = requests.Session()
        self.session.headers["Authorization"] = f"Bearer {jeton}"

    def _get(self, chemin: str, **params):
        r = self.session.get(f"{API}{chemin}", params=params, timeout=60)
        if r.status_code >= 400:
            raise SystemExit(f"Supabase API GET {chemin} : HTTP {r.status_code} {r.text[:300]}")
        return r.json()

    def _post(self, chemin: str, corps: dict):
        r = self.session.post(f"{API}{chemin}", json=corps, timeout=120)
        if r.status_code >= 400:
            raise SystemExit(f"Supabase API POST {chemin} : HTTP {r.status_code} {r.text[:300]}")
        return r.json() if r.text else {}

    # --- projet ---------------------------------------------------------------
    def projet(self) -> dict | None:
        for p in self._get("/projects"):
            if p.get("name") == NOM_PROJET:
                return p
        return None

    def organisation(self) -> dict:
        """Première organisation du compte ; créée si le compte n'en a pas encore."""
        orgs = self._get("/organizations")
        if not orgs:
            print("Aucune organisation Supabase : création de « ImmoJ » (offre gratuite)…")
            self._post("/organizations", {"name": "ImmoJ"})
            # On relit la liste : l'identifiant attendu par /projects est celui de la liste.
            orgs = self._get("/organizations")
        if not orgs:
            raise SystemExit("impossible de créer ou de lire une organisation Supabase")
        return orgs[0]

    def creer_projet(self) -> dict:
        org = self.organisation()
        mdp = "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(32))
        print(f"Création du projet « {NOM_PROJET} » dans l'organisation "
              f"« {org.get('name')} » (id {org.get('id')})…")
        # Le mot de passe de la base n'est jamais réutilisé : tout passe par l'API.
        corps = {"name": NOM_PROJET, "organization_id": org["id"],
                 "db_pass": mdp, "region": REGION}
        r = self.session.post(f"{API}/projects", json=corps, timeout=120)
        if r.status_code >= 400:
            raise SystemExit(f"Supabase API POST /projects : HTTP {r.status_code} {r.text[:300]}\n"
                             f"Organisation reçue : {org}")
        return r.json()

    def attendre_pret(self, ref: str, max_s: int = 600) -> None:
        debut = time.monotonic()
        while time.monotonic() - debut < max_s:
            statut = self._get(f"/projects/{ref}").get("status")
            print(f"   état du projet : {statut}")
            if statut == "ACTIVE_HEALTHY":
                return
            time.sleep(15)
        raise SystemExit("le projet n'est pas devenu actif dans le délai imparti")

    # --- clés et SQL -----------------------------------------------------------
    def cles(self, ref: str) -> dict[str, str]:
        brut = self._get(f"/projects/{ref}/api-keys", reveal="true")
        return choisir_cles(brut)

    def executer_sql(self, ref: str, sql: str) -> None:
        self._post(f"/projects/{ref}/database/query", {"query": sql})


def choisir_cles(liste: list[dict]) -> dict[str, str]:
    """Retrouve la clé anon (publique) et la clé service (secrète) dans la réponse API.

    Supabase renvoie les clés historiques (name = anon / service_role) et, sur
    les projets récents, les nouvelles (type = publishable / secret).
    """
    anon = service = ""
    for c in liste:
        nom, typ, valeur = c.get("name", ""), c.get("type", ""), c.get("api_key") or ""
        if not valeur:
            continue
        if nom == "anon" or typ == "publishable":
            anon = anon or valeur
        elif nom == "service_role" or typ == "secret":
            service = service or valeur
    if not anon or not service:
        noms = [c.get("name") for c in liste]
        raise SystemExit(f"clés introuvables dans la réponse API : {noms}")
    return {"anon": anon, "service": service}


def topic_ntfy(ref: str) -> str:
    """Topic ntfy stable et difficile à deviner, dérivé de l'identifiant du projet."""
    return "immoj-" + hashlib.sha256(f"ntfy:{ref}".encode()).hexdigest()[:14]


def commande_installer(admin: Admin) -> int:
    p = admin.projet()
    if p is None:
        p = admin.creer_projet()
    ref = p["id"]
    print(f"Projet : {ref} (https://{ref}.supabase.co)")
    admin.attendre_pret(ref)
    for nom in ("schema.sql", "policies.sql"):
        print(f"Exécution de {nom}…")
        admin.executer_sql(ref, (DOSSIER_SQL / nom).read_text(encoding="utf-8"))
    cles = admin.cles(ref)
    print("Base installée. Clés récupérées : anon", "OK" if cles["anon"] else "absente",
          "; service", "OK" if cles["service"] else "absente")
    print(f"Topic ntfy : {topic_ntfy(ref)}")
    return 0


def commande_env(admin: Admin) -> int:
    p = admin.projet()
    if p is None:
        # Pas bloquant : le stockage JSON (mode sans compte) continue de fonctionner.
        print("Projet « immoj » introuvable : lance le workflow « Installer Supabase ». "
              "En attendant, seul le stockage JSON est alimenté.")
        return 0
    ref = p["id"]
    cles = admin.cles(ref)
    valeurs = {
        "SUPABASE_URL": f"https://{ref}.supabase.co",
        "SUPABASE_SERVICE_KEY": cles["service"],
        "SUPABASE_ANON_KEY": cles["anon"],
        "NTFY_TOPIC": os.environ.get("NTFY_TOPIC") or topic_ntfy(ref),
    }
    fichier_env = os.environ.get("GITHUB_ENV")
    for nom, valeur in valeurs.items():
        if nom != "SUPABASE_URL":
            print(f"::add-mask::{valeur}")   # masque la valeur dans les journaux GitHub
        if fichier_env:
            with open(fichier_env, "a", encoding="utf-8") as f:
                f.write(f"{nom}={valeur}\n")
        else:
            print(f"export {nom}={valeur}")
    print("Accès Supabase résolus via le jeton de gestion.")
    return 0


def main() -> int:
    parseur = argparse.ArgumentParser(description=__doc__)
    parseur.add_argument("commande", choices=("installer", "env"))
    args = parseur.parse_args()
    admin = Admin(os.environ.get("SUPABASE_ACCESS_TOKEN", "").strip())
    return commande_installer(admin) if args.commande == "installer" else commande_env(admin)


if __name__ == "__main__":
    sys.exit(main())
