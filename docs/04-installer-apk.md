# 4. Installer l'appli sur ton téléphone Android

L'APK (le fichier d'installation Android) est compilé par GitHub : tu n'as
rien à installer sur ton PC.

## Récupérer l'APK

Le plus simple, **directement depuis le téléphone** :

1. Dans le navigateur du téléphone, connecté à ton compte GitHub, ouvre
   https://github.com/flash01-pixel/ImmoJ/blob/apk/ImmoJ.apk
   (c'est la branche `apk`, mise à jour à chaque compilation).
2. Appuie sur le bouton de téléchargement (icône flèche vers le bas, ou
   « Download raw file » / « Raw »). Le fichier `ImmoJ.apk` se télécharge.

Autre méthode, depuis un PC : onglet **Actions** → **Compiler l'APK** →
dernière exécution réussie → section **Artifacts** → `ImmoJ-apk` (archive zip
à décompresser), puis envoyer `ImmoJ.apk` sur le téléphone (câble, e-mail,
Drive).

## Installer

1. Sur le téléphone, ouvre le fichier `ImmoJ.apk` (depuis l'appli Fichiers ou
   la notification de téléchargement).
2. Android demande d'autoriser l'installation d'applis inconnues pour
   l'appli d'où tu ouvres le fichier (Fichiers, Chrome, Gmail…) : **Paramètres
   → autoriser**, puis reviens et appuie **Installer**.
3. Si « Play Protect » affiche un avertissement (appli non reconnue), choisis
   **Installer quand même** : c'est normal pour une appli qui n'est pas sur le
   Play Store.
4. Ouvre **ImmoJ**.

## Première configuration

Au premier lancement, l'écran **Paramètres** s'affiche :

1. **URL du projet** : la même valeur que `SUPABASE_URL`.
2. **Clé anon** : la clé `anon public` (ou `sb_publishable_…`), **pas** la
   `service_role`.
3. Appuie **Tester** : « Connexion réussie ✔ » doit apparaître.
4. Appuie **Enregistrer**. La liste des annonces s'affiche.

Pour mettre à jour l'appli : télécharge le nouvel APK et installe-le par-dessus
(les réglages sont conservés).
