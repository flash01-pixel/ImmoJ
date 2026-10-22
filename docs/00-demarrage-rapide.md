# 0. Démarrage rapide

Deux façons d'utiliser ImmoJ. La première ne demande aucun compte.

## Voie 1 : sans compte (recommandée)

Le collecteur tourne sur GitHub toutes les heures et publie les annonces dans
un fichier du dépôt (branche `donnees`). L'appli télécharge ce fichier, garde
tout sur le téléphone (statuts, notes, recherches sauvegardées) et affiche des
notifications Android pour les nouveautés et les baisses de prix.

**Une seule condition : le dépôt doit être public** (sinon le téléphone ne
peut pas lire le fichier sans mot de passe). Ce qui devient visible : le code,
les guides et les annonces collectées, déjà publiques sur les sites. Tes
statuts, notes et critères restent sur ton téléphone.

1. https://github.com/flash01-pixel/ImmoJ/settings → tout en bas, « Danger
   Zone » → **Change repository visibility** → **Make public** → confirmer.
2. Sur le téléphone, connecté à GitHub :
   https://github.com/flash01-pixel/immoj-apk/blob/apk/ImmoJ.apk → télécharger →
   ouvrir → **Installer** (autoriser les sources inconnues si demandé).
3. Ouvre ImmoJ : la liste s'affiche. Accepte les notifications quand Android
   le demande. C'est tout.

Les annonces se mettent à jour toutes les heures (collecte GitHub), et l'appli
vérifie les nouveautés en arrière-plan (fréquence réglable).

### Ce que propose l'appli (1.2)

- **Annonceur** : chaque annonce porte « Particulier », « Agence » (avec son
  nom) ou « Notaire » ; filtre rapide « Particuliers », compteurs dans Filtres et
  dans État des sources. Plus jamais « PRIVATE » ni un prénom affiché comme agence.
- **Types** : appartement, maison, studio, kot, chambre/colocation, terrain,
  immeuble, commerce/bureau, garage/parking. Moins de 1 % des annonces restent
  « Bien » (type inconnu).
- **Position** : la fiche indique si la position est exacte ou au centre de la
  commune ; une annonce dont le site ne donne pas la localité reste sans
  position plutôt que placée chez l'agence.

- **Partager vers ImmoJ** : depuis Facebook, Messenger ou un navigateur, « Partager » →
  ImmoJ crée une fiche manuelle (lien, prix, commune, note, photo) dans la même liste.
  Bouton « + » pour saisir une annonce à la main. Voir docs/08.

### Ce que proposait déjà la 1.1.0

- **Actualiser** : le bouton en haut, ou la liste tirée vers le bas,
  retélécharge vraiment les données. Le menu ⋮ → « Lancer une collecte
  maintenant » fait tourner le collecteur sur GitHub tout de suite (il faut un
  jeton GitHub dans les paramètres : GitHub → Settings → Developer settings →
  Fine-grained tokens → dépôt ImmoJ, permission *Actions : Read and write*).
- **Carte** : appui long pour placer un cercle de recherche ; glisse son centre
  ou la poignée sur son bord pour le déplacer et changer le rayon, ou utilise le
  curseur. Les communes couvertes et le nombre d'annonces s'affichent en
  direct ; « Retirer » supprime le cercle. Seule la province de Luxembourg est
  prise en compte (le pointillé vert en montre le contour approximatif).
- **Fiche** : toutes les photos (appui = plein écran) et toutes les
  informations trouvées sur la fiche d'origine (salles de bain, garage,
  terrain, année, chauffage, PEB…).
- **Paramètres** : thème clair/sombre/système, type d'alerte (mes recherches,
  toutes les nouveautés, aucune), baisses de prix, silence la nuit, fréquence de
  vérification, photos dans la liste, jeton GitHub, compte Supabase facultatif.

## Voie 2 : avec Supabase (optionnelle)

Même fonctionnement, mais les données vivent dans une base Supabase et les
notifications passent par ntfy. Utile seulement si tu veux garder le dépôt
privé ou utiliser l'appli sur plusieurs téléphones avec les mêmes statuts.

1. Compte Supabase : https://supabase.com → **Start your project** →
   **Continue with GitHub**. Ne crée pas de projet.
2. Jeton : https://supabase.com/dashboard/account/tokens → **Generate new
   token** → lien **Create legacy token** → nom `ImmoJ` → copie le jeton `sbp_…`.
3. Secret GitHub : https://github.com/flash01-pixel/ImmoJ/settings/secrets/actions
   → **New repository secret** → `SUPABASE_ACCESS_TOKEN` → colle → **Add secret**.
4. Onglet **Actions** → **Installer Supabase** → **Run workflow** (crée
   l'organisation, le projet, les tables). Puis **Compiler l'APK** → **Run
   workflow** : l'appli est compilée avec la configuration intégrée.
5. Installe l'APK (étape 2 de la voie 1). Dans Paramètres : **Installer ntfy**
   puis **S'abonner dans ntfy** pour les notifications.

Pour revenir à la voie 1 : Paramètres → vider les deux champs → Enregistrer.
