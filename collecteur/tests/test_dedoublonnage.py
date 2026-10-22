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
    # Surface inconnue d'un côté et agences différentes : seul le texte peut trancher.
    nouvelle = cand(titre="Studio rénové rue de la Gare", surface=None, agence="Immo A",
                    description="Studio au deuxième étage, cuisine neuve, cave.")
    assert trouver_doublon(nouvelle, [cand(id="a1", agence="Immo B")]) is None


def test_peu_d_infos_exige_plus_de_similarite():
    existante = cand(id="a1", chambres=None, surface=None)
    presque = cand(chambres=None, surface=None, titre="Appartement à Arlon",
                   description="Appartement lumineux à Arlon avec terrasse.")
    identique = cand(chambres=None, surface=None)
    assert trouver_doublon(presque, [existante]) is None
    assert trouver_doublon(identique, [existante]) is existante


def test_choisit_le_meilleur():
    a1 = cand(id="a1", titre="Maison 2 ch", description="Autre chose totalement différente ici",
              surface=None, agence="Immo B")
    a2 = cand(id="a2")
    assert trouver_doublon(cand(), [a1, a2]) is a2


# --- Lot 4 (C5) : correspondance structurée, cas réels -------------------------

def test_titres_differents_mais_structure_identique():
    # Bastogne 950 € 2 ch. 92 m² : « Appartement à louer » (Immovlan) et
    # « Bastogne: Superbe appartement neuf - 2 chambres » (Immoweb).
    existante = cand(id="a1", commune="Bastogne", code_postal="6600", prix=950, chambres=2,
                     surface=92, titre="Appartement à louer", description="Honesty vous propose…",
                     agence="HONESTY BERTRIX")
    nouvelle = cand(commune="Bastogne", code_postal="6600", prix=950, chambres=2, surface=92,
                    titre="Bastogne: Superbe appartement neuf - 2 chambres",
                    description="Idéalement situé au centre-ville, cet appartement neuf…",
                    agence="Honesty Bertrix - 7 bureaux proches de chez vous")
    assert trouver_doublon(nouvelle, [existante]) is existante


def test_structure_identique_mais_surface_trop_differente():
    # Habay 1 100 € 2 ch. : 115 m² (Immojidée) contre 80 m² (Honesty) → deux biens.
    existante = cand(id="a1", commune="Habay", prix=1100, surface=115, agence="IMMOJIDEE")
    nouvelle = cand(commune="Habay", prix=1100, surface=80, titre="Appartement à Habay Marbehan",
                    description="Autre bien", agence="Honesty")
    assert trouver_doublon(nouvelle, [existante]) is None


def test_surface_inconnue_meme_agence():
    # Paliseul 750 € 2 ch. : « Rez-De-Chaussée à louer » (Immovlan, 100 m²) et
    # « MAISSIN: Appartement REZ avec terrasse » (site Ard'Immo, surface inconnue).
    existante = cand(id="a1", commune="Paliseul", prix=750, chambres=2, surface=100,
                     titre="Rez-De-Chaussée à louer", description="", agence="ARD'IMMO & CONSEILS")
    nouvelle = cand(commune="Paliseul", prix=750, chambres=2, surface=None,
                    titre="MAISSIN: Appartement REZ avec terrasse, jardinet et garage",
                    description="", agence="Ard'Immo & Conseils")
    assert trouver_doublon(nouvelle, [existante]) is existante
    autre_agence = cand(commune="Paliseul", prix=750, chambres=2, surface=None,
                        titre="MAISSIN: Appartement REZ", description="", agence="Immo Durand")
    assert trouver_doublon(autre_agence, [existante]) is None


def test_meme_reference():
    existante = cand(id="a1", titre="Maison", description="", reference="REF-5042")
    nouvelle = cand(titre="Villa 4 façades", description="", prix=820, chambres=None,
                    surface=None, reference="ref 5042")
    assert trouver_doublon(nouvelle, [existante]) is existante


def test_prix_different_jamais_fort():
    existante = cand(id="a1", prix=800, surface=92)
    nouvelle = cand(prix=810, surface=92, titre="Autre titre", description="Autre description")
    assert trouver_doublon(nouvelle, [existante]) is None
