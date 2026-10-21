"""Tests des modules « agences » sur les pages réelles enregistrées (fixtures/html/agences)."""

import yaml

from immoj.config import FICHIER_AGENCES
from immoj.sources.agences import charger_sources_agences
from immoj.sources.agences.generique import analyser_liste
from immoj.sources.agences.whise import analyser_html, url_scroll

AGENCES = {a["nom"]: a
           for a in yaml.safe_load(FICHIER_AGENCES.read_text(encoding="utf-8"))["agences"]}


def test_chargement_yaml():
    sources = charger_sources_agences()
    assert "agences:whise" in sources and "agences:generique" in sources
    assert len(sources["agences:whise"].agences) >= 3


def test_url_scroll():
    url = url_scroll("https://www.picardimmo.be", "location")
    assert url.startswith("https://www.picardimmo.be/fr/List/InfiniteScroll?json=%7B")
    assert "PurposeIDList" in url and "%5B2%5D" in url


def verifier(annonces, transaction, nom, minimum=1):
    assert len(annonces) >= minimum, f"{nom} : {len(annonces)} annonce(s)"
    for a in annonces:
        assert a.transaction == transaction
        assert a.url.startswith("http") and a.agence == nom
        assert a.id_externe and ":" in a.id_externe
        assert a.titre
    return annonces


# --- Whise -------------------------------------------------------------------

def test_whise_scroll_picard(lire_fixture):
    ag = AGENCES["Picard Immo"]
    annonces = verifier(analyser_html(lire_fixture("agences/picardimmo_scroll_louer.html"),
                                      ag["url"], ag, "location"), "location", ag["nom"], 10)
    a = annonces[0]
    assert a.code_postal and a.commune and a.prix
    assert any(x.chambres for x in annonces) and any(x.surface for x in annonces)
    assert any(x.peb for x in annonces) and any(x.photos for x in annonces)


def test_whise_scroll_arcade(lire_fixture):
    ag = AGENCES["Groupe Arcade Immobilier"]
    loc = verifier(analyser_html(lire_fixture("agences/groupearcade_scroll_louer.html"),
                                 ag["url"], ag, "location"), "location", ag["nom"], 3)
    assert all(a.prix for a in loc)
    vente = verifier(analyser_html(lire_fixture("agences/groupearcade_scroll_vendre.html"),
                                   ag["url"], ag, "achat"), "achat", ag["nom"], 20)
    assert any(a.prix and a.prix > 100_000 for a in vente)
    # Une page « à vendre » ne doit donner aucune location
    assert analyser_html(lire_fixture("agences/groupearcade_scroll_vendre.html"),
                         ag["url"], ag, "location") == []


def test_whise_scroll_plainchamp(lire_fixture):
    ag = AGENCES["Immo Plainchamp"]
    verifier(analyser_html(lire_fixture("agences/immoplainchamp_scroll_louer.html"),
                           ag["url"], ag, "location"), "location", ag["nom"], 3)


def test_whise_html_neufcoeur(lire_fixture):
    ag = AGENCES["Neufcoeur"]
    annonces = verifier(analyser_html(lire_fixture("agences/neufcoeur_louer.html"),
                                      ag["url"], ag, "location"), "location", ag["nom"], 3)
    entrepot = next(a for a in annonces if a.id_externe.endswith(":7716644"))
    assert entrepot.prix == 3000 and entrepot.surface == 557
    assert entrepot.commune == "Neufchâteau" and entrepot.code_postal == "6840"
    assert entrepot.photos[0].startswith("https://r2.storagewhise.eu/")
    verifier(analyser_html(lire_fixture("agences/neufcoeur_vendre.html"),
                           ag["url"], ag, "achat"), "achat", ag["nom"], 8)


# --- Générique ---------------------------------------------------------------

def generique(lire_fixture, nom, fichier, transaction, minimum):
    ag = AGENCES[nom]
    url = ag["options"][f"liste_{transaction}"]
    return verifier(analyser_liste(lire_fixture(f"agences/{fichier}"), url, ag, transaction),
                    transaction, nom, minimum)


def test_pepit(lire_fixture):
    loc = generique(lire_fixture, "Pepit-immo", "pepitimmo_louer.html", "location", 5)
    a = next(x for x in loc if x.url.endswith("/bien/arlon-74/"))
    assert a.prix == 1000 and a.code_postal == "6700" and a.commune == "ARLON"
    assert a.type == "appartement" and a.chambres == 1 and a.surface == 36 and a.peb == "A"
    generique(lire_fixture, "Pepit-immo", "pepitimmo_vendre.html", "achat", 10)


def test_mobilimmo(lire_fixture):
    loc = generique(lire_fixture, "Mobilimmo", "mobilimmo_louer.html", "location", 5)
    a = next(x for x in loc if "saint-hubert" in x.url)
    assert a.prix == 720 and a.commune == "Saint-Hubert" and a.chambres == 1
    assert a.photos and "wp-content/uploads" in a.photos[0]
    generique(lire_fixture, "Mobilimmo", "mobilimmo_vendre.html", "achat", 20)


def test_bdimmo(lire_fixture):
    loc = generique(lire_fixture, "BD Immo", "bdimmo_louer.html", "location", 8)
    a = next(x for x in loc if x.url.endswith("/4089"))
    assert a.titre.startswith("Appartement 2 chambres") and a.type == "appartement"
    assert a.prix and 300 <= a.prix <= 3000
    assert a.photos and "omnicasapictures" in a.photos[0]
    generique(lire_fixture, "BD Immo", "bdimmo_vendre.html", "achat", 8)


def test_dgimmo(lire_fixture):
    # La même page liste tout : 93 cartes dont 74 « Vendu » ; data-filter donne sale/rent.
    loc = generique(lire_fixture, "DG-Immo", "dgimmo_louer2.html", "location", 5)
    assert all(a.prix is None or a.prix < 5000 for a in loc)
    vente = generique(lire_fixture, "DG-Immo", "dgimmo_louer2.html", "achat", 5)
    a = next(x for x in vente if x.url.endswith("/2868"))
    assert a.prix == 379000 and a.code_postal == "6670" and a.chambres == 4 and a.surface == 190
    assert a.jardin is True and a.terrasse is True and a.peb == "B" and a.type == "maison"
    assert a.commune.startswith("Gouvy")
    assert not any(x.url.endswith("/2868") for x in loc)


def test_immopeter(lire_fixture):
    vente = generique(lire_fixture, "Immo Peter", "immopeter_vendre.html", "achat", 10)
    a = next(x for x in vente if x.url.endswith("id=669"))
    assert a.prix == 369000 and a.commune == "TAILLES" and a.chambres == 4 and a.surface == 163
    assert a.type == "maison" and a.titre.startswith("MAISON ARDENNAISE")
    generique(lire_fixture, "Immo Peter", "immopeter_louer.html", "location", 2)


def test_wellimmo(lire_fixture):
    # 6 cartes dont 3 « Vendu » (ignorées) : il reste 2 locations.
    loc = generique(lire_fixture, "Wellimmo", "wellimmo_louer.html", "location", 2)
    assert len(loc) == 2
    maison = next(a for a in loc if a.type == "maison")
    assert maison.prix == 900 and maison.code_postal == "6920" and maison.surface == 98
    assert maison.commune == "WELLIN"
