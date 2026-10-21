"""Lecture des alertes e-mail des portails (Immoweb, Zimmo, Logic-immo).

Méthode de secours fiable pour les sites qui bloquent les serveurs GitHub :
on crée sur chaque portail une « alerte e-mail » pour la province de
Luxembourg, envoyée sur une adresse Gmail dédiée. Le collecteur lit cette boîte
en IMAP (mot de passe d'application dans les secrets GitHub), extrait les liens
d'annonces des e-mails non lus, puis marque les e-mails comme lus.

Chaque annonce trouvée reçoit comme `source` le nom du portail (immoweb, zimmo,
logicimmo) et comme `id_externe` l'identifiant lu dans l'URL : une annonce vue
par e-mail ET par collecte directe est donc naturellement la même publication.

Les e-mails d'alerte contiennent peu d'informations (titre, prix, lieu,
parfois chambres) : la fiche sera complétée si une autre source publie le même
bien. Voir docs/07-alertes-email.md pour la configuration.
"""

from __future__ import annotations

import email
import imaplib
import logging
import re
from email.header import decode_header, make_header
from email.message import Message
from urllib.parse import urlsplit

from bs4 import BeautifulSoup

from ..config import charger_secrets
from ..modele import Annonce, deviner_transaction, deviner_type, nombre
from .base import Source

log = logging.getLogger(__name__)

SERVEUR_IMAP = "imap.gmail.com"

# Pour chaque portail : domaines d'expédition et motif d'URL d'annonce
# (groupe 1 = identifiant). Les URL de suivi sont nettoyées avant.
PORTAILS = {
    "immoweb": {
        "expediteurs": ("immoweb.be",),
        "motif": re.compile(
            r"immoweb\.be/(?:fr|nl|en)/annonce/[^\s\"'<>?#]*?/(\d{6,9})(?:[/?#\"'<\s]|$)"),
        "transaction_url": {"a-louer": "location", "te-huur": "location", "for-rent": "location",
                            "a-vendre": "achat", "te-koop": "achat", "for-sale": "achat"},
    },
    "zimmo": {
        "expediteurs": ("zimmo.be",),
        "motif": re.compile(
            r"zimmo\.be/(?:fr|nl|en)/[^\s\"'<>?#]*?/([A-Z0-9]{5,8})/?(?:[?#\"'<\s]|$)"),
        "transaction_url": {"a-louer": "location", "te-huur": "location",
                            "a-vendre": "achat", "te-koop": "achat"},
    },
    "logicimmo": {
        "expediteurs": ("logic-immo.be", "logic-immo.com"),
        "motif": re.compile(
            r"logic-immo\.be/(?:fr|nl|en)/[^\s\"'<>?#]*?/([A-Za-z0-9-]{4,40})/?(?:[?#\"'<\s]|$)"),
        "transaction_url": {"location": "location", "huur": "location",
                            "vente": "achat", "koop": "achat", "a-louer": "location",
                            "a-vendre": "achat"},
    },
}


class AlertesMail(Source):
    nom = "alertes_mail"
    libelle = "Alertes e-mail"
    respecter_robots = False

    @classmethod
    def est_configuree(cls) -> bool:
        import os
        return bool(os.environ.get("GMAIL_UTILISATEUR") and os.environ.get("GMAIL_MOT_DE_PASSE"))

    def collecter(self, transaction: str) -> list[Annonce]:
        # On lit la boîte une seule fois par exécution (les deux transactions
        # sortent du même lot d'e-mails).
        if getattr(self, "_cache", None) is None:
            self._cache = self._lire_boite()
        return [a for a in self._cache if a.transaction == transaction]

    def _lire_boite(self) -> list[Annonce]:
        secrets = charger_secrets()
        if not secrets.gmail_utilisateur or not secrets.gmail_mot_de_passe:
            raise RuntimeError("GMAIL_UTILISATEUR / GMAIL_MOT_DE_PASSE non configurés "
                               "(voir docs/07-alertes-email.md)")
        annonces: list[Annonce] = []
        boite = imaplib.IMAP4_SSL(SERVEUR_IMAP)
        try:
            boite.login(secrets.gmail_utilisateur, secrets.gmail_mot_de_passe)
            boite.select("INBOX")
            statut, donnees = boite.search(None, "UNSEEN")
            if statut != "OK":
                raise RuntimeError(f"recherche IMAP impossible : {statut}")
            identifiants = donnees[0].split()
            log.info("[alertes_mail] %d e-mail(s) non lu(s)", len(identifiants))
            for num in identifiants[-50:]:   # au plus 50 par exécution
                statut, parties = boite.fetch(num, "(RFC822)")
                if statut != "OK" or not parties or not isinstance(parties[0], tuple):
                    continue
                message = email.message_from_bytes(parties[0][1])
                trouvees = analyser_email(message)
                annonces.extend(trouvees)
                # Lu, qu'il contienne des annonces ou non (pub, newsletter...).
                boite.store(num, "+FLAGS", "\\Seen")
            self.client.nb_requetes = len(identifiants)
        finally:
            try:
                boite.logout()
            except Exception:  # noqa: BLE001
                pass
        return annonces


# ---------------------------------------------------------------------------
# Analyse d'un e-mail (fonctions pures, testables sans IMAP)
# ---------------------------------------------------------------------------

def _entete(message: Message, nom: str) -> str:
    brut = message.get(nom, "")
    try:
        return str(make_header(decode_header(brut)))
    except Exception:  # noqa: BLE001
        return brut


def _corps_html(message: Message) -> str:
    """Renvoie le corps HTML (ou texte) décodé d'un e-mail, multipart ou non."""
    html, texte = "", ""
    parties = message.walk() if message.is_multipart() else [message]
    for partie in parties:
        ctype = partie.get_content_type()
        if ctype not in ("text/html", "text/plain"):
            continue
        charge = partie.get_payload(decode=True)
        if not charge:
            continue
        jeu = partie.get_content_charset() or "utf-8"
        try:
            contenu = charge.decode(jeu, errors="replace")
        except LookupError:
            contenu = charge.decode("utf-8", errors="replace")
        if ctype == "text/html":
            html += contenu
        else:
            texte += contenu
    return html or f"<pre>{texte}</pre>"


def _portail_expediteur(expediteur: str) -> str | None:
    bas = expediteur.lower()
    for nom, infos in PORTAILS.items():
        if any(d in bas for d in infos["expediteurs"]):
            return nom
    return None


def analyser_email(message: Message) -> list[Annonce]:
    """Extrait les annonces d'un e-mail d'alerte. Liste vide si non reconnu."""
    portail = _portail_expediteur(_entete(message, "From"))
    if not portail:
        return []
    sujet = _entete(message, "Subject")
    return analyser_html(_corps_html(message), portail, sujet)


def _nettoyer_url(url: str) -> str:
    """Retire les paramètres de suivi (utm_..., etc.) et les ancres."""
    parts = urlsplit(url)
    return f"{parts.scheme or 'https'}://{parts.netloc}{parts.path}"


def analyser_html(html: str, portail: str, sujet: str = "") -> list[Annonce]:
    """Repère les liens d'annonces et lit les infos affichées autour de chacun."""
    infos = PORTAILS[portail]
    soup = BeautifulSoup(html, "lxml")
    vus: dict[str, Annonce] = {}
    titres_faibles: set[str] = set()   # annonces dont le titre vient d'un lien sans texte (photo)
    for lien in soup.find_all("a", href=True):
        href = lien["href"]
        m = infos["motif"].search(href + " ")
        if not m:
            continue
        identifiant = m.group(1).upper() if portail == "zimmo" else m.group(1)
        if identifiant in vus:
            # Un second lien vers la même annonce (le texte après la photo) : on
            # récupère son titre s'il est meilleur.
            texte_lien = lien.get_text(" ", strip=True)
            if identifiant in titres_faibles and len(texte_lien) >= 4:
                vus[identifiant].titre = texte_lien[:150]
                titres_faibles.discard(identifiant)
            continue
        url = _nettoyer_url(href)
        bloc = _bloc_autour(lien)
        texte = bloc.get_text(" ", strip=True) if bloc else lien.get_text(" ", strip=True)
        transaction = _transaction(href, sujet, texte, infos["transaction_url"])
        if not transaction:
            continue
        code_postal, localite = _lieu(texte)
        titre = lien.get_text(" ", strip=True)
        if len(titre) < 4:
            titre = texte[:80] or f"Annonce {portail}"
            titres_faibles.add(identifiant)
        image = bloc.find("img", src=True) if bloc else None
        photos = [image["src"]] if image and image["src"].startswith("http") else []
        vus[identifiant] = Annonce(
            source=portail,
            id_externe=identifiant,
            url=url,
            titre=titre[:150],
            transaction=transaction,
            type=deviner_type(texte),
            prix=_prix(texte),
            commune=localite,
            code_postal=code_postal,
            chambres=_chambres(texte),
            surface=_surface(texte),
            photos=photos,
            description=texte[:1000],
        )
    return list(vus.values())


def _bloc_autour(lien) -> object | None:
    """Remonte jusqu'à un conteneur assez grand (cellule, ligne, div) autour du lien."""
    noeud = lien
    for _ in range(6):
        if noeud is None or noeud.name == "body":
            break
        texte = noeud.get_text(" ", strip=True)
        if len(texte) > 40 and (re.search(r"\d\s*€|€\s*\d", texte) or noeud.name in ("tr", "td")):
            return noeud
        noeud = noeud.parent
    return lien.parent


def _transaction(href: str, sujet: str, texte: str, table: dict[str, str]) -> str | None:
    bas = href.lower()
    for cle, valeur in table.items():
        if cle in bas:
            return valeur
    return deviner_transaction(sujet) or deviner_transaction(texte)


def _prix(texte: str) -> int | None:
    m = re.search(r"(\d{1,3}(?:[ .  ]?\d{3})*)\s*€", texte)
    return nombre(m.group(1)) if m else None


def _chambres(texte: str) -> int | None:
    m = re.search(r"(\d+)\s*(?:ch(?:ambres?|\.)?|slaapkamers?|bedrooms?)\b", texte, re.I)
    return int(m.group(1)) if m else None


def _surface(texte: str) -> int | None:
    m = re.search(r"(\d{2,4})\s*m[²2]", texte)
    return int(m.group(1)) if m else None


def _lieu(texte: str) -> tuple[str | None, str | None]:
    m = re.search(r"\b(6[6-9]\d{2})\s+([A-Za-zÀ-ÿ'’ -]{3,40}?)(?=\s*(?:[,·|(\d-]|$))", texte)
    if m:
        return m.group(1), m.group(2).strip(" -")
    return None, None
