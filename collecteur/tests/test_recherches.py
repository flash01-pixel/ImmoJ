from immoj.modele import Annonce
from immoj.recherches import correspond, distance_km


def annonce(**kw) -> Annonce:
    base = dict(source="immovlan", id_externe="X1", url="https://x", titre="Appartement",
                transaction="location", type="appartement", prix=800, chambres=2, surface=80,
                peb="C", commune="Arlon", code_postal="6700")
    base.update(kw)
    return Annonce(**base)


def test_distance():
    # Arlon → Bastogne ≈ 36 km
    assert 30 < distance_km(49.683, 5.817, 50.000, 5.717) < 40


def test_recherche_vide_accepte_tout():
    assert correspond(annonce(), {}, "Arlon", 49.68, 5.82)


def test_transaction():
    assert not correspond(annonce(), {"transaction": "achat"}, "Arlon", None, None)


def test_communes():
    r = {"communes": ["Arlon", "Attert"]}
    assert correspond(annonce(), r, "Arlon", None, None)
    assert not correspond(annonce(), r, "Virton", None, None)


def test_rayon():
    r = {"centre_lat": 49.683, "centre_lon": 5.817, "rayon_km": 10}
    assert correspond(annonce(), r, "Arlon", 49.70, 5.80)
    assert not correspond(annonce(), r, "Bastogne", 50.0, 5.717)
    assert not correspond(annonce(), r, "Arlon", None, None), "sans GPS on ne peut pas dire"


def test_communes_ou_rayon():
    r = {"communes": ["Virton"], "centre_lat": 49.683, "centre_lon": 5.817, "rayon_km": 10}
    assert correspond(annonce(), r, "Arlon", 49.70, 5.80)       # dans le rayon
    assert correspond(annonce(), r, "Virton", 49.567, 5.533)    # dans la liste


def test_prix_et_chambres():
    assert not correspond(annonce(prix=1200), {"prix_max": 1000}, "Arlon", None, None)
    assert not correspond(annonce(prix=500), {"prix_min": 600}, "Arlon", None, None)
    assert not correspond(annonce(chambres=1), {"chambres_min": 2}, "Arlon", None, None)
    assert correspond(annonce(), {"prix_min": 600, "prix_max": 1000, "chambres_min": 2},
                      "Arlon", None, None)


def test_types_et_peb():
    assert not correspond(annonce(type="maison"), {"types": ["appartement"]}, "Arlon", None, None)
    assert correspond(annonce(peb="B"), {"peb_max": "C"}, "Arlon", None, None)
    assert not correspond(annonce(peb="E"), {"peb_max": "C"}, "Arlon", None, None)
    assert correspond(annonce(peb=None), {"peb_max": "C"}, "Arlon", None, None)


def test_criteres_booleens():
    assert not correspond(annonce(animaux=False), {"animaux": True}, "Arlon", None, None)
    assert correspond(annonce(animaux=None), {"animaux": True}, "Arlon", None, None)
    assert correspond(annonce(jardin=True), {"jardin": True}, "Arlon", None, None)
