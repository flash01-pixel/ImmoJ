"""Tests du parser Immovlan sur des pages de résultats réelles enregistrées."""

from datetime import date

from immoj.sources.immovlan import Immovlan, _charges, analyser_liste


def test_liste_location_contient_20_annonces(lire_fixture):
    annonces = analyser_liste(lire_fixture("immovlan/liste_location.html"), "location")
    assert len(annonces) == 20
    assert len({a.id_externe for a in annonces}) == 20, "identifiants uniques"
    for a in annonces:
        assert a.source == "immovlan"
        assert a.transaction == "location"
        assert a.url.startswith("https://immovlan.be/fr/detail/")
        assert a.id_externe == a.id_externe.upper()
        assert a.prix is None or 100 <= a.prix <= 10_000, a.prix
        assert a.code_postal and a.code_postal.startswith("6"), "province de Luxembourg"
        assert a.commune


def test_premiere_annonce_location_details(lire_fixture):
    a = analyser_liste(lire_fixture("immovlan/liste_location.html"), "location")[0]
    assert a.id_externe == "VBE80830"
    assert a.type == "appartement"
    assert a.titre == "Appartement à louer"
    assert a.prix == 950
    assert a.charges == 145            # « 950 € + 145 € de provisions »
    assert a.code_postal == "6600"
    assert a.commune == "Bastogne"
    assert a.chambres == 2
    assert a.surface == 92
    assert a.terrasse is True
    assert a.peb == "A"
    assert a.agence == "HONESTY BERTRIX"
    assert a.telephone == "+32 61 29 24 14"
    assert a.date_publication == date(2026, 10, 17)
    assert a.photos and a.photos[0].startswith("https://api-image.immovlan.be/")
    assert "Honesty vous propose" in a.description


def test_liste_achat(lire_fixture):
    annonces = analyser_liste(lire_fixture("immovlan/liste_achat.html"), "achat")
    # 20 cartes, dont 4 « projets » de promoteurs (projectdetail) volontairement ignorés
    assert len(annonces) == 16
    assert all(a.transaction == "achat" for a in annonces)
    assert sum(a.prix is not None for a in annonces) >= 12  # quelques « prix sur demande »
    assert {a.type for a in annonces} <= {"maison", "appartement", "studio", "terrain", "autre"}


def test_types_devines_depuis_url(lire_fixture):
    annonces = analyser_liste(lire_fixture("immovlan/liste_location.html"), "location")
    par_id = {a.id_externe: a for a in annonces}
    assert par_id["VBE79064"].type == "maison"
    assert par_id["VWD19126"].type == "appartement"   # rez-de-chaussée
    assert par_id["VBE82293"].type == "appartement"   # duplex


def test_page_vide():
    assert analyser_liste("<html><body>rien</body></html>", "location") == []


def test_url_liste_sans_noindex():
    src = Immovlan.__new__(Immovlan)
    url = src.url_liste("location", 2)
    assert "noindex" not in url, "interdit par robots.txt"
    assert "provinces=luxembourg" in url and "page=2" in url
    assert "page=" not in src.url_liste("achat", 1)


def test_extraction_charges():
    assert _charges("Loyer 800 € + 120 € de charges") == 120
    assert _charges("charges : 95€ par mois") == 95
    assert _charges("Provisions pour charges de 150 €") == 150
    assert _charges("Aucune information") is None
