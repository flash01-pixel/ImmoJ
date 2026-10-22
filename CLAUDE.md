# ImmoJ — repères pour les sessions Claude Code

Appli Android (Kotlin/Compose, `android/`) + collecteur Python (`collecteur/`) + Supabase (`supabase/`).
Lis `README.md`, `PLAN.md`, `docs/` et `CHANGELOG.md` avant de toucher au code.

## Règles de travail (demandées par le propriétaire, débutant, qui teste depuis son téléphone)
- Travailler sur une branche (`immoj-1.2`…), petits commits explicites en français, push après chaque commit.
- **Jamais de merge sur `main` sans accord écrit.** Les PR se fusionnent via l'outil GitHub après accord.
- Chaque lot : tests verts (pytest + ruff ; tests JVM Kotlin), APK compilé par GitHub sans erreur,
  versionName `1.2.0-lotN`, entrée dans `CHANGELOG.md`, puis message court : changements, lien de l'APK,
  checklist « Comment vérifier sur ton téléphone » (5–10 points), question « OK pour le lot suivant ? ».
- Interface et commentaires en français. Aucun service payant, pas de collecte derrière une connexion,
  robots.txt et délais de politesse respectés, aucune fonction supprimée sans accord.
- Toute action fréquente dans l'appli doit tenir en 2 gestes ; chaque écran affiche son compteur.

## Commandes
- Collecteur : `cd collecteur && ruff check immoj tests outils && python -m pytest -q`
  (ne pas masquer le code de sortie de pytest avec `| tail`).
- Appli : pas de SDK Android dans le conteneur. La compilation et les tests JVM tournent sur GitHub
  (`build-apk.yml`, déclenché par tout push touchant `android/`). Logs : outil `get_job_logs`.
- APK : publié dans le dépôt PRIVÉ `flash01-pixel/immoj-apk` (secret `APK_REPO_TOKEN`) : `main` → branche
  `apk`, autres branches → `apk-test` (fichier `ImmoJ.apk`, « Download raw file »). Le dépôt `ImmoJ` est
  public : ne jamais y publier l'APK (il contient la clé anon Supabase). Le conteneur n'a pas accès à
  `immoj-apk` : vérifier la publication dans le journal du workflow (« APK publié : … »).
- Pages réelles : le conteneur ne joint pas les sites immobiliers. Utiliser `capture.yml`
  (`collecteur/outils/pages_a_capturer.txt`, syntaxe `URL | source/fichier | {"En-tête": …} | POST a=b`),
  `analyse-agences.yml` et `collecte.yml` (lancement manuel = `--forcer`, toutes les sources).
- Données réelles : branche `donnees` (`annonces.json`, `base.json`).
- Supabase : `installer-supabase.yml` exécute `schema.sql` + `policies.sql` (idempotents) sur la branche choisie.

## Pièges connus
- Le répertoire de travail du Bash change entre les appels : utiliser des chemins absolus.
- `create or replace view` refuse un changement de colonnes sur `a.*` : `schema.sql` fait `drop view … cascade`
  puis recrée la vue et la fonction `annonces_dans_rayon`.
- Sites sans `charset` : `requests` décode en latin-1 (« € » → « â¬ ») ; `http.corriger_encodage` règle ça.
- Dates Postgres « +00:00 » : toujours passer par `util/Dates.kt` (jamais `Instant.parse`).
- Quota GitHub Actions : dépôt privé = 2 000 min/mois ; un passage de collecte dure 2–5 min.
- Les tests JVM Kotlin sont dans `android/app/src/test` ; ils bloquent l'APK s'ils échouent.
- Clé de signature de développement : `android/signature/immoj-debug.keystore` (mot de passe `immoj-debug`).
