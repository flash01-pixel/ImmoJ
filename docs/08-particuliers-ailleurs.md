# 8. Les annonces de particuliers, d'où qu'elles viennent (lot 5)

Évaluation faite le 22/10/2026 depuis les serveurs GitHub (workflow `capture.yml`,
pages enregistrées dans `collecteur/tests/fixtures/html/lot5/`). Règle du jeu : pas de
connexion, pas de contournement de protection, robots.txt et délais respectés.

## Tableau de décision

| Source | Accessible depuis GitHub ? | robots.txt | Annonces dans la province | dont particuliers | Absentes d'ImmoJ | Décision |
|---|---|---|---|---|---|---|
| **Immoweb — rubrique « kot »** | oui (API JSON) | OK | 9 kots | 6 | 9 (la recherche « maison et appartement » les ignorait) | **Intégrée** : le collecteur lit aussi `kot/a-louer/luxembourg/province` |
| PAP.fr (rubrique Belgique) | **non** : page de défi Cloudflare (HTTP 403, « Just a moment… ») | interdit à des dizaines de robots | inconnu (PAP est français, quelques biens belges) | — | — | Non |
| Trovit | **non** : `maison.trovit.be` et `immobilier.trovit.be` ne répondent pas (DNS) ; Trovit est de toute façon un agrégateur d'Immoweb/Immovlan/Zimmo, sans annonces propres | — | — | — | 0 (reprises d'ailleurs) | Non |
| Rentola | **non** : toutes les pages (même l'accueil et le sitemap) renvoient une page d'erreur Next.js (HTTP 404) hors navigateur ; agrégateur, pas d'annonces propres | OK (seules `/ads-map/`, `/api/events` interdites) | — | — | 0 (reprises d'ailleurs) | Non |
| athome.lu | oui (HTML + JSON intégré, 12 373 locations… au Grand-Duché) | OK (`/srp/` autorisé, `Crawl-delay: 1`) | la recherche « Belgique » ne se laisse pas cibler par l'URL (toute tentative retombe sur le Luxembourg : 1 bien belge sur 20 par page, 619 pages) | portail d'agences, très peu de particuliers | quasi nulles : les agences frontalières (Arlon, Aubange, Messancy) publient aussi sur Immoweb/Immovlan | Non (réévaluer si un identifiant de lieu belge est trouvé, voir capture `athome_rest_*.json`) |
| immotop.lu | **non** : captcha DataDome (HTTP 403) | — | — | — | — | Non |
| Vivastreet | **non** : défi Cloudflare (HTTP 403) ; robots.txt interdit `/search/*` et les pages « particulier » | interdit | — | — | — | Non |
| Kots : kotplanet.be | **non** : le domaine redirige vers sudinfo.be (« Access Denied ») | — | — | — | — | Non (site disparu) |
| Kots : kot.be | **non** : HTTP 404 | — | — | — | — | Non |
| Kots : henallux.be/kots | HTTP 404 (page déplacée ; les kots Hénallux sont réservés aux étudiants inscrits) | — | — | — | — | Non |
| Zimmo, Logic-immo (direct) | **non** : défi Cloudflare, y compris `robots.txt` | illisible | — | — | — | Non en direct |
| Zimmo, Logic-immo (alertes e-mail) | oui (le lecteur `alertes_mail` existe) | sans objet | non mesurable depuis GitHub (sites bloqués) | Zimmo a un filtre « particulier », Logic-immo est surtout alimenté par les agences | inconnu | **Pas d'installation imposée** : gain non démontré. Le guide pas à pas (docs/07) existe si tu veux essayer ; coût 0 €, 10 minutes |
| Facebook Marketplace et groupes | exclu (connexion obligatoire, risque de bannissement) | — | — | — | — | **Partager vers ImmoJ** à la place |

## Partager vers ImmoJ (appli)

- Depuis Facebook, Messenger, un navigateur, un SMS… : **Partager** → **ImmoJ**. L'écran
  « Ajouter une annonce » s'ouvre, pré-rempli avec le lien, le prix s'il figure dans le texte et
  le titre. Choisis location/achat, le type, la commune (obligatoire pour la carte), puis
  **Enregistrer dans ImmoJ**. Une image partagée (capture d'écran) devient la photo de la fiche.
- Bouton **+** en bas de la liste (et menu ⋮ → « + Ajouter une annonce ») pour saisir une annonce
  entendue de bouche-à-oreille.
- Ces fiches (source « Ajoutée à la main ») vivent sur le téléphone, dans la même liste que les
  autres : statuts, favoris, carte (centre de la commune), compteurs, filtres (annonceur
  « Particulier » par défaut). Elles ne sont pas envoyées à Supabase ni partagées.

## Comment relancer une évaluation

Ajouter des lignes dans `collecteur/outils/pages_a_capturer.txt` et pousser sur une branche de
travail : le workflow « Capturer des pages HTML » enregistre les réponses (code HTTP, page) dans
`collecteur/tests/fixtures/html/`. Une page « Just a moment… » = Cloudflare ; « Please enable
JS » = DataDome ; un JSON = exploitable.
