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

## Minutes GitHub Actions

Le dépôt est public : les minutes sont gratuites et illimitées. Pour information,
la collecte consomme ≈ 1 500 à 1 800 minutes par mois (17 passages légers de 2 à
5 min par jour + 1 passage profond de 8 à 15 min). Un dépôt privé n'en aurait que
2 000 : il faudrait alors réduire la plage horaire dans
`.github/workflows/collecte.yml` (ex. `0 6-20 * * *`) ou `PAGES_MAX` dans
`collecteur/immoj/config.py`. Suivi : Settings → **Billing and plans** → Actions.

## Passage profond, réparation, couverture (depuis le lot 4)

- Chaque heure : passage **léger** (3 premières pages des portails, fraîcheur).
  À 7 h (Bruxelles) : passage **profond** (toutes les pages, jusqu'à 80 par
  portail), suivi d'une **réparation** de la base : annonces actives sans
  publication (orphelines) désactivées, doublons entre sites fusionnés (même
  commune, prix, chambres, surface à ±5 %), annonceur et type recalculés.
- Une annonce n'est désactivée (plus vue depuis 7 jours) que si chacune de ses
  sources a fait un passage profond réussi depuis : un passage léger ne voit
  pas les annonces reculées au-delà de la page 3.
- Lancement manuel : Actions → **Collecte des annonces** → *Run workflow* →
  cocher « Passage profond » et/ou « Réparer la base ».
- Le journal affiche par source `couverture : N lue(s) / M disponible(s), P de
  particuliers` ; l'écran **État des sources** de l'appli montre la même chose.
  « disponibles » vient du site (`totalItems` Immoweb, dernière page de la
  pagination Immovlan) ; 2ememain et les agences ne donnent pas de total.
- 2ememain ignore le filtre par catégorie de son API (vérifié par capture) :
  le collecteur interroge 5 centres (Arlon, Bastogne, Libramont, Marche,
  Vielsalm) à 30 km et garde la commune citée en tête du libellé
  (« Hotton + Partie De Soy » → Hotton ; « Somme-Leuze + … » → hors province).
