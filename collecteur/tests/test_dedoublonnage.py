from immoj.dedoublonnage import Candidat, trouver_doublon


def cand(**kw) -> Candidat:
    base = dict(id="x", transaction="location", commune="Arlon", code_postal="6700", prix=800,
                chambres=2, surface=85, titre="Appartement 2 chambres à Arlon",
                description="Bel appartement lumineux avec terrasse, proche du centre.")
    base.update(kw)
    return Candidat(**base)


def test_meme_bien_sur_deux_sites():
    existante = cand(id="a1")
    nouvelle = cand(id="", prix=810, titre="Appartement à louer",
                    description="Bel appartement lumineux avec terrasse proche du centre")
    assert trouver_doublon(nouvelle, [existante]) is existante


def test_prix_trop_differents():
    assert trouver_doublon(cand(prix=950), [cand(id="a1")]) is None


def test_chambres_differentes():
    assert trouver_doublon(cand(chambres=3), [cand(id="a1")]) is None


def test_communes_differentes():
    assert trouver_doublon(cand(commune="Virton", code_postal="6760"), [cand(id="a1")]) is None


def test_textes_differents_meme_immeuble():
    nouvelle = cand(titre="Studio rénové rue de la Gare",
                    description="Studio au deuxième étage, cuisine neuve, cave.")
    assert trouver_doublon(nouvelle, [cand(id="a1")]) is None


def test_peu_d_infos_exige_plus_de_similarite():
    existante = cand(id="a1", chambres=None, surface=None)
    presque = cand(chambres=None, surface=None, titre="Appartement à Arlon",
                   description="Appartement lumineux à Arlon avec terrasse.")
    identique = cand(chambres=None, surface=None)
    assert trouver_doublon(presque, [existante]) is None
    assert trouver_doublon(identique, [existante]) is existante


def test_choisit_le_meilleur():
    a1 = cand(id="a1", titre="Maison 2 ch", description="Autre chose totalement différente ici")
    a2 = cand(id="a2")
    assert trouver_doublon(cand(), [a1, a2]) is a2
