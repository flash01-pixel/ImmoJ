"""Lecture générique des fiches (photos + détails) et fiche Immoweb."""

import json

from immoj.fiche import enrichir_depuis_html, fusionner, photos_de_la_page
from immoj.modele import Annonce
from immoj.sources.base import Source
from immoj.sources.immoweb import completer_depuis_classified, extraire_classified


def _annonce(url, id_externe="x:1"):
    return Annonce(source="test", id_externe=id_externe, url=url, titre="T", transaction="achat",
                   photos=["https://www.alisonimmo.be/wp-content/uploads/2026/10/E3639271621743100917_855AL.jpg"])


def test_fiche_alison(lire_fixture):
    a = _annonce("https://www.alisonimmo.be/propriete/halanzy-maison-3-facades-2-chambres-jardin/")
    enrichir_depuis_html(a, lire_fixture("agences/alisonimmo_detail.html"))
    assert len(a.photos) >= 5 and len(set(a.photos)) == len(a.photos)
    assert all(p.startswith("https://") for p in a.photos)
    assert a.details.get("Salles de bain") == 1 and a.details.get("Année de construction") == 1931
    assert a.details.get("Toilettes") == 1 and "Revenu cadastral" in a.details
    assert "Alison IMMO vous présente" in a.description


def test_fiche_homesweetgaume(lire_fixture):
    a = _annonce("https://homesweetgaume.be/bien/le-mocassin/")
    enrichir_depuis_html(a, lire_fixture("agences/homesweetgaume_detail.html"))
    assert len(a.photos) >= 5
    assert not any("logo" in p.lower() or p.lower().endswith(".svg") for p in a.photos)


def test_fusion_photos_tailles():
    """La même photo en 640 et 1920 px ne compte qu'une fois."""
    p1 = "https://r2.storagewhise.eu/honesty/Pictures/7861609/640/1e01499b.jpg"
    p2 = "https://r2.storagewhise.eu/honesty/Pictures/7861609/1920/1e01499b.jpg"
    assert fusionner([p1], [p2, "https://x/autre-300x200.jpg", "https://x/autre.jpg"]) == \
        [p1, "https://x/autre-300x200.jpg"]


def test_photos_exclusions():
    from bs4 import BeautifulSoup
    html = """<html><body>
      <img src="/img/logo.png"><img src="/wp-content/themes/x/picto-peb.png">
      <img data-src="/uploads/2026/bien-12345-1.jpg"><img src="https://cdn.autre.be/photos/abc.jpg">
      <a href="/uploads/2026/bien-12345-2.jpeg">photo</a><img src="data:image/gif;base64,xx">
    </body></html>"""
    photos = photos_de_la_page(BeautifulSoup(html, "lxml"), "https://www.agence.be/bien/12345",
                               _annonce("https://www.agence.be/bien/12345", "agence.be:12345"))
    assert photos == ["https://www.agence.be/uploads/2026/bien-12345-1.jpg",
                      "https://cdn.autre.be/photos/abc.jpg",
                      "https://www.agence.be/uploads/2026/bien-12345-2.jpeg"]


def test_immoweb_classified():
    donnees = {"media": {"pictures": [{"largeUrl": "https://static.immoweb.be/p/1.jpg"},
                                      {"largeUrl": "https://static.immoweb.be/p/2.jpg"}]},
               "property": {"bathroomCount": 2, "landSurface": 631, "hasGarage": True,
                            "building": {"condition": "GOOD", "constructionYear": 1998},
                            "kitchen": {"type": "HYPER_EQUIPPED"},
                            "description": "<p>Belle maison</p>"},
               "transaction": {"rental": {"isPetsAllowed": False}}}
    html = "<script>window.classified = " + json.dumps(donnees) + ";\n</script>"
    assert extraire_classified(html) == donnees
    a = _annonce("https://www.immoweb.be/fr/annonce/maison/a-vendre/arlon/6700/1")
    a.photos = ["https://static.immoweb.be/p/1.jpg"]
    completer_depuis_classified(a, donnees)
    assert a.photos == ["https://static.immoweb.be/p/1.jpg", "https://static.immoweb.be/p/2.jpg"]
    assert a.details["Salles de bain"] == 2 and a.details["Terrain"] == "631 m²"
    assert a.details["État"] == "bon" and a.details["Année de construction"] == 1998
    assert a.details["Cuisine"] == "hyper équipée" and a.details["Animaux"] is False
    assert a.details["Garage"] is True and "Belle maison" in a.description


class _Fausse(Source):
    nom = "fausse"
    fiches_max = 2

    def __init__(self):
        super().__init__()
        self.lues = []

    def collecter(self, transaction):
        return []

    def enrichir(self, a):
        self.lues.append(a.id_externe)
        a.photos = a.photos + ["https://x/nouvelle.jpg"]
        a.details["Garage"] = True


def test_fiches_nouvelles_seulement_et_plafond():
    src = _Fausse()
    src.connues = {"f:1": {"photos": ["https://x/a.jpg", "https://x/b.jpg"],
                           "details": {"Cave": True}}}
    annonces = [Annonce(source="fausse", id_externe=f"f:{i}", url=f"https://x/{i}", titre="T",
                        transaction="achat") for i in (1, 2, 3, 4)]
    src._enrichir_nouvelles(annonces)
    assert src.lues == ["f:2", "f:3"]                     # 1 connue, 4 au-delà du plafond
    assert annonces[0].photos == ["https://x/a.jpg", "https://x/b.jpg"]
    assert annonces[0].details == {"Cave": True}
    assert annonces[1].details == {"Garage": True} and annonces[3].details == {}


def test_budget_de_temps_global():

    src = _Fausse()
    Source.budget_fiches_s, Source.temps_fiches_s = 10.0, 10.0      # budget déjà épuisé
    try:
        annonces = [Annonce(source="fausse", id_externe="f:9", url="https://x/9", titre="T",
                            transaction="achat")]
        src._enrichir_nouvelles(annonces)
        assert src.lues == [] and annonces[0].details == {}
        Source.temps_fiches_s = 0.0
        src._enrichir_nouvelles(annonces)
        assert src.lues == ["f:9"] and Source.temps_fiches_s >= 0.0
    finally:
        Source.budget_fiches_s, Source.temps_fiches_s = None, 0.0
