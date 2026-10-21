# 2. Configurer Supabase (créer les tables)

Deux fichiers SQL créent tout ce qu'il faut : `supabase/schema.sql` (tables,
vues, fonctions, liste des communes) puis `supabase/policies.sql` (règles de
sécurité). Il faut les exécuter **une fois**, dans cet ordre.

1. Dans Supabase, ouvre ton projet `immoj`.
2. Colonne de gauche : icône **SQL Editor** (un symbole `>_` ou « SQL »).
3. Clique **+ New query** (ou « New SQL snippet »).
4. Ouvre le fichier `supabase/schema.sql` sur GitHub :
   https://github.com/flash01-pixel/ImmoJ/blob/main/supabase/schema.sql
   (si tu es encore sur la branche de développement, choisis-la dans le menu
   « main » en haut à gauche de GitHub). Clique l'icône **Copy raw file**
   (deux carrés superposés, en haut à droite du fichier).
5. Colle dans l'éditeur SQL de Supabase, puis clique **Run** (en bas à
   droite, ou Ctrl+Entrée). Le message doit être « Success. No rows
   returned ».
6. Recommence (nouvelle query, copier, coller, Run) avec
   `supabase/policies.sql`.
7. Vérification : colonne de gauche, **Table Editor**. Tu dois voir les tables
   `annonces`, `annonce_sources`, `collectes`, `communes` (44 lignes),
   `historique_prix`, `notifications_envoyees`, `recherches_sauvegardees`,
   `statuts`.

C'est fini pour Supabase. Étape suivante : **03-secrets-github.md**.

## En cas d'erreur

- « relation "…" already exists » : tu as lancé le script deux fois, c'est
  sans conséquence.
- « policy "…" already exists » : idem pour policies.sql.
- Pour tout repartir de zéro : SQL Editor → `drop schema public cascade;
  create schema public;` puis relance les deux fichiers.
