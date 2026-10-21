"""Tests du parser immo.notaire.be sur les pages réelles de la province."""

from immoj.sources.notaire import Notaire, analyser_liste


def test_page_province(lire_fixture):
    annonces = analyser_liste(lire_fixture("notaire/province_luxembourg.html"))
    assert len(annonces) == 25
    assert len({a.id_externe for a in annonces}) == 25
    for a in annonces:
        assert a.transaction == "achat" and a.source == "notaire"
        assert a.url.startswith("https://immo.notaire.be/fr/")
        assert a.code_postal and a.code_postal.startswith("6"), a.url
        assert a.commune
    maison = next(a for a in annonces if a.id_externe == "173727")
    assert maison.titre == "Grande maison à Martelange"
    assert maison.prix == 252000 and maison.code_postal == "6630" and maison.commune == "Martelange"
    assert maison.type == "maison" and maison.photos[0].startswith("https://immo.notaire.be/")
    assert "gré à gré" in maison.description


def test_ventes_publiques_marquees(lire_fixture):
    annonces = analyser_liste(lire_fixture("notaire/province_luxembourg.html")) + \
        analyser_liste(lire_fixture("notaire/province_luxembourg_p2.html"))
    encheres = [a for a in annonces if a.titre.startswith("[Vente online]")
                or a.titre.startswith("[Vente publique]")]
    assert len(encheres) >= 10, "les ventes Biddit (« Vente online ») sont marquées"
    assert all("online" in a.description or "publique" in a.description for a in encheres)


def test_page_2_differente(lire_fixture):
    p1 = {a.id_externe for a in analyser_liste(lire_fixture("notaire/province_luxembourg.html"))}
    p2 = {a.id_externe for a in analyser_liste(lire_fixture("notaire/province_luxembourg_p2.html"))}
    assert p2 and not (p1 & p2)


def test_url():
    src = Notaire.__new__(Notaire)
    assert src.url_liste(2) == ("https://immo.notaire.be/fr/biens-a-vendre-dans-la-province/luxembourg"
                                "?province=WLX&order_by=publicationStartDate%3ADESC&page=2")
    assert Notaire.transactions == ("achat",)
