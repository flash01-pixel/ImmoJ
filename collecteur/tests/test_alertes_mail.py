"""Tests du lecteur d'alertes e-mail (sans IMAP : e-mails construits en mémoire)."""

from email.message import EmailMessage
from pathlib import Path

from immoj.sources.alertes_mail import analyser_email, analyser_html

FIXTURES = Path(__file__).parent / "fixtures" / "mails"


def email_html(expediteur: str, sujet: str, html: str) -> EmailMessage:
    m = EmailMessage()
    m["From"] = expediteur
    m["Subject"] = sujet
    m.set_content("version texte")
    m.add_alternative(html, subtype="html")
    return m


def test_alerte_immoweb_synthetique():
    html = (FIXTURES / "immoweb_alerte_synthetique.html").read_text(encoding="utf-8")
    msg = email_html("Immoweb <alerts@immoweb.be>", "3 nouvelles annonces à louer", html)
    annonces = analyser_email(msg)
    assert [a.id_externe for a in annonces] == ["21940001", "21940002", "21940003"]
    a = annonces[0]
    assert a.source == "immoweb"
    assert a.url == "https://www.immoweb.be/fr/annonce/appartement/a-louer/arlon/6700/21940001"
    assert a.transaction == "location" and a.type == "appartement"
    assert a.titre == "Appartement 2 chambres lumineux"
    assert a.prix == 850 and a.code_postal == "6700" and a.commune == "Arlon"
    assert a.chambres == 2 and a.surface == 85
    assert a.photos == ["https://media.immowebstatic.be/photo1.jpg"]
    b = annonces[1]
    assert b.prix == 1250 and b.commune == "Virton" and b.type == "maison"
    c = annonces[2]
    assert c.transaction == "achat" and c.prix == 195000 and c.chambres == 4


def test_expediteur_inconnu_ignore():
    msg = email_html("Promo <news@autre.be>", "Offre", "<a href='https://www.immoweb.be/fr/annonce/x/a-louer/y/6700/21940009'>x</a>")
    assert analyser_email(msg) == []


def test_zimmo_et_logicimmo():
    html = """
    <div><a href="https://www.zimmo.be/fr/arlon-6700/a-louer/appartement/L8Z3MQ/?src=mail">
    Appartement à louer</a> 790 € 6700 Arlon 1 chambre</div>
    """
    z = analyser_html(html, "zimmo", "Nouvelles annonces")
    assert len(z) == 1 and z[0].id_externe == "L8Z3MQ" and z[0].prix == 790
    assert z[0].url == "https://www.zimmo.be/fr/arlon-6700/a-louer/appartement/L8Z3MQ/"
    html = """
    <td><a href="https://www.logic-immo.be/fr/location/appartement/arlon/6700/abc-123-def?x=1">
    Studio meublé</a><br>650 € · 6700 Arlon</td>
    """
    li = analyser_html(html, "logicimmo", "Alerte Logic-immo : location")
    assert len(li) == 1 and li[0].id_externe == "abc-123-def" and li[0].type == "studio"
    assert li[0].transaction == "location"


def test_email_texte_seul():
    m = EmailMessage()
    m["From"] = "noreply@immoweb.be"
    m["Subject"] = "Nouvelle annonce à louer"
    m.set_content("Voir https://www.immoweb.be/fr/annonce/maison/a-louer/arlon/6700/21940010 920 €")
    # Sans HTML, il n'y a pas de balise <a> : rien à extraire, mais pas d'erreur.
    assert analyser_email(m) == []
