# 7. Alertes e-mail des portails (Immoweb, Zimmo, Logic-immo)

Zimmo et Logic-immo bloquent les serveurs GitHub (Cloudflare), et Immoweb
pourrait le faire un jour. La solution fiable : chaque portail t'envoie ses
nouvelles annonces par e-mail, et le collecteur lit ces e-mails.

## A. Une adresse Gmail dédiée

1. Crée une nouvelle adresse Gmail (ex. `immoj.alertes@gmail.com`) : ne
   réutilise pas ton adresse personnelle, le collecteur marquera les e-mails
   comme lus.
2. Active la **validation en deux étapes** : https://myaccount.google.com/security
   → « Validation en deux étapes » → suivre les étapes (numéro de téléphone).
3. Crée un **mot de passe d'application** : https://myaccount.google.com/apppasswords
   → nom « ImmoJ » → **Créer**. Google affiche 16 lettres (ex. `abcd efgh ijkl mnop`).
   Copie-les **sans les espaces**. Ce mot de passe ne sert qu'au collecteur.

## B. Les secrets GitHub

Dépôt → Settings → Secrets and variables → Actions → New repository secret :

| Name | Valeur |
|---|---|
| `GMAIL_UTILISATEUR` | l'adresse complète, ex. `immoj.alertes@gmail.com` |
| `GMAIL_MOT_DE_PASSE` | les 16 lettres du mot de passe d'application, sans espaces |

Tant que ces secrets sont absents, la source « alertes_mail » est simplement
ignorée (ligne « non configurée » dans les journaux).

## C. Créer les alertes sur chaque portail

Avec la nouvelle adresse Gmail, crée un compte sur chaque site puis une alerte
**« Province de Luxembourg »**, en location (et une seconde en achat si tu veux) :

- **Immoweb** : faire une recherche « À louer » → Province : Luxembourg →
  bouton « Créer une alerte » (cloche) → fréquence **Instantanée** ou
  « Chaque jour ».
- **Zimmo** : recherche → « Enregistrer la recherche » / « Alerte e-mail ».
- **Logic-immo** : recherche → « Créer une alerte ».

Astuce : accepte les e-mails « instantanés » quand c'est proposé, c'est ce qui
donne les notifications les plus rapides.

## D. Vérifier

Après le premier e-mail reçu, lance la collecte à la main (Actions → Collecte
des annonces → Run workflow) : le journal doit afficher
`[alertes_mail] N e-mail(s) non lu(s)` puis le nombre d'annonces trouvées.

Le parser a été écrit sur un e-mail d'exemple reconstitué, pas sur un vrai
e-mail de chaque portail. Si des annonces manquent ou ont des infos erronées,
enregistre un e-mail réel (Gmail → ⋮ → « Télécharger le message », fichier
`.eml`) et dépose-le dans `collecteur/tests/fixtures/mails/` : le parser sera
ajusté dessus.
