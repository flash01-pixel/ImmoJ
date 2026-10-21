# 6. Dépannage

## Lire les journaux du collecteur

Onglet **Actions** → **Collecte des annonces** → cliquer une exécution →
cliquer le job **collecter** → dérouler « Lancer le collecteur ».

Lignes typiques :
- `[immovlan] location : 60 annonces` : la page a été lue.
- `[immovlan] OK : 60 annonces vues, 3 nouvelles` : intégration réussie.
- `[immovlan] BLOQUÉ : HTTP 403` : le site refuse GitHub. La source est marquée
  « bloquée » dans l'appli (écran État des sources) ; les autres continuent.
- `Hors plage horaire` : normal entre 23h et 7h.
- `Variables d'environnement manquantes` : un secret est absent ou mal nommé.

## L'appli affiche une erreur

| Message | Cause probable | Solution |
|---|---|---|
| Supabase n'est pas configuré | URL/clé absentes | Paramètres → remplir et Tester |
| Clé refusée (401) | mauvaise clé, ou clé `service_role` au lieu de `anon` | copier la clé `anon public` |
| Vue introuvable (404) | `schema.sql` pas exécuté | guide 02 |
| Supabase a refusé l'écriture | `policies.sql` pas exécuté | guide 02 |
| Pas de connexion Internet | URL fausse ou projet en pause | vérifier l'URL ; Supabase → Restore project |
| Aucune annonce | le collecteur n'a pas encore tourné | Actions → Run workflow |

## Une source est en panne

L'écran **État des sources** montre la dernière réussite et l'erreur. Après
3 échecs consécutifs, une notification ntfy « Source en panne » est envoyée
(une seule). Les sites changent parfois leur HTML : dans ce cas le parser doit
être adapté. Pour cela il faut une page récente :

1. Modifier `collecteur/outils/pages_a_capturer.txt` (une ligne
   `URL | source/fichier.html`) et pousser : le workflow « Capturer des pages
   HTML » enregistre la page dans `collecteur/tests/fixtures/html/`.
2. Ou, depuis un navigateur : ouvrir la page, Ctrl+S, « Page web, HTML
   uniquement », et déposer le fichier dans le même dossier.

## Quota GitHub Actions (dépôt privé)

Settings → **Billing and plans** → *Usage this month* → Actions. Si tu
approches des 2 000 minutes, réduis la plage horaire dans
`.github/workflows/collecte.yml` (ex. `0 6-20 * * *`) ou `PAGES_MAX` dans
`collecteur/immoj/config.py`.
