# 5. Ajouter une agence (phase 3)

> Cette phase n'est pas encore construite. Ce guide décrit le fonctionnement
> prévu pour que le fichier `agences.yaml` soit utilisable dès sa création.

Les agences sont décrites dans `collecteur/agences.yaml`. Chaque entrée :

```yaml
- nom: Honesty Arlon
  commune: Arlon
  url: https://www.honesty.be/
  logiciel: honesty        # module générique utilisé (voir liste ci-dessous)
  actif: true              # false = ignorée (pas de site, ou publie seulement sur Immoweb)
  options: {}              # paramètres propres au module (ex. identifiant d'agence)
```

Pour ajouter une agence : copier un bloc, adapter nom/commune/url, choisir le
`logiciel`. Aucun code à modifier. Si le logiciel du site n'est pas encore
géré, mettre `actif: false` et le signaler : un module générique sera ajouté.
