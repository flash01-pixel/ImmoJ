# Journal des modifications

## 1.2.0-lot2 (22 octobre 2026) — Carte : poser et retirer le cercle facilement

- Pose : appui maintenu n'importe où → vibration, viseur et aperçu du cercle 70 dp au-dessus du doigt ; la carte ne défile plus, le centre suit le doigt avec la commune la plus proche et le nombre d'annonces en direct ; relâcher = poser. Sortir de la carte ou ✕ annule. Un simple appui ou un appui sur un marqueur ne déplace jamais le cercle.
- Déplacer : glisser directement le point central (zone de 48 dp, sans appui long). Rayon : poignée sur le bord (48 dp), puces 2 · 5 · 10 · 15 · 20 · 30 km, curseur ; libellé et compteur en direct, liste recalculée au relâchement seulement. Une seule constante pour défaut (10 km) et bornes (0,5–60).
- Retirer : puce « 12 km autour de Libramont ✕ » en haut de la carte et en tête de liste ; un appui retire, « Zone retirée — Annuler ». « Retirer » dans les Filtres fait pareil.
- Boutons « Ma position » (permission demandée au premier appui) et « Toute la province ».
- Panneau du bas compact sur une ligne (rayon, curseur, « 12 communes »), dépliable.
- Marqueurs regroupés avec leur nombre (appui = zoom), étiquettes de prix, annonces hors zone en grisé ; dessin direct sans objet par annonce.
- Technique : un calque osmdroid dédié au geste (`CalqueCercle`), calques mis à jour sans être retirés, aucune requête pendant un geste ; tests JVM de la géométrie (décalage du viseur, rayon depuis la poignée, bornes, regroupement).


## 1.2.0-lot1 (22 octobre 2026) — Fiabilité et compteurs partout

### Appli
- Toutes les annonces actives sont chargées (index léger paginé, plus aucune limite silencieuse de 300) ; filtrage, tri et comptages en mémoire, identiques en mode Supabase et en mode sans compte.
- Compteurs : « Location (397) · Achat (1 189) », « 37 annonces · 5 nouvelles » ou « 37 sur 397 », « Données du 22/10 à 02:34 », pastille de nouveautés sur l'onglet Annonces, « 37 dans la zone · 4 sans position » sur la carte, « N annonces · M nouvelles depuis ta dernière visite » sur chaque recherche sauvegardée, total / par source / particuliers dans l'État des sources.
- Filtres appliqués immédiatement (rien de perdu au retour), bouton « Voir N annonces », nombre à côté de chaque option (type, commune, source, PEB, période, jardin…), suggestions avec leur gain quand il n'y a aucun résultat, « Réinitialiser » avec Annuler.
- Filtre « Sites d'agences » corrigé (tous les logiciels), « Notaires / Biddit » ajouté, Zimmo / Logic-immo remplacés par « Alertes e-mail ».
- Dates : un seul utilitaire (`Dates`) qui lit « +00:00 » sur toutes les versions d'Android ; « il y a 3 h », période « Détectées depuis » et badges fonctionnent partout.
- Fiche → liste : statut répercuté tout de suite ; ouvrir une annonce « Nouveau » la marque « Vu » ; annonces « Refusé » masquées par défaut (interrupteur dans les filtres, rappel dans la liste).
- Chargements annulables : le dernier changement demandé est toujours celui affiché ; aucun téléchargement sur un simple changement de filtre.
- Rayon : une seule constante (défaut 10 km, bornes 0,5–60 km).
- Paramètres → À propos : version, build, commit, branche, date de compilation.

### APK
- Clé de signature de développement fixe (`android/signature/immoj-debug.keystore`) : chaque APK s'installe par-dessus le précédent. Une désinstallation unique est nécessaire pour passer de la 1.1.0 à la première 1.2.0.
- versionCode = numéro d'exécution GitHub ; `main` publie sur la branche `apk`, les branches de travail sur `apk-test` ; tests unitaires JVM exécutés avant la compilation.

### Collecteur
- Export et vue Supabase : champ `photo` (vignette) ; migration de la vue incluse dans `schema.sql`.

## 1.1.0 (22 octobre 2026)
- Carte : cercle de recherche manipulable ; fiche : toutes les photos et détails ; paramètres enrichis ; actualisation réelle ; 23 agences collectées.

## 1.0.0 (21 octobre 2026)
- Première version complète : portails, agences, notaires, Supabase, mode sans compte, notifications.
