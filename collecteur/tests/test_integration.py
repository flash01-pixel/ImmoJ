"""Test du flux complet d'intégration avec un faux stockage en mémoire.

On vérifie : import initial silencieux, nouveauté notifiée, doublon rattaché,
baisse de prix notifiée, prix inchangé = rien.
"""

from immoj.config import Secrets
from immoj.dedoublonnage import Candidat
from immoj.geocodage import Geocodeur
from immoj.main import Integrateur
from immoj.modele import Annonce
from immoj.notifications import Notificateur
from immoj.sources.base import ResultatCollecte


class StockageMemoire:
    """Imite les méthodes de `Stockage` utilisées par l'intégrateur."""

    def __init__(self) -> None:
        self.annonces: dict[str, dict] = {}
        self.sources: dict[tuple[str, str], dict] = {}
        self.prix: list[tuple[str, int]] = []
        self.notifs: set[tuple] = set()
        self.reussites: set[str] = set()
        self._n = 0

    def sources_connues(self, source):
        return {k[1]: v for k, v in self.sources.items() if k[0] == source}

    def a_deja_reussi(self, source):
        return source in self.reussites

    def candidats_doublon(self, transaction, commune, code_postal):
        return [Candidat(id=i, transaction=a["transaction"], commune=a["commune"],
                         code_postal=a["code_postal"], prix=a["prix"], chambres=a["chambres"],
                         surface=a["surface"], titre=a["titre"], description=a["description"])
                for i, a in self.annonces.items()
                if a["transaction"] == transaction and a["commune"] == commune]

    def creer_annonce(self, a, commune, lat, lon):
        self._n += 1
        i = f"id{self._n}"
        self.annonces[i] = {"transaction": a.transaction, "commune": commune,
                            "code_postal": a.code_postal, "prix": a.prix, "chambres": a.chambres,
                            "surface": a.surface, "titre": a.titre, "description": a.description,
                            "lat": lat, "lon": lon}
        return i

    def ajouter_source(self, annonce_id, a):
        self.sources[(a.source, a.id_externe)] = {"annonce_id": annonce_id,
                                                  "prix_affiche": a.prix}

    def completer_annonce(self, annonce_id, a):
        pass

    def changer_prix(self, annonce_id, source, id_externe, ancien, nouveau):
        self.prix.append((annonce_id, nouveau))
        self.sources[(source, id_externe)]["prix_affiche"] = nouveau

    def toucher(self, source, ids_externes, annonce_ids):
        pass

    def marquer_notification(self, annonce_id, type_notif, recherche_id):
        cle = (annonce_id, type_notif, recherche_id)
        if cle in self.notifs:
            return False
        self.notifs.add(cle)
        return True


class NotificateurEspion(Notificateur):
    def __init__(self):
        super().__init__(Secrets("", "", "topic"), simulation=True)
        self.envoyees: list[str] = []

    def _publier(self, titre, message, **kw):
        self.envoyees.append(titre)
        return True


def annonce(source="immovlan", ide="A1", prix=800, **kw) -> Annonce:
    base = dict(source=source, id_externe=ide, url=f"https://{source}/{ide}",
                titre="Appartement à louer", transaction="location", type="appartement",
                prix=prix, chambres=2, surface=85, commune="Bastogne", code_postal="6600",
                description="Bel appartement lumineux au centre de Bastogne avec terrasse.")
    base.update(kw)
    return Annonce(**base)


def integrateur(stockage, notif, recherches=None):
    return Integrateur(stockage, Geocodeur(), notif, recherches or [])


def test_import_initial_sans_notification_individuelle():
    st, nt = StockageMemoire(), NotificateurEspion()
    resultat = ResultatCollecte("immovlan", True, [annonce(), annonce(ide="A2")])
    n = integrateur(st, nt).integrer(resultat)
    assert n == 2, "deux annonces d'une même source ne sont jamais fusionnées"
    assert nt.envoyees == ["ImmoJ : immovlan connecté"]
    assert st.annonces["id1"]["lat"] is not None, "géocodée au centre de Bastogne"


def test_nouveaute_notifiee_apres_import_initial():
    st, nt = StockageMemoire(), NotificateurEspion()
    st.reussites.add("immovlan")
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce()]))
    assert nt.envoyees == ["Nouveau : 6600 Bastogne"]


def test_recherche_sauvegardee_filtre_les_notifications():
    st, nt = StockageMemoire(), NotificateurEspion()
    st.reussites.add("immovlan")
    recherches = [{"id": "r1", "nom": "Arlon pas cher", "communes": ["Arlon"], "notifier": True}]
    integrateur(st, nt, recherches).integrer(ResultatCollecte("immovlan", True, [annonce()]))
    assert nt.envoyees == [], "Bastogne n'est pas dans la recherche"
    recherches[0]["communes"] = ["Bastogne"]
    integrateur(st, nt, recherches).integrer(
        ResultatCollecte("immovlan", True, [annonce(ide="A2")]))
    assert nt.envoyees == ["Arlon pas cher : 6600 Bastogne"]


def test_doublon_rattache_sans_nouvelle_fiche():
    st, nt = StockageMemoire(), NotificateurEspion()
    st.reussites.update({"immovlan", "zimmo"})
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce()]))
    n = integrateur(st, nt).integrer(
        ResultatCollecte("zimmo", True, [annonce(source="zimmo", ide="Z9", prix=805)]))
    assert n == 0 and len(st.annonces) == 1
    assert st.sources[("zimmo", "Z9")]["annonce_id"] == "id1"
    assert nt.envoyees == ["Nouveau : 6600 Bastogne"], "une seule notification pour le même bien"


def test_baisse_de_prix():
    st, nt = StockageMemoire(), NotificateurEspion()
    st.reussites.add("immovlan")
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce(prix=800)]))
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce(prix=800)]))
    assert st.prix == []
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce(prix=750)]))
    assert st.prix == [("id1", 750)]
    assert nt.envoyees == ["Nouveau : 6600 Bastogne", "Prix baissé : 6600 Bastogne"]
    # Une hausse est enregistrée mais pas notifiée.
    integrateur(st, nt).integrer(ResultatCollecte("immovlan", True, [annonce(prix=900)]))
    assert st.prix[-1] == ("id1", 900) and len(nt.envoyees) == 2
