"""Le stockage JSON doit se comporter comme Supabase pour l'intégrateur."""

import json

from immoj.config import Secrets
from immoj.geocodage import Geocodeur
from immoj.main import Integrateur
from immoj.modele import Annonce
from immoj.notifications import Notificateur
from immoj.sources.base import ResultatCollecte
from immoj.stockage_fichier import StockageFichier


def annonce(source="immovlan", ide="A1", prix=800, **kw) -> Annonce:
    base = dict(source=source, id_externe=ide, url=f"https://{source}/{ide}",
                titre="Appartement à louer", transaction="location", type="appartement",
                prix=prix, chambres=2, surface=85, commune="Bastogne", code_postal="6600",
                description="Bel appartement lumineux au centre de Bastogne avec terrasse.")
    base.update(kw)
    return Annonce(**base)


def integrer(st, resultat, profond=False):
    notif = Notificateur(Secrets("", "", ""), simulation=True)
    n = Integrateur(st, Geocodeur(), notif, []).integrer(resultat)
    st.enregistrer_collecte(resultat.source, "2026-10-21T10:00:00+00:00", True,
                            len(resultat.annonces), n, "", False, 1.0, profond=profond)
    return n


def test_cycle_complet(tmp_path):
    chemin = tmp_path / "base.json"
    st = StockageFichier(chemin)
    assert integrer(st, ResultatCollecte("immovlan", True, [annonce(), annonce(ide="A2")])) == 2
    # Doublon depuis une autre source → rattaché, pas de nouvelle fiche
    assert integrer(st, ResultatCollecte("zimmo", True,
                                         [annonce(source="zimmo", ide="Z1", prix=805)])) == 0
    assert len(st.annonces) == 2 and len(st.sources) == 3
    # Baisse de prix
    integrer(st, ResultatCollecte("immovlan", True, [annonce(prix=750), annonce(ide="A2")]))
    a1 = next(a for a in st.annonces.values() if a["prix"] == 750)
    assert a1["prix_precedent"] == 800 and a1["prix_baisse_le"]

    # Persistance : relecture depuis le fichier
    st2 = StockageFichier(chemin)
    assert len(st2.annonces) == 2 and st2.a_deja_reussi("immovlan") and not st2.a_deja_reussi("x")
    assert st2.echecs_consecutifs("immovlan") == 0

    # Export pour l'appli (une seule fois par passage, C9)
    st2.exporter(tmp_path / "annonces.json")
    export = json.loads((tmp_path / "annonces.json").read_text(encoding="utf-8"))
    assert len(export["communes"]) == 44
    assert {s["source"] for s in export["sources"]} == {"immovlan", "zimmo"}
    assert len(export["annonces"]) == 2
    exp = next(a for a in export["annonces"] if a["prix"] == 750)
    assert exp["sources"] == ["immovlan", "zimmo"] and len(exp["urls"]) == 2
    assert [h["prix"] for h in exp["historique_prix"]] == [800, 750]
    assert exp["commune"] == "Bastogne" and exp["latitude"]


def test_desactivation_seulement_apres_passage_profond(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, ResultatCollecte("immovlan", True, [annonce()]))
    for a in st.annonces.values():
        a["derniere_vue"] = "2020-01-01T00:00:00+00:00"
    # Seuls des passages légers depuis : le bien a pu reculer au-delà de la page 3.
    assert st.desactiver_anciennes(7) == 0
    # Un passage profond réussi ne l'a pas revue : désactivée.
    resultat = ResultatCollecte("immovlan", True, [annonce(ide="A9")], profond=True)
    integrer(st, resultat, profond=True)
    assert st.desactiver_anciennes(7) == 1
    assert st.exporter(tmp_path / "annonces.json") == 1


def test_hors_province_desactivees(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    integrer(st, ResultatCollecte("immovlan", True, [annonce(ide="A1")]))
    # Un bien entré avant le filtre, avec un code postal liégeois
    aid = next(iter(st.annonces))
    st.annonces[aid]["code_postal"] = "4000"
    assert st.desactiver_hors_province() == 1 and st.desactiver_hors_province() == 0


def test_echecs_consecutifs(tmp_path):
    st = StockageFichier(tmp_path / "base.json")
    for ok in (True, False, False):
        st.enregistrer_collecte("immoweb", "2026-10-21T10:00:00+00:00", ok, 0, 0, "x", False, 1)
    assert st.echecs_consecutifs("immoweb") == 2
