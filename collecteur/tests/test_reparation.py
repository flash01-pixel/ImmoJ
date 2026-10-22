"""Réparation de la base (lot 4) : orphelines, doublons, annonceur, types, précision."""

from immoj.config import Secrets
from immoj.geocodage import Geocodeur
from immoj.main import Integrateur
from immoj.modele import Annonce
from immoj.notifications import Notificateur
from immoj.reparation import doublons_structurels, reparer_stockage
from immoj.sources.base import ResultatCollecte
from immoj.stockage_fichier import StockageFichier


def annonce(source="immovlan", ide="A1", **kw) -> Annonce:
    base = dict(source=source, id_externe=ide, url=f"https://{source}/{ide}",
                titre="Appartement à louer", transaction="location", type="appartement",
                prix=950, chambres=2, surface=92, commune="Bastogne", code_postal="6600",
                description="Honesty vous propose cet appartement.", agence="HONESTY BERTRIX",
                annonceur="agence")
    base.update(kw)
    return Annonce(**base)


def integrer(st, *annonces):
    notif = Notificateur(Secrets("", "", ""), simulation=True)
    return Integrateur(st, Geocodeur(), notif, []).integrer(
        ResultatCollecte(annonces[0].source, True, list(annonces)))


def test_orphelines_desactivees(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, annonce())
    # Une jumelle orpheline, comme celles créées avant le lot 4 (C4).
    aid = st.creer_annonce(annonce(ide="A1bis"), "Bastogne", 50.0, 5.717)
    assert st.annonces[aid]["actif"]
    bilan = reparer_stockage(st, Geocodeur())
    assert bilan["orphelines"] == 1 and not st.annonces[aid]["actif"]
    # Non-régression : un second passage ne touche plus à rien.
    assert reparer_stockage(st, Geocodeur())["orphelines"] == 0


def test_doublons_entre_sources_fusionnes(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, annonce())
    # Même bien chez Immoweb, créé séparément (titre différent, avant la règle structurée).
    aid2 = st.creer_annonce(annonce(source="immoweb", ide="I1",
                                    titre="Bastogne: Superbe appartement neuf - 2 chambres",
                                    photos=["https://p/2.jpg"]), "Bastogne", 50.0, 5.717)
    st.ajouter_source(aid2, annonce(source="immoweb", ide="I1"))
    # Un autre bien de la même agence (prix différent) reste distinct.
    integrer(st, annonce(ide="A1"), annonce(ide="A2", prix=875))
    paires = doublons_structurels(st.annonces_actives_brutes())
    assert len(paires) == 1 and paires[0][1]["id"] == aid2
    bilan = reparer_stockage(st, Geocodeur())
    assert bilan["doublons"] == 1
    cible_id = paires[0][0]["id"]
    assert not st.annonces[aid2]["actif"]
    assert st.sources["immoweb:I1"]["annonce_id"] == cible_id
    assert "https://p/2.jpg" in st.annonces[cible_id]["photos"]
    assert sum(a["actif"] for a in st.annonces.values()) == 2


def test_jumeaux_sur_le_meme_site_jamais_fusionnes(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, annonce(ide="A1"), annonce(ide="A2"))   # deux appartements identiques, même site
    assert doublons_structurels(st.annonces_actives_brutes()) == []
    assert reparer_stockage(st, Geocodeur())["doublons"] == 0


def test_annonceur_et_types_repares(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, annonce(source="immoweb", ide="P1", agence="PRIVATE", annonceur="inconnu",
                         titre="Hall industriel à Transinne", type="autre"))
    aid = next(iter(st.annonces))
    # Base d'avant le lot 4 : agence PRIVATE affichée, annonceur inconnu, type « autre ».
    st.annonces[aid].update({"agence": "PRIVATE", "annonceur": "inconnu", "type": "autre",
                             "precision_position": None})
    st.sources["immoweb:P1"]["agence"] = "PRIVATE"
    bilan = reparer_stockage(st, Geocodeur())
    a = st.annonces[aid]
    assert a["annonceur"] == "particulier" and a["agence"] is None
    assert a["type"] == "commerce"
    assert a["precision_position"] == "commune"
    assert bilan["annonceur"] == 1 and bilan["types"] == 1 and bilan["precision"] == 1


def test_migration_base_ancienne(tmp_path):
    chemin = tmp_path / "base.json"
    st = StockageFichier(chemin)
    integrer(st, annonce(source="immoweb", ide="P1", agence="PRIVATE", annonceur="particulier"))
    for a in st.annonces.values():
        del a["annonceur"]
        a["agence"] = "PRIVATE"
    st.sauvegarder()
    relu = StockageFichier(chemin)
    a = next(iter(relu.annonces.values()))
    assert a["annonceur"] == "particulier" and a["agence"] is None
