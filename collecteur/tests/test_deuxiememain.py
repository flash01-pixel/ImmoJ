"""Tests du parser 2ememain sur des réponses JSON réelles enregistrées."""

import json
from datetime import date, timedelta

import pytest

from immoj.sources.deuxiememain import DeuxiemeMain, _date, analyser_json


@pytest.fixture
def lire_json(lire_fixture):
    return lambda chemin: json.loads(lire_fixture(chemin))


def test_toute_la_belgique_filtree_sur_la_province(lire_json):
    # Réponse sans filtre géographique : presque tout est hors province.
    annonces = analyser_json(lire_json("2ememain/api_search.json"))
    assert len(annonces) <= 3
    for a in annonces:
        assert a.code_postal.startswith("6")


def test_rayon_60km(lire_json):
    annonces = analyser_json(lire_json("2ememain/api_rayon_60km.json"))
    assert len(annonces) >= 10, "une vingtaine de biens résidentiels dans la province"
    assert {a.transaction for a in annonces} == {"location", "achat"}
    for a in annonces:
        assert a.source == "2ememain"
        assert a.type in ("maison", "appartement", "studio", "kot", "terrain")
        assert a.url.startswith("https://www.2ememain.be/v/immo/")
        assert a.commune and a.code_postal.startswith("6")
        assert a.latitude and 49.4 < a.latitude < 50.5
        if a.transaction == "location":
            assert a.prix is None or 50 <= a.prix <= 10_000
    # Catégories hors sujet (garages, étranger, colocation « recherche ») absentes
    assert not any("colocation" in a.titre.lower() for a in annonces)


def test_detail_dune_annonce(lire_json):
    annonces = analyser_json(lire_json("2ememain/api_rayon_60km.json"))
    a = next(x for x in annonces if x.titre.startswith("Petite maison 2 chambres"))
    assert a.transaction == "location" and a.type == "maison"
    assert a.commune and a.code_postal == "6760"   # Bleid → Virton
    assert a.chambres == 2 or a.chambres is None
    assert a.photos and a.photos[0].startswith("https://")


def test_demandes_exclues():
    donnees = {"listings": [{
        "itemId": "m1", "title": "CHERCHE maison à louer région Arlon", "categoryId": 2143,
        "location": {"cityName": "Arlon"}, "priceInfo": {"priceCents": 80000, "priceType": "FIXED"},
    }, {
        "itemId": "m2", "title": "Garage à louer", "categoryId": 1643,
        "location": {"cityName": "Arlon"}, "priceInfo": {"priceCents": 8000, "priceType": "FIXED"},
    }]}
    assert analyser_json(donnees) == []


def test_dates():
    assert _date("Aujourd'hui") == date.today()
    assert _date("Hier") == date.today() - timedelta(days=1)
    assert _date("Avant-hier") == date.today() - timedelta(days=2)
    assert _date("19 oct. '26") == date(2026, 10, 19)
    assert _date("3 févr. '25") == date(2025, 2, 3)
    assert _date(None) is None


def test_url_api():
    src = DeuxiemeMain.__new__(DeuxiemeMain)
    url = src.url_api(offset=30, postcode="6600")
    assert "l1CategoryId=1032" in url and "postcode=6600" in url and "distanceMeters=30000" in url
    assert "offset=30" in url


def test_filtre_categorie_ignore_par_le_serveur(lire_json):
    """Capture du 22/10 : l2CategoryId=2771 renvoie les mêmes 185 résultats que sans filtre."""
    avec = lire_json("2ememain/api_cat_2771.json")
    assert avec["totalResultCount"] == 185
    assert {x["categoryId"] for x in avec["listings"]} != {2771}


def test_centres_multiples_couvrent_la_province(lire_json):
    """Les 4 centres à 30 km lisent des communes que le cercle unique ne listait pas en tête,
    et le géocodeur en garde la commune citée en tête (« Arlon + Partie De Wolkrange »)."""
    annonces = []
    for centre in ("arlon", "bastogne", "libramont", "marche"):
        annonces += analyser_json(lire_json(f"2ememain/api_centre_{centre}_30km.json"))
    communes = {a.commune for a in annonces}
    assert any(c.startswith("Arlon") for c in communes)
    assert any("Vaux" in c for c in communes)          # « Vaux-Lez-Rosieres » retrouvée
    assert all(a.code_postal for a in annonces)
    particuliers = [a for a in annonces if a.annonceur == "particulier"]
    agences = [a for a in annonces if a.annonceur == "agence"]
    assert particuliers and agences
    assert all(a.agence is None for a in particuliers), "jamais un prénom affiché comme agence"


def test_annonceur_2ememain():
    def brut(nom, titre="Maison à louer", description=""):
        return {"listings": [{
            "itemId": "x", "title": titre, "description": description, "categoryId": 2143,
            "location": {"cityName": "Arlon"},
            "priceInfo": {"priceCents": 80000, "priceType": "FIXED"},
            "sellerInformation": {"sellerName": nom},
        }]}
    assert analyser_json(brut("Pauline"))[0].annonceur == "particulier"
    assert analyser_json(brut("Ard'Immo & Conseils"))[0].annonceur == "agence"
    assert analyser_json(brut("CVA GERCA"))[0].annonceur == "agence"
    pro = analyser_json(brut("Marc", description="Contactez notre agence"))[0]
    assert pro.annonceur == "agence"
    assert analyser_json(brut("Marc"))[0].agence is None
