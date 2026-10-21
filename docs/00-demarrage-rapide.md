# 0. Démarrage rapide (le minimum à faire soi-même)

Tout est automatisé sauf deux choses qu'il est impossible de faire à ta place :
créer ton compte Supabase (il faut ton identité) et coller un jeton dans GitHub
(les secrets GitHub ne sont accessibles qu'au propriétaire du dépôt).
Compte 10 minutes.

## 1. Créer le compte Supabase et un jeton (5 min)

1. Va sur https://supabase.com → **Start your project** → **Continue with GitHub**.
   Autorise. Si Supabase te propose de créer une organisation, accepte les
   valeurs par défaut (plan **Free**). **Ne crée pas de projet** : le workflow
   s'en charge.
2. Va sur https://supabase.com/dashboard/account/tokens
   (ou : avatar en bas à gauche → **Account** → **Access Tokens**).
3. **Generate new token**. Si l'écran demande de choisir une organisation
   (« Project / Organization »), appuie plutôt sur le lien **Create legacy
   token** : ce jeton couvre tout le compte, sans organisation à choisir.
4. Nom `ImmoJ` → **Generate token**. Copie le jeton affiché (il commence par
   `sbp_`). Il ne sera plus jamais affiché : si tu le perds, génère-en un autre.
   Pas besoin de créer d'organisation ni de projet : le workflow s'en charge.

## 2. Coller le jeton dans GitHub (1 min)

1. Ouvre https://github.com/flash01-pixel/ImmoJ/settings/secrets/actions
2. **New repository secret** → Name : `SUPABASE_ACCESS_TOKEN` → Secret : colle
   le jeton → **Add secret**.

C'est le seul secret nécessaire. (Optionnel, plus tard : `GMAIL_UTILISATEUR`
et `GMAIL_MOT_DE_PASSE` pour les alertes e-mail, guide 07.)

## 3. Lancer l'installation (2 min, automatique)

1. Onglet **Actions** du dépôt → **Installer Supabase** → **Run workflow** →
   **Run workflow**.
2. Attends la coche verte (2 à 5 minutes : création du projet, tables, règles).
3. Puis **Actions** → **Collecte des annonces** → **Run workflow** : première
   collecte (environ 2 minutes). Ensuite elle tourne seule toutes les heures.
4. Puis **Actions** → **Compiler l'APK** → **Run workflow** : l'appli est
   compilée **avec ta configuration intégrée**.

Tu peux aussi me demander de lancer ces trois workflows : je peux le faire
depuis ici dès que le jeton est en place.

## 4. Installer l'appli (2 min)

1. Depuis le téléphone, connecté à GitHub :
   https://github.com/flash01-pixel/ImmoJ/blob/apk/ImmoJ.apk → télécharger →
   ouvrir → **Installer** (autoriser les sources inconnues si demandé).
2. Ouvre ImmoJ : la liste des annonces s'affiche, rien à saisir.
3. Menu ⋮ → **Paramètres** → **Installer ntfy** (Play Store), puis
   **S'abonner dans ntfy** : les notifications sont actives.

## Comment ça marche

Le jeton Supabase permet au collecteur et à la compilation de l'appli de
retrouver eux-mêmes l'adresse du projet et ses clés (API de gestion Supabase).
Le sujet ntfy est dérivé de l'identifiant aléatoire du projet : impossible à
deviner, identique côté collecteur et côté appli. Rien n'est écrit dans le code.
