# ImmoJ

Application personnelle pour trouver un logement en province de Luxembourg
(Belgique) : un collecteur Python (GitHub Actions) centralise les annonces de
plusieurs sites dans Supabase, supprime les doublons, et envoie une
notification ntfy pour chaque nouvelle annonce ou baisse de prix. Une appli
Android (Kotlin + Jetpack Compose) affiche le tout.

Le plan complet est dans [PLAN.md](PLAN.md).

## Mise en service (dans l'ordre)

1. [Créer les comptes](docs/01-creer-les-comptes.md) — GitHub, Supabase, ntfy
2. [Configurer Supabase](docs/02-configurer-supabase.md) — exécuter `supabase/schema.sql` puis `policies.sql`
3. [Ajouter les secrets GitHub](docs/03-secrets-github.md) — et lancer une première collecte
4. [Installer l'APK](docs/04-installer-apk.md) — sur le téléphone, puis entrer l'URL et la clé anon

Les workflows GitHub (collecte horaire, compilation de l'APK) n'apparaissent
dans l'onglet *Actions* qu'une fois le code sur la branche `main` : il faut
donc fusionner la branche de développement dans `main` (une *pull request*
suffit).

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `collecteur/` | Python : sources (`immoj/sources/`), dédoublonnage, Supabase, ntfy, tests |
| `supabase/` | schéma SQL et règles de sécurité |
| `android/` | appli Android |
| `.github/workflows/` | collecte horaire, tests, compilation de l'APK, capture de pages |
| `docs/` | guides pas à pas et dépannage |

## Développement

```bash
cd collecteur
pip install -r requirements.txt
pytest              # tests des parsers sur les pages enregistrées
ruff check .        # style
python -m immoj.main --simulation --forcer   # collecte sans rien écrire
```

## État d'avancement

- [x] Étape 1 (MVP) : Immovlan → Supabase → ntfy → appli (liste, détail, statuts, état des sources)
- [ ] Étape 2 : autres portails (Zimmo, Logic-immo, Realo, 2ememain)
- [ ] Étape 3 : carte, filtres, communes/rayon, recherches sauvegardées dans l'appli
- [ ] Étape 4 : Immoweb (direct + alertes e-mail)
- [ ] Étape 5 : agences (`agences.yaml`)
- [ ] Étape 6 : Biddit et ventes notariales (achat)
