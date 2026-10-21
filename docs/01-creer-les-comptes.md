# 1. Créer les comptes (GitHub, Supabase, ntfy)

Tout est gratuit. Compte environ 30 minutes. Fais les étapes dans l'ordre et
note au fur et à mesure les valeurs demandées dans un fichier texte **sur ton
PC uniquement** (jamais dans le dépôt GitHub) : tu en auras besoin à l'étape 3.

Valeurs à noter :

| Nom | Où la trouver | Exemple |
|---|---|---|
| `SUPABASE_URL` | Supabase → Project Settings → Data API | `https://abcdefgh.supabase.co` |
| `SUPABASE_SERVICE_KEY` | Supabase → Project Settings → API Keys → `service_role` | longue chaîne commençant par `eyJ…` ou `sb_secret_…` |
| Clé `anon` | Supabase → Project Settings → API Keys → `anon` / `publishable` | `eyJ…` ou `sb_publishable_…` |
| `NTFY_TOPIC` | tu l'inventes | `immoj-k7x2p9qv4m-arlon` |

---

## A. GitHub (tu l'as déjà)

Le dépôt `ImmoJ` existe déjà. Vérifie deux choses :

1. Ouvre https://github.com/flash01-pixel/ImmoJ → l'onglet **Settings**
   (tout à droite). Dans la colonne de gauche, **General** : tout en bas,
   « Danger Zone » indique si le dépôt est **private**. Il doit l'être
   (ta recherche de logement reste privée).
2. Onglet **Actions** : si un message propose d'activer les Actions, clique
   « I understand my workflows, go ahead and enable them ».

Rien d'autre à faire côté GitHub pour l'instant ; les secrets viennent à
l'étape 3.

---

## B. Supabase (base de données)

1. Va sur https://supabase.com et clique **Start your project**.
2. Choisis **Continue with GitHub** (plus simple : pas de nouveau mot de passe).
   Autorise Supabase à lire ton compte GitHub.
3. Sur le tableau de bord, clique **New project**.
   - *Organization* : laisse celle proposée (créée automatiquement).
   - *Project name* : `immoj`
   - *Database password* : clique **Generate a password**, puis **copie-le
     dans ton fichier de notes** (tu n'en auras normalement jamais besoin,
     mais il est impossible à récupérer plus tard).
   - *Region* : **West EU (Ireland)** ou **Central EU (Frankfurt)**, le plus
     proche de la Belgique.
   - *Pricing plan* : **Free**.
   - Clique **Create new project**. Attends 1 à 2 minutes que le projet
     soit « Active ».
4. Récupère les trois valeurs :
   - Icône **engrenage** (Project Settings) en bas de la colonne de gauche.
   - **Data API** : copie **Project URL** → c'est `SUPABASE_URL`.
   - **API Keys** : il y a deux onglets selon la version de Supabase.
     - Onglet *Legacy anon, service_role keys* (ou directement la page) :
       - **anon public** → c'est la clé `anon` (pour l'appli Android).
       - **service_role** (cliquer **Reveal**) → c'est `SUPABASE_SERVICE_KEY`.
         Cette clé donne tous les droits : ne la mets **que** dans les secrets
         GitHub, jamais dans l'appli ni dans le code.
     - Si tu vois plutôt *Publishable key* et *Secret keys* : la publishable
       (`sb_publishable_…`) joue le rôle de `anon`, et une secret key
       (`sb_secret_…`, bouton **Create new**) joue le rôle de `service_role`.
       Les deux formats fonctionnent avec ImmoJ.
5. Passe au guide **02-configurer-supabase.md** pour créer les tables.

> Bon à savoir : un projet gratuit est mis en pause après 7 jours sans
> activité. Le collecteur horaire l'empêche. Si tu arrêtes le collecteur
> longtemps, il suffira de cliquer « Restore project » dans Supabase.

---

## C. ntfy (notifications sur le téléphone)

ntfy est un service de notifications gratuit : le collecteur « publie » sur un
*topic* (un nom), et ton téléphone, abonné à ce topic, reçoit la notification.
Pas de compte nécessaire.

1. **Invente ton topic.** C'est le seul secret : quiconque le connaît peut
   lire tes notifications. Prends quelque chose de long et imprévisible,
   par exemple `immoj-` suivi de 10 lettres et chiffres au hasard :
   `immoj-k7x2p9qv4m`. Note-le → c'est `NTFY_TOPIC`.
2. Sur ton téléphone Android, installe **ntfy** depuis le Play Store
   (éditeur « ntfy / Philipp Heckel », icône verte).
3. Ouvre ntfy, appuie sur **+** (« S'abonner à un sujet »), tape exactement
   ton topic, valide. Laisse le serveur par défaut `ntfy.sh`.
4. Dans les réglages Android de l'appli ntfy, autorise les notifications et,
   si proposé, désactive l'optimisation de batterie pour ntfy (sinon les
   notifications peuvent arriver en retard).
5. Test : ouvre https://ntfy.sh/TON-TOPIC dans un navigateur (remplace
   TON-TOPIC), écris un message dans le champ en bas et envoie. Il doit
   apparaître sur le téléphone dans les secondes qui suivent.

Tu as maintenant les quatre valeurs du tableau. Étape suivante :
**02-configurer-supabase.md**.
