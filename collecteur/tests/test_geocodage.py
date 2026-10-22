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


# --- Lot 4 (C3) : libellés composés des portails -----------------------------

def test_commune_citee_en_tete():
    g = Geocodeur()
    assert g.trouver("Hotton + Partie De Soy").nom == "Hotton"
    assert g.trouver("Tenneville+Partie De Flamierge").nom == "Tenneville"
    assert g.trouver("Bastogne + Partie De Longchamps Et Sibret").nom == "Bastogne"
    marche = g.trouver("Marche-En-Famenne +Partie De Baillonville Et Noiseux")
    assert marche.nom == "Marche-en-Famenne"
    assert g.trouver("Bertrix + Partie D'Offagne").nom == "Bertrix"
    assert g.trouver("Marche-en-Famenne (Marloie)").nom == "Marche-en-Famenne"


def test_commune_voisine_hors_province():
    g = Geocodeur()
    # Somme-Leuze (Namur) cite des sections proches de Durbuy : ne pas se laisser tromper.
    assert g.trouver("Somme-Leuze + Partie De Grandhan Et De Maffe") is None
    assert g.trouver("Rochefort") is None and g.trouver("Han-sur-Lesse") is None


def test_localites_completees():
    g = Geocodeur()
    assert g.trouver("Vaux-Lez-Rosieres").nom == "Vaux-sur-Sûre"
    assert g.trouver("Sibret").nom == "Vaux-sur-Sûre"
    assert g.trouver("Longchamps").nom == "Bertogne"
    assert g.trouver("Soy").nom == "Érezée"


def test_correspondance_la_plus_longue():
    g = Geocodeur()
    assert g.trouver("Arlon Centre").nom == "Arlon"
    assert g.trouver("Appartement à La Roche-en-Ardenne").nom == "La Roche-en-Ardenne"
    assert g.trouver("Han").nom == "Tintigny", "nom exact : oui"
    assert g.trouver("Chemin du Han 5") is None, "un fragment de 3 lettres ne suffit pas"


def test_contour_et_coordonnees():
    g = Geocodeur()
    assert g.dans_le_contour(49.683, 5.817)          # Arlon
    assert g.dans_le_contour(50.0, 5.717)            # Bastogne
    assert g.dans_le_contour(49.567, 5.83)           # Athus (frontière)
    assert not g.dans_le_contour(50.467, 4.867)      # Namur
    assert not g.dans_le_contour(49.611, 6.13)       # Luxembourg-ville
    assert not g.dans_le_contour(50.26, 4.91)        # Dinant
    assert g.par_coordonnees(49.683, 5.817).nom == "Arlon"
    assert g.par_coordonnees(50.467, 4.867) is None
    # Commune inconnue mais coordonnées en province → commune la plus proche.
    assert g.trouver("Hameau Inconnu", None, 50.0, 5.717).nom == "Bastogne"
    assert g.trouver("Hameau Inconnu", None, 50.467, 4.867) is None
