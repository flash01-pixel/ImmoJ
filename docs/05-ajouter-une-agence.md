# 5. Ajouter ou corriger une agence

Les agences sont décrites dans `collecteur/agences.yaml`. Le collecteur crée
une source par **logiciel** (`agences:whise`, `agences:generique`…) qui
parcourt toutes les agences actives de ce logiciel. Aucun code à modifier pour
ajouter une agence : seulement ce fichier.

## Les trois cas

**1. Site sur le modèle Whise** (adresses du type `/fr/7/chercher-bien/a-louer`,
images sur `storagewhise.eu`) :

```yaml
  - nom: Immo Exemple
    commune: Arlon
    url: https://www.immo-exemple.be
    logiciel: whise
    actif: true
    options: {mode: scroll}     # ou {mode: html} si la page de liste contient déjà les annonces
```

**2. Autre site avec une page de liste** (WordPress, Omnicasa, site maison) :
module `generique`, avec les sélecteurs CSS de la page. Ouvre la page des
biens à louer dans ton navigateur, clic droit → « Inspecter », repère
l'élément qui entoure chaque annonce et les sous-éléments utiles :

```yaml
  - nom: Immo Exemple
    commune: Arlon
    url: https://www.immo-exemple.be
    logiciel: generique
    actif: true
    options:
      liste_location: https://www.immo-exemple.be/a-louer
      liste_achat: https://www.immo-exemple.be/a-vendre
      carte: "article.bien"      # un élément par annonce
      lien: "a"                  # lien vers la fiche ("." si la carte est elle-même un lien)
      titre: "h3"                # tout le reste est optionnel
      prix: ".prix"
      lieu: ".adresse"           # texte contenant « 6700 Arlon »
      chambres: ".chambres"
      surface: ".surface"
      photo: "img"
```

Les exemples déjà configurés (Pepit-immo, Mobilimmo, BD Immo, DG-Immo, Immo
Peter, Wellimmo) servent de modèles.

**3. Pas de site, ou site sans liste exploitable** : `actif: false` avec une
`note`. Les portails (Immovlan, Immoweb…) couvrent l'agence.

## Vérifier avant d'activer

1. Pousse `agences.yaml` sur une branche de travail : le workflow
   **Analyser les agences** teste l'URL et indique le logiciel détecté.
2. Enregistre la page de liste dans `collecteur/tests/fixtures/html/agences/`
   (workflow **Capturer des pages HTML**) et ajoute un test dans
   `collecteur/tests/test_agences.py` sur le modèle des autres.
3. Lance les tests (`pytest`) : si le parser renvoie des annonces avec prix et
   lieu, active l'agence.

## Couverture (22 octobre 2026)

**Collectées (23)** : Groupe Arcade, Picard Immo, Immo Plainchamp, Neufcoeur,
Juliet Immo, Homeside (Whise) · Honesty (Whise/Webulous, ajax) · ERA B-Lux
(API du site national, toutes les agences ERA de la province) · Immo Bastogne,
Benjamin Gaspard (Omnicasa/Gatsby) · Alison Immo, Home Sweet Gaume (WordPress +
fiche) · BD Immo, Immo Peter, Pepit-immo, Mobilimmo, Wellimmo, DG-Immo
(= Guibert Grandjean), Immo 23, Ardenne Immo, Trevi Luxembourg, Immo Centre
Ardennes, ITI Groupe, Immo Bia, Ard'Immo & Conseils (module générique) · MCA
Immobilier (API JSON).

**Site trouvé mais inexploitable** : Vos Agences et Century 21 (liste
JavaScript), L'Agence (le site ne contient que des biens de démonstration
d'Anvers), Delzandre et Easyhome (API sans prix, fiches à analyser), Lio Immo
(pas de type de bien), Isimmo (défi Cloudflare), Euro Ardennes (page vide),
W Immobilière et Tadam (applications JavaScript sans données côté serveur),
Logesud (Wix). Toutes publient aussi sur Immoweb/Immovlan.

**Site injoignable ou inexistant** : Access Immo, Ultimmo, Immo Dek, Sud Immo,
Centrale Immobilière, Dynasty, Immobilière des Ardennes, AIS Nord Luxembourg,
Immo CM, Brion Immo, Berns (site vitrine sans biens). Si tu connais leur site,
ajoute l'URL dans le YAML et pousse : le rapport d'analyse dira quel module
utiliser.
