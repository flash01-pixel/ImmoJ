# 3. Ajouter les secrets dans GitHub

Les secrets sont des valeurs chiffrées que GitHub Actions passe au collecteur.
Ils n'apparaissent jamais dans le code ni dans les logs.

1. Ouvre https://github.com/flash01-pixel/ImmoJ/settings/secrets/actions
   (ou : dépôt → **Settings** → colonne de gauche **Secrets and variables** →
   **Actions**).
2. Clique **New repository secret**.
3. Dans *Name*, tape exactement le nom ci-dessous ; dans *Secret*, colle la
   valeur ; clique **Add secret**. Répète pour chaque ligne.

| Name | Valeur | Obligatoire ? |
|---|---|---|
| `SUPABASE_URL` | l'URL du projet, ex. `https://abcdefgh.supabase.co` | oui |
| `SUPABASE_SERVICE_KEY` | la clé `service_role` (ou une secret key `sb_secret_…`) | oui |
| `NTFY_TOPIC` | ton topic ntfy, ex. `immoj-k7x2p9qv4m` | oui pour les notifications |
| `GMAIL_UTILISATEUR` | adresse Gmail dédiée aux alertes Immoweb | plus tard (phase 2) |
| `GMAIL_MOT_DE_PASSE` | mot de passe d'application Gmail | plus tard (phase 2) |

Attention aux espaces : si tu copies un espace avant ou après la valeur, la
connexion échouera.

## Premier lancement du collecteur (test)

1. Onglet **Actions** du dépôt.
2. Colonne de gauche : **Collecte des annonces**.
3. Bouton **Run workflow** (à droite) → laisse les champs vides → **Run
   workflow**. Si le workflow n'apparaît pas, c'est qu'il n'est pas encore sur
   la branche `main` (voir le README, section « Mise en service »).
4. Après ~2 minutes, clique sur l'exécution pour voir le journal. Tu dois lire
   des lignes comme `[immovlan] location : 60 annonces` puis
   `[immovlan] OK : … nouvelles`. Un premier import envoie **une seule**
   notification ntfy (« Immovlan connecté ») au lieu d'une par annonce.
5. Dans Supabase → Table Editor → `annonces` : les lignes sont là.

Ensuite, le collecteur tourne tout seul toutes les heures entre 7h et 23h.
Étape suivante : **04-installer-apk.md**.
