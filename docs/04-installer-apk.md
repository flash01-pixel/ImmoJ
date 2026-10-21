# 4. Installer l'appli sur ton téléphone Android

L'APK (le fichier d'installation Android) est compilé par GitHub : tu n'as
rien à installer sur ton PC.

## Récupérer l'APK

1. Onglet **Actions** du dépôt → colonne de gauche **Compiler l'APK**.
2. Clique sur la dernière exécution réussie (coche verte).
3. En bas de la page, section **Artifacts** : clique **ImmoJ-apk**. Un fichier
   `ImmoJ-apk.zip` se télécharge.
4. Décompresse-le : il contient `ImmoJ.apk`.
5. Envoie `ImmoJ.apk` sur ton téléphone (par câble USB, par e-mail à toi-même,
   Google Drive, ou directement en ouvrant GitHub depuis le navigateur du
   téléphone, connecté à ton compte).

Astuce : plus tard, pour une version « officielle », il suffira de créer un
tag `v1.0` dans GitHub (Releases → Draft a new release) : l'APK sera attaché
à la release, téléchargeable sans décompression.

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
