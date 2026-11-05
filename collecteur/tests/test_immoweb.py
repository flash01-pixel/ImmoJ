"""Tests du parser Immoweb sur des réponses JSON réelles enregistrées."""

import json

import pytest

from immoj.sources.immoweb import Immoweb, analyser_json


@pytest.fixture
def lire_json(lire_fixture):
    return lambda chemin: json.loads(lire_fixture(chemin))


def test_location_30_annonces(lire_json):
    annonces = analyser_json(lire_json("immoweb/api_location.json"), "location")
    assert 25 <= len(annonces) <= 30   # 30 résultats moins d'éventuels projets groupés
    assert len({a.id_externe for a in annonces}) == len(annonces)
    for a in annonces:
        assert a.source == "immoweb" and a.transaction == "location"
        assert a.url.startswith("https://www.immoweb.be/fr/annonce/")
        assert a.url.endswith(a.id_externe)
        assert a.code_postal and a.code_postal.startswith("6")
        assert a.commune
        assert a.type in ("appartement", "maison", "studio", "kot", "autre")


def test_details_dune_annonce(lire_json):
    annonces = analyser_json(lire_json("immoweb/api_location.json"), "location")
    a = next(x for x in annonces if x.id_externe == "21933055")
    assert a.type == "maison"
    assert a.titre == "Maison 3 chambres avec jardin et terrasse"
    assert a.prix == 1750
    assert a.charges is None            # monthlyRentalCosts = 0 → inconnu
    assert a.commune == "Arlon" and a.code_postal == "6706"
    assert a.latitude and 49.6 < a.latitude < 49.7
    assert a.chambres == 3 and a.surface == 108
    assert a.peb == "A+"
    assert a.agence == "Immo 23"
    assert len(a.photos) >= 2 and a.photos[0].startswith("https://media-resize.immowebstatic.be/")
    assert a.url == "https://www.immoweb.be/fr/annonce/house/a-louer/arlon/6706/21933055"


def test_achat(lire_json):
    annonces = analyser_json(lire_json("immoweb/api_achat.json"), "achat")
    # salesPitch est parfois un objet : la description doit rester une chaîne.
    assert all(isinstance(a.description, str) for a in annonces)
    assert any("Fanzel" in a.description for a in annonces)
    assert len(annonces) >= 20
    assert all(a.transaction == "achat" for a in annonces)
    assert sum(1 for a in annonces if a.prix and a.prix > 50_000) >= 15


def test_frequence_reduite():
    assert Immoweb.frequence_heures == 3
    assert Immoweb.doit_tourner(9) and not Immoweb.doit_tourner(10)


def test_url_api():
    src = Immoweb.__new__(Immoweb)
    assert src.url_api("location", 2) == (
        "https://www.immoweb.be/fr/search-results/maison-et-appartement/a-louer/"
        "luxembourg/province?countries=BE&page=2&orderBy=newest")


def test_json_vide():
    assert analyser_json({}, "location") == []


def test_kots_de_la_province(lire_json):
    """Lot 5 : la recherche « kot » (capture du 22/10) donne 9 kots, dont 6 de particuliers."""
    annonces = analyser_json(lire_json("lot5/immoweb_kots_luxembourg.json"), "location")
    assert len(annonces) == 9
    assert {a.type for a in annonces} == {"kot"}
    assert sum(a.annonceur == "particulier" for a in annonces) == 6
    assert all(a.agence is None for a in annonces if a.annonceur == "particulier")
    src = Immoweb.__new__(Immoweb)
    url = src.url_api("location", 1, "kot/a-louer/luxembourg/province")
    assert "/kot/a-louer/luxembourg/province?" in url
