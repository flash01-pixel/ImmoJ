from immoj.geocodage import Geocodeur, normaliser


def test_44_communes():
    assert len(Geocodeur().communes) == 44


def test_normaliser():
    assert normaliser("Érezée") == "erezee"
    assert normaliser("La Roche-en-Ardenne") == "la roche en ardenne"


def test_commune_officielle():
    g = Geocodeur()
    assert g.trouver("Bastogne").nom == "Bastogne"
    assert g.trouver("bastogne").nom == "Bastogne"
    assert g.trouver("ARLON").nom == "Arlon"


def test_localite_vers_commune():
    g = Geocodeur()
    assert g.trouver("Athus").nom == "Aubange"
    assert g.trouver("Barvaux").nom == "Durbuy"
    assert g.trouver("Marloie").nom == "Marche-en-Famenne"
    assert g.trouver("Nadrin").nom == "Houffalize"


def test_code_postal_seul():
    g = Geocodeur()
    assert g.trouver(None, "6791").nom == "Aubange"
    assert g.trouver("Inconnu", "6900").nom == "Marche-en-Famenne"


def test_hors_province():
    g = Geocodeur()
    assert g.trouver("Namur", "5000") is None
    assert not g.est_dans_la_province("Liège", "4000")


def test_coordonnees():
    c = Geocodeur().trouver("Arlon")
    assert 49.6 < c.lat < 49.8 and 5.7 < c.lon < 5.9
