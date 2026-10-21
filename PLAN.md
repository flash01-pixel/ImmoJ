# ImmoJ — Plan du projet

Application personnelle pour centraliser les annonces immobilières de la province
de Luxembourg (Belgique), supprimer les doublons, filtrer selon mes critères et
me notifier des nouveautés. Location en priorité, bascule possible sur l'achat.
Budget : 0 €.

> Ce document est le plan à valider **avant** d'écrire le code. Les questions
> ouvertes sont regroupées à la fin.

---

## 1. Vue d'ensemble

```
   ┌──────────────────────────────┐        ┌──────────────────┐
   │ GitHub Actions (gratuit)     │        │ ntfy.sh          │
   │ cron horaire 7h–23h Bxl      │──push──▶ topic privé      │──▶ Appli ntfy
   │ collecteur Python            │        └──────────────────┘    (Android)
   │  • portails, agences, mails  │
   │  • dédoublonnage, géocodage  │
   │  • filtres des recherches    │
   └──────────────┬───────────────┘
                  │ API REST (clé service_role, secret GitHub)
                  ▼
   ┌──────────────────────────────┐
   │ Supabase (offre gratuite)    │
   │ Postgres + API REST auto     │
   │  annonces, sources, prix,    │
   │  statuts, recherches, logs   │
   └──────────────▲───────────────┘
                  │ API REST (clé anon + règles RLS)
   ┌──────────────┴───────────────┐
   │ Appli Android ImmoJ          │
   │ Kotlin + Jetpack Compose     │
   │ liste, carte, détail,        │
   │ filtres, statuts, sources    │
   └──────────────────────────────┘
```

Trois briques indépendantes, reliées uniquement par Supabase :

| Brique | Rôle | Hébergement | Coût |
|---|---|---|---|
| **Collecteur** (Python 3.12) | visite les sources, normalise, dédoublonne, écrit dans Supabase, envoie les notifications | GitHub Actions | 0 € (2 000 min/mois si dépôt privé, illimité si public) |
| **Base de données** | stockage + API REST automatique | Supabase Free | 0 € (500 Mo, largement suffisant) |
| **Appli Android** | consultation et statuts personnels | ton téléphone, APK compilé par GitHub Actions | 0 € |
| **Notifications** | push vers le téléphone | ntfy.sh (serveur public) + appli ntfy | 0 € |

---

## 2. Arborescence du projet

```
ImmoJ/
├── README.md                      Présentation rapide + liens vers docs/
├── PLAN.md                        Ce document
├── docs/                          Guides pas à pas pour débutant (en français)
│   ├── 01-creer-les-comptes.md    GitHub, Supabase, ntfy : captures d'écran décrites, étape par étape
│   ├── 02-configurer-supabase.md  Exécuter schema.sql, récupérer URL et clés
│   ├── 03-secrets-github.md       Ajouter chaque secret, un par un
│   ├── 04-installer-apk.md        Télécharger l'APK depuis GitHub et l'installer sur Android
│   ├── 05-ajouter-une-agence.md   Modifier agences.yaml sans toucher au code
│   └── 06-depannage.md            Que faire quand une source est en panne, lire les logs, etc.
│
├── collecteur/                    Tout le Python
│   ├── requirements.txt
│   ├── pyproject.toml             Config de pytest et ruff (linter)
│   ├── agences.yaml               Liste des agences (nom, commune, url, logiciel, actif)
│   ├── communes.yaml              Les 44 communes de la province : nom, codes postaux, lat/lon
│   ├── immoj/
│   │   ├── __init__.py
│   │   ├── main.py                Point d'entrée : lance chaque source, puis dédoublonnage, puis notifications
│   │   ├── config.py              Lecture des variables d'environnement / secrets
│   │   ├── modele.py              `Annonce` : la structure de données commune à toutes les sources
│   │   ├── http.py                Client HTTP partagé : user-agent, délais entre requêtes, respect de robots.txt, retries
│   │   ├── geocodage.py           Commune → GPS via communes.yaml (puis Nominatim en secours, avec cache)
│   │   ├── dedoublonnage.py       Rapprochement approximatif des annonces identiques
│   │   ├── stockage.py            Lecture/écriture Supabase (upsert annonces, historique prix, journal des collectes)
│   │   ├── recherches.py          Compare les nouvelles annonces aux recherches sauvegardées
│   │   ├── notifications.py       Envoi ntfy (nouvelle annonce, baisse de prix, source en panne)
│   │   └── sources/
│   │       ├── __init__.py        Registre des sources (nom → classe)
│   │       ├── base.py            Classe abstraite `Source` : collecter() -> list[Annonce], gestion des erreurs
│   │       ├── immovlan.py        Phase 1
│   │       ├── zimmo.py           Phase 1
│   │       ├── logicimmo.py       Phase 1
│   │       ├── realo.py           Phase 1
│   │       ├── deuxiememain.py    Phase 1
│   │       ├── immoweb.py         Phase 2 : collecte directe très basse fréquence, désactivation auto si blocage
│   │       ├── immoweb_mail.py    Phase 2 : lecture IMAP des alertes Gmail
│   │       ├── biddit.py          Phase 4 (achat)
│   │       ├── notaires.py        Phase 4 (achat) : notaire.be ventes publiques
│   │       ├── athome.py          Optionnel
│   │       └── agences/           Phase 3 : UN module par logiciel/réseau, piloté par agences.yaml
│   │           ├── __init__.py    Charge agences.yaml et instancie le bon module
│   │           ├── whise.py
│   │           ├── sweepbright.py
│   │           ├── omnicasa.py
│   │           ├── apimo.py
│   │           ├── honesty.py     (réseau)
│   │           ├── era.py         (réseau)
│   │           ├── ...            un fichier par logiciel découvert à l'analyse
│   │           └── generique.py   Site maison : sélecteurs CSS déclarés dans le YAML
│   ├── outils/
│   │   ├── capturer_page.py       Enregistre une page HTML dans tests/fixtures pour écrire un test
│   │   └── analyser_agences.py    Aide à identifier le logiciel d'un site d'agence (phase 3)
│   └── tests/
│       ├── fixtures/html/
│       │   ├── immovlan/liste.html, detail.html
│       │   ├── zimmo/...
│       │   └── ...
│       ├── test_immovlan.py       Un fichier de test par source, basé sur les pages enregistrées
│       ├── test_dedoublonnage.py
│       ├── test_geocodage.py
│       └── test_recherches.py
│
├── supabase/
│   ├── schema.sql                 Tables, index, vues (à coller dans l'éditeur SQL de Supabase)
│   ├── policies.sql               Règles de sécurité (RLS) pour la clé anon de l'appli
│   └── migrations/                Modifications ultérieures, numérotées (001_..., 002_...)
│
├── .github/workflows/
│   ├── collecte.yml               Cron horaire → lance le collecteur
│   ├── tests.yml                  À chaque push : tests Python + lint
│   ├── build-apk.yml              À chaque tag ou à la demande : compile l'APK et le publie dans « Releases »
│   └── capture.yml                À la demande : enregistre une page HTML (utile si pas d'accès local)
│
└── android/                       Projet Android Studio
    ├── settings.gradle.kts
    ├── build.gradle.kts
    ├── gradle/libs.versions.toml  Versions des bibliothèques
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/                Icône, chaînes (français), thème
            └── java/be/immoj/
                ├── ImmoJApp.kt            Application + injection simple
                ├── MainActivity.kt
                ├── data/
                │   ├── SupabaseApi.kt     Interface Retrofit vers l'API REST (PostgREST)
                │   ├── Modeles.kt         Annonce, Source, Statut, RechercheSauvegardee…
                │   ├── Depot.kt           Repository : requêtes + filtres → PostgREST
                │   └── Preferences.kt     DataStore : URL Supabase, clé anon, mode location/achat
                ├── ui/
                │   ├── theme/
                │   ├── navigation/        Routes Compose
                │   ├── liste/             Écran liste (tri, badges Nouveau / Prix baissé)
                │   ├── carte/             Vue carte osmdroid + choix d'un point + rayon
                │   ├── detail/            Fiche annonce (photos, infos, liens sources, téléphone, statut, note)
                │   ├── filtres/           Feuille de filtres + sélection de communes
                │   ├── recherches/        Recherches sauvegardées (CRUD)
                │   ├── sources/           État des sources
                │   └── parametres/        Config Supabase, topic ntfy, test de connexion
                └── util/                  Formatage prix, dates relatives, distance
```

---

## 3. Modèle de données (Supabase / Postgres)

| Table | Contenu | Écrit par |
|---|---|---|
| `annonces` | une ligne par **bien** (après dédoublonnage) : type, transaction, prix, charges, commune, code postal, lat/lon, chambres, surface, PEB, jardin, terrasse, animaux, meublé, date_publication, premiere_detection, derniere_vue, photos (tableau d'URL), description, agence, actif | collecteur |
| `annonce_sources` | une ligne par **publication** : annonce_id, source, id_externe, url, titre, prix_affiche, premiere_vue, derniere_vue. Clé unique (source, id_externe) | collecteur |
| `historique_prix` | annonce_id, prix, date | collecteur |
| `statuts` | annonce_id (unique), statut (`nouveau / vu / interesse / contacte / visite_prevue / refuse`), note, modifie_le | appli |
| `recherches_sauvegardees` | nom, transaction, communes[], centre_lat, centre_lon, rayon_km, prix_min/max, types[], chambres_min, surface_min, jardin, animaux, meuble, peb_max, sources[], notifier (bool), actif | appli (lu par le collecteur) |
| `notifications_envoyees` | recherche_id, annonce_id, type (`nouvelle` / `baisse`), envoyee_le — évite les doublons de notification | collecteur |
| `collectes` | source, debut, fin, ok, nb_trouvees, nb_nouvelles, message_erreur — alimente l'écran « État des sources » | collecteur |
| `communes` | nom, codes postaux, lat, lon — référentiel pour les filtres et le géocodage | schema.sql |

Vues pratiques pour l'appli :
- `v_annonces_liste` : annonce + statut + badge `prix_baisse` (dernier prix < prix précédent) + badge `nouveau` (première détection < 48 h et statut = nouveau).
- `v_etat_sources` : dernière collecte réussie et nombre d'annonces actives par source.

**Sécurité, usage perso.** Le collecteur utilise la clé `service_role` (secret GitHub, jamais dans l'appli). L'appli utilise la clé `anon` avec des règles RLS : lecture sur tout, écriture limitée à `statuts` et `recherches_sauvegardees`. L'APK n'est pas diffusé, donc la clé anon reste privée. Si un jour tu veux plus de sécurité, on ajoute Supabase Auth (connexion email/mot de passe) sans changer le reste.

---

## 4. Le collecteur, en détail

**Déroulé d'une exécution (toutes les heures) :**
1. Charger la configuration (secrets, `agences.yaml`, `communes.yaml`).
2. Lire les recherches sauvegardées dans Supabase.
3. Pour chaque source active, dans un `try/except` individuel :
   - respecter `robots.txt`, délai de 2 à 5 s entre requêtes, user-agent de navigateur, 2 tentatives max ;
   - ne parcourir que les premières pages triées « plus récent » (pas tout le site) ;
   - produire des `Annonce` normalisées ;
   - en cas d'erreur ou de blocage (403, captcha) : journaliser clairement dans `collectes`, passer à la source suivante.
4. Pour chaque annonce : géocoder la commune, chercher un doublon existant, insérer ou mettre à jour, enregistrer le prix dans l'historique si changé.
5. Comparer les annonces nouvelles / en baisse aux recherches sauvegardées, envoyer les notifications ntfy non encore envoyées.
6. Marquer `actif = false` les annonces plus vues depuis 7 jours sur aucune source.
7. Si une source échoue 3 fois de suite : une notification ntfy « source en panne » (une seule, pas à chaque heure).

**Dédoublonnage (`dedoublonnage.py`).** Deux candidats sont considérés identiques si :
- même transaction ET même commune (ou même code postal), ET
- prix identiques à ±3 %, ET
- même nombre de chambres (ou l'un des deux inconnu), ET
- surface identique à ±10 % (ou l'une inconnue), ET
- similarité du titre **ou** des 300 premiers caractères de la description ≥ 70 % (`rapidfuzz`, `token_set_ratio`).
Le doublon est rattaché à l'annonce existante (nouvelle ligne dans `annonce_sources`), sans créer de nouvelle fiche. Les seuils sont dans `config.py` pour être ajustables.

**Fréquence et quota GitHub.**
- 17 exécutions/jour × 30 jours ≈ 510 exécutions/mois.
- Avec un dépôt privé (2 000 min/mois) : **≤ 3,9 min par exécution**. Un run portails seul tient en 1,5 à 2,5 min (cache `pip`, timeout dur à 5 min).
- Pour la phase 3 (≈ 50 agences), les agences seront collectées **en rotation** (un tiers des agences par exécution) ou **toutes les 3 h seulement**, pour rester sous le quota. Immoweb direct : 2 fois par jour maximum.
- Si tu passes le dépôt en **public**, les minutes sont illimitées et ces précautions deviennent du confort. Attention : un dépôt public ne doit contenir **aucun secret** (ils sont dans GitHub Secrets, pas dans le code, c'est prévu ainsi).
- Le cron GitHub est en UTC : on programme `0 5-22 * * *` (couvre 7h–23h de Bruxelles été comme hiver) et le script vérifie l'heure locale de Bruxelles avant de travailler, pour ne rien faire en dehors de 7h–23h (ces exécutions à vide coûtent ~20 s).
- Bonus : l'activité horaire empêche Supabase Free de mettre le projet en pause (règle des 7 jours d'inactivité).

**Tests.** Chaque parser est testé sur des pages HTML enregistrées dans `tests/fixtures/html/<source>/`. Les tests n'accèdent jamais à Internet. Contrainte pratique : depuis mon environnement de travail, les sites immobiliers ne sont pas accessibles (pare-feu). Les pages d'exemple seront donc capturées par toi (script `capturer_page.py`, ou « Enregistrer sous » dans le navigateur) ou par le workflow `capture.yml` qui tourne sur GitHub. Je te guiderai.

---

## 5. L'appli Android

- **Techno** : Kotlin, Jetpack Compose, Material 3, Navigation Compose, Retrofit + kotlinx.serialization (API REST PostgREST), Coil (photos), osmdroid (carte OpenStreetMap), DataStore (préférences). Pas de backend : l'appli parle directement à Supabase.
- **Écrans** : Liste (tri récent/prix, badges) · Carte (marqueurs, point + rayon) · Détail · Filtres (incl. sélection de communes) · Recherches sauvegardées · État des sources · Paramètres.
- **Bascule Location / Achat** : un commutateur en haut de la liste, mémorisé.
- **Filtre par rayon** : calculé côté Supabase via une fonction SQL `annonces_dans_rayon(lat, lon, km)` (formule de haversine, pas besoin de PostGIS) pour rester rapide.
- **Hors-ligne** : cache simple de la dernière liste (Room n'est pas nécessaire au MVP ; à ajouter si besoin).
- **Configuration au premier lancement** : écran Paramètres où tu colles l'URL Supabase et la clé anon (pas de secret compilé dans l'APK, pas besoin de recompiler si ça change).
- **Compilation** : workflow `build-apk.yml` sur GitHub → fichier `ImmoJ.apk` téléchargeable dans l'onglet *Releases*. Installation : autoriser les « sources inconnues » puis ouvrir le fichier (guide `docs/04-installer-apk.md`). Android Studio reste possible mais pas obligatoire.

---

## 6. Phases et ordre de réalisation

| Étape | Contenu | Livrable |
|---|---|---|
| **0. Comptes** | Guides pas à pas : GitHub (dépôt, secrets), Supabase (projet, schéma SQL, clés), ntfy (appli + topic privé type `immoj-k7x2p9qv4m`) | `docs/01…03` |
| **1. MVP bout en bout** | 1 source (Immovlan) → Supabase → notification ntfy → appli qui affiche la liste, le détail et la bascule location/achat. Tests du parser Immovlan. Workflows cron + tests + APK. | première version utilisable |
| **2. Portails** | Zimmo, Logic-immo, Realo, 2ememain + dédoublonnage + historique des prix + badges | phase 1 complète |
| **3. Appli complète** | Carte, filtres, communes/rayon, recherches sauvegardées (→ notifications ciblées), statuts + notes, état des sources | toutes les fonctions |
| **4. Immoweb** | Collecte directe basse fréquence avec auto-désactivation ; lecture IMAP des alertes Gmail | phase 2 |
| **5. Agences** | Analyse des sites, `agences.yaml`, modules par logiciel/réseau, rotation pour le quota | phase 3 |
| **6. Achat** | Biddit, ventes notariales ; athome.lu en option | phase 4 |

Chaque étape se termine par un push sur la branche, des tests qui passent et un petit guide de vérification (« comment voir que ça marche »).

---

## 7. Questions à valider

1. **Dépôt public ou privé ?** Je recommande **privé** (ta recherche de logement reste privée) avec les optimisations de quota ci-dessus. Si tu préfères public, le quota disparaît.
2. **Source du MVP : Immovlan** te convient-il ? (bon compromis : pages HTML lisibles, couverture correcte de la province). Sinon Zimmo.
3. **Clé anon + RLS** pour l'appli (simple, usage perso) : OK, ou veux-tu dès le départ une connexion par email/mot de passe (Supabase Auth) ?
4. **Compilation de l'APK par GitHub Actions** (aucune installation sur ton PC) : OK ?
5. **Capture des pages HTML de test** : tu es d'accord pour me fournir quelques pages enregistrées depuis ton navigateur quand je te le demanderai (ou lancer le workflow `capture.yml`) ?
6. **Identifiant d'application** `be.immoj` et nom affiché « ImmoJ » : OK ?

Dès validation, je commence par l'étape 0 (guides de création des comptes) et l'étape 1 (MVP).
