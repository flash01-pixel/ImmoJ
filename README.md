# ImmoJ

Application personnelle pour trouver un logement en province de Luxembourg
(Belgique) : un collecteur Python (GitHub Actions) centralise les annonces de
plusieurs sites dans Supabase, supprime les doublons, et envoie une
notification ntfy pour chaque nouvelle annonce ou baisse de prix. Une appli
Android (Kotlin + Jetpack Compose) affiche le tout.

Le plan complet est dans [PLAN.md](PLAN.md).

## Mise en service

**[Démarrage rapide](docs/00-demarrage-rapide.md)** : sans compte (dépôt
public, annonces publiées par GitHub, tout sur le téléphone) ou avec Supabase
(un jeton, installation automatique, appli préconfigurée).

Voie manuelle détaillée (si tu préfères tout faire toi-même) :
guides [01](docs/01-creer-les-comptes.md), [02](docs/02-configurer-supabase.md),
[03](docs/03-secrets-github.md), [04](docs/04-installer-apk.md).
Optionnel : [alertes e-mail](docs/07-alertes-email.md) pour Immoweb, Zimmo et Logic-immo.

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
- [x] Étape 2 : Immoweb (JSON direct, cadence réduite), 2ememain (API), lecteur d'alertes e-mail (Immoweb, Zimmo, Logic-immo)
- [x] Étape 3 : carte, filtres, communes/rayon, recherches sauvegardées dans l'appli (0.2.0)
- [x] Étape 5 : agences — 10 sites collectés (Whise, Omnicasa, WordPress) ; les autres sont couvertes par les portails (voir `docs/05`)
- [x] Étape 6 : ventes notariales via immo.notaire.be, ventes Biddit incluses (achat)
