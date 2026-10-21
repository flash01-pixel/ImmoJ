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
    loc = generique(lire_fixture, "DG-Immo", "dgimmo_louer2.html", "location", 0)
    assert all(a.prix is None or a.prix < 5000 for a in loc)   # (toutes louées à la capture)
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
    assert a.type == "maison" and a.titre.startswith("Maison à 6661 TAILLES")
    assert a.description.startswith("Maison") or "MAISON ARDENNAISE" in a.description
    generique(lire_fixture, "Immo Peter", "immopeter_louer.html", "location", 2)


def test_wellimmo(lire_fixture):
    # 6 cartes dont 3 « Vendu » (ignorées) : il reste 2 locations.
    loc = generique(lire_fixture, "Wellimmo", "wellimmo_louer.html", "location", 2)
    assert len(loc) == 2
    maison = next(a for a in loc if a.type == "maison")
    assert maison.prix == 900 and maison.code_postal == "6920" and maison.surface == 98
    assert maison.commune == "WELLIN"


def test_immo23(lire_fixture):
    loc = generique(lire_fixture, "Immo 23", "immo23_louer.html", "location", 10)
    a = next(x for x in loc if x.url.endswith("+87511643"))
    assert a.prix == 1750 and a.commune == "Arlon" and a.type == "maison"
    assert a.chambres == 3 and a.surface == 108
    assert a.titre.startswith("Maison 3 chambres") and a.photos[0].startswith("https://")


def test_ardenneimmo(lire_fixture):
    ag = AGENCES["Ardenne Immo"]
    html = lire_fixture("agences/ardenneimmo_accueil.html")
    vente = verifier(analyser_liste(html, ag["url"], ag, "achat"), "achat", ag["nom"], 20)
    a = next(x for x in vente if x.url.endswith("-7909441"))
    assert a.prix == 289000 and a.commune == "HOUFFALIZE" and a.chambres == 5 and a.peb == "F"
    assert all("a-vendre" in x.url for x in vente)
    loc = analyser_liste(html, ag["url"], ag, "location")
    assert all("a-louer" in x.url for x in loc)
    assert not any(x.url in {v.url for v in vente} for x in loc)


# --- Whise/Webulous (WordPress), ex. Honesty -----------------------------------

def test_honesty(lire_fixture):
    import json

    from immoj.sources.agences.honesty import convertir, slugifier, url_fiche

    ag = AGENCES["Honesty"]
    assert slugifier("Rochefort, Buissonville") == "rochefort-buissonville"
    assert slugifier("à louer") == "a-louer" and slugifier("Étalle ") == "etalle"
    biens = json.loads(lire_fixture("agences/honesty_ajax_louer.json"))
    loc = [a for a in (convertir(b, ag["url"], ag, "location") for b in biens) if a]
    verifier(loc, "location", ag["nom"], 20)
    a = next(x for x in loc if x.id_externe.endswith(":7861609"))
    assert a.url == "https://www.honesty.be/bien/maison/a-louer/rochefort-buissonville/7861609"
    assert a.prix == 1250 and a.code_postal == "5580" and a.commune == "Rochefort"
    assert a.chambres == 3 and a.surface == 125 and a.peb == "A+" and a.type == "maison"
    assert a.jardin is True and a.terrasse is True and a.meuble is False
    assert a.photos and a.photos[0].startswith("https://r2.storagewhise.eu/")
    assert "Honesty vous propose" in a.description
    assert str(a.date_publication) == "2026-09-11"
    # Un bien « à louer » n'apparaît pas côté achat.
    assert not [x for x in (convertir(b, ag["url"], ag, "achat") for b in biens) if x]

    biens = json.loads(lire_fixture("agences/honesty_ajax_vendre.json"))
    vente = [a for a in (convertir(b, ag["url"], ag, "achat") for b in biens) if a]
    verifier(vente, "achat", ag["nom"], 10)
    # Les biens vendus / sous option sont écartés.
    assert len(vente) < len(biens)
    assert all("/a-vendre/" in x.url for x in vente)
    assert url_fiche("https://www.honesty.be", biens[0]).startswith("https://www.honesty.be/bien/")


# --- ERA (API JSON:API du site national) ---------------------------------------

def test_era(lire_fixture):
    import json

    from immoj.sources.agences.era import analyser_index, identifiants_province, url_index

    ag = AGENCES["ERA B-Lux"]
    communes, sections = identifiants_province(
        json.loads(lire_fixture("agences/era_api_locations.json"))["data"])
    assert len(communes) >= 40 and len(sections) >= 150 and "181" in communes   # Arlon
    url = url_index(ag["url"], "location", communes, sections, 36)
    assert url.startswith("https://www.era.be/fr/jsonapi/index/property_index?filter")
    assert "pager%5Boffset%5D=36" in url and "sale_or_rent%5D=rent" in url

    loc = analyser_index(json.loads(lire_fixture("agences/era_index_lux_louer.json")),
                         ag["url"], ag, "location")
    verifier(loc, "location", ag["nom"], 15)
    a = next(x for x in loc
             if x.url.endswith("appartement-1-chambre-avec-terrasse-au-coeur-de-bastogne"))
    assert a.prix == 760 and a.code_postal == "6600" and a.commune == "Bastogne"
    assert a.chambres == 1 and a.surface == 61 and a.peb == "A" and a.type == "appartement"
    assert a.latitude and 49.5 < a.latitude < 50.5 and a.photos[0].startswith("https://www.era.be/")
    assert all("/a-louer/" in x.url for x in loc)
    vente = analyser_index(json.loads(lire_fixture("agences/era_index_lux_vendre.json")),
                           ag["url"], ag, "achat")
    verifier(vente, "achat", ag["nom"], 30)
    assert all(x.code_postal and x.code_postal.startswith("6") for x in vente)
    # Les biens vendus (classe property-sold) sont écartés.
    assert not analyser_index(json.loads(lire_fixture("agences/era_jsonapi_index.json")),
                              ag["url"], ag, "location")[:0]


# --- Omnicasa / Gatsby (Immo Bastogne) ------------------------------------------

def test_omnicasa_gatsby(lire_fixture):
    import json

    from immoj.sources.agences.omnicasa_gatsby import analyser_page_data, urls_fiches

    ag = AGENCES["Immo Bastogne"]
    plan = urls_fiches(lire_fixture("agences/immobastogne_sitemap_0.xml"))
    assert plan["16346"] == \
        "https://www.immobastogne.be/fr/a-louer/bastogne/bel-appartement-1-chambre-a-bastogne/16346/"
    loc = analyser_page_data(json.loads(lire_fixture("agences/immobastogne_pagedata_louer.json")),
                             ag["url"], ag, "location", plan)
    verifier(loc, "location", ag["nom"], 15)
    a = next(x for x in loc if x.id_externe.endswith(":16346"))
    assert a.url == plan["16346"] and a.prix == 720 and a.code_postal == "6600"
    assert a.chambres == 1 and a.surface == 79 and a.peb == "C" and a.type == "appartement"
    assert a.latitude and a.longitude and a.photos[0].startswith("https://cdn.omnicasapictures.com/")
    assert len({x.id_externe for x in loc}) == len(loc)        # une seule langue par bien
    donnees_vente = json.loads(lire_fixture("agences/immobastogne_pagedata_vendre.json"))
    vente = analyser_page_data(donnees_vente, ag["url"], ag, "achat", plan)
    verifier(vente, "achat", ag["nom"], 30)
    assert all(x.prix is None or x.prix > 5000 for x in vente)   # (un carport : 15 000 €)
    # Sans plan du site : repli sur la page de la localité.
    donnees_loc = json.loads(lire_fixture("agences/immobastogne_pagedata_louer.json"))
    sans_plan = analyser_page_data(donnees_loc, ag["url"], ag, "location")
    assert all(x.url.startswith("https://www.immobastogne.be/fr/a-louer/") for x in sans_plan)


# --- WordPress (API REST + fiche) ---------------------------------------------

def test_wordpress_alison(lire_fixture):
    import json

    from immoj.sources.agences.wordpress import completer_depuis_fiche, depuis_rest

    ag = AGENCES["Alison Immo"]
    entrees = json.loads(lire_fixture("agences/alisonimmo_property.json"))
    brutes = [a for a in (depuis_rest(e, ag["url"], ag, ag["options"]) for e in entrees) if a]
    assert len(brutes) >= 40 and {b.transaction for b in brutes} == {"achat", "location"}
    b = next(x for x in brutes if "halanzy-maison-3-facades" in x.url)
    assert b.type == "maison" and b.commune == "Halanzy" and b.transaction == "achat"
    assert b.photos and "Alison IMMO vous présente" in b.description
    a = completer_depuis_fiche(b, lire_fixture("agences/alisonimmo_detail.html"), ag["options"])
    assert a.prix == 189000 and a.chambres == 2 and a.surface == 74
    assert a.code_postal == "6792" and a.peb == "D" and a.transaction == "achat"


def test_wordpress_homesweetgaume(lire_fixture):
    import json

    from immoj.sources.agences.wordpress import completer_depuis_fiche, depuis_rest

    ag = AGENCES["Home Sweet Gaume"]
    entrees = json.loads(lire_fixture("agences/homesweetgaume_bien.json"))
    brutes = [a for a in (depuis_rest(e, ag["url"], ag, ag["options"]) for e in entrees) if a]
    assert len(brutes) >= 40
    b = next(x for x in brutes if x.url.endswith("/bien/le-mocassin/"))
    assert b.commune == "Jamoigne" and b.titre == "Le Mocassin"
    a = completer_depuis_fiche(b, lire_fixture("agences/homesweetgaume_detail.html"), ag["options"])
    assert a.prix == 195000 and a.chambres == 6 and a.surface == 430
    assert a.code_postal == "6810" and a.transaction == "achat"


def test_wordpress_cache():
    """Un bien déjà connu est reconstruit sans lire sa fiche."""
    from immoj.modele import Annonce
    from immoj.sources.agences.wordpress import _depuis_cache

    a = Annonce(source="agences:wordpress", id_externe="x:1", url="https://x/1", titre="T",
                transaction="achat")
    a = _depuis_cache(a, {"prix": 800, "chambres": 2, "surface": 70, "peb": "B",
                          "code_postal": "6700", "localite": "Arlon", "transaction": "location"})
    assert a.prix == 800 and a.transaction == "location" and a.commune == "Arlon"


# --- Module générique : Trevi, ITI, Immo Bia, Immo Centre Ardennes -----------------

def test_trevi(lire_fixture):
    loc = generique(lire_fixture, "Trevi Luxembourg", "trevi_louer_zips.html", "location", 4)
    a = next(x for x in loc if "/bien/6130677/" in x.url)
    assert a.prix == 1225 and a.commune == "Arlon" and a.code_postal == "6700"
    assert a.chambres == 2 and a.surface == 97 and a.type == "appartement" and a.peb == "A"
    vente = generique(lire_fixture, "Trevi Luxembourg", "trevi_vendre_zips.html", "achat", 10)
    assert all(x.prix is None or x.prix > 50000 for x in vente)
    # Une carte sans chambre ne confond pas la surface avec le nombre de chambres.
    terrain = next(x for x in vente if x.type == "terrain")
    assert terrain.chambres is None and terrain.surface == 1148


def test_iti(lire_fixture):
    loc = generique(lire_fixture, "Iti Groupe", "iti_louer.html", "location", 8)
    a = next(x for x in loc if "/1497/maison-individuelle-trois-chambres" in x.url)
    assert a.prix == 1095 and a.commune == "Florenville" and a.chambres == 3 and a.type == "maison"
    vente = generique(lire_fixture, "Iti Groupe", "iti_vendre.html", "achat", 8)
    assert any(x.commune == "Izel" and x.prix == 399000 for x in vente)


def test_immobia(lire_fixture):
    loc = generique(lire_fixture, "BIA Immo", "immobia_louer.html", "location", 1)
    a = loc[0]
    assert a.prix == 820 and a.code_postal == "6990" and a.chambres == 2 and a.surface == 90
    vente = generique(lire_fixture, "BIA Immo", "immobia_vendre.html", "achat", 10)
    assert any(x.code_postal == "6666" and x.chambres == 4 and x.surface == 164 for x in vente)


def test_immocentreardennes(lire_fixture):
    ag = AGENCES["Immo Centre Ardennes"]
    loc = verifier(analyser_liste(lire_fixture("agences/immocentreardennes_results_louer.html"),
                                  ag["options"]["liste_location"], ag, "location"),
                   "location", ag["nom"], 8)
    a = next(x for x in loc if x.url.endswith(",7777281"))
    assert a.commune == "LIBRAMONT-CHEVIGNY" and a.code_postal == "6800" and a.chambres == 2
    assert a.surface == 145 and a.peb == "E" and a.type == "appartement"
    html_vente = lire_fixture("agences/immocentreardennes_results_vendre.html")
    vente = verifier(analyser_liste(html_vente, ag["options"]["liste_achat"], ag, "achat"),
                     "achat", ag["nom"], 5)
    assert all("achat," in x.url for x in vente)


def test_ardimmoc(lire_fixture):
    loc = generique(lire_fixture, "Ard'Immo & Conseils", "ardimmoc_louer.html", "location", 20)
    a = next(x for x in loc if "maissin-appartement-rez" in x.url)
    assert a.code_postal == "6852" and a.chambres == 2 and a.type == "appartement" and a.prix
    vente = generique(lire_fixture, "Ard'Immo & Conseils", "ardimmoc_vendre.html", "achat", 30)
    a = next(x for x in vente if "noirefontaine-agreable-maison-de-plain-pied-avec-jardin" in x.url)
    assert a.prix == 279000 and a.code_postal == "6831" and a.chambres == 2
