/* =========================================================================
   MCA Immobilier — comportements du site
   Aucune dépendance externe.
   ========================================================================= */
(function () {
  'use strict';

  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* -----------------------------------------------------------------------
     0. Langue
     Les pages néerlandaises (site/nl/, <html lang="nl">) gardent ce script :
     les textes qu'il affiche passent par tr(), qui renvoie la traduction
     néerlandaise quand elle existe, le français sinon. Les valeurs envoyées
     au serveur (options de la candidature…) restent en français.
     ----------------------------------------------------------------------- */
  var LANGUE = document.documentElement.lang === 'nl' ? 'nl' : 'fr';
  // Les pages NL vivent dans /nl/ : les images injectées par ce script
  // partent de la racine du site.
  var RACINE = LANGUE === 'nl' ? '/' : '';

  var VERTALING = {
    // menu
    'Acheter': 'Kopen',
    'Vendre': 'Verkopen',
    'Biens exclusifs': 'Exclusieve panden',
    'Louer': 'Huren',
    'Gestion locative': 'Huurbeheer',
    'Syndic': 'Syndicus',
    'Projets': 'Nieuwbouwprojecten',
    'Blog': 'Advies',
    'https://holidaysardenne.be/fr': 'https://holidaysardenne.be/nl',
    'https://proprietaires.maisons-chalets-ardennes.be/fr/': 'https://proprietaires.maisons-chalets-ardennes.be/nl/',
    'Maisons de vacances': 'Vakantiewoningen',
    'Menu principal': 'Hoofdmenu',
    'Navigation': 'Navigatie',
    'Fermer le menu': 'Menu sluiten',
    'Ouvrir le menu': 'Menu openen',
    'Nos agences': 'Onze kantoren',
    'Vente, location &amp; syndic': 'Verkoop, verhuur &amp; syndicus',
    'Liens utiles': 'Nuttige links',
    'Estimation gratuite': 'Gratis schatting',
    'Nous écrire': 'Schrijf ons',
    'Espace propriétaire': 'Eigenaarsportaal',
    // avis
    'Relancer le défilement des avis': 'Het scrollen van de reviews hervatten',
    'Mettre en pause le défilement des avis': 'Het scrollen van de reviews pauzeren',
    // candidature
    ' · réf. ': ' · ref. ',
    'Moins de 1000 €': 'Minder dan € 1.000',
    '1000 – 1200 €': '€ 1.000 – 1.200',
    '1200 – 1500 €': '€ 1.200 – 1.500',
    '1500 – 2000 €': '€ 1.500 – 2.000',
    '2000 – 2500 €': '€ 2.000 – 2.500',
    '2500 – 3000 €': '€ 2.500 – 3.000',
    'Plus de 3000 €': 'Meer dan € 3.000',
    'Fermer': 'Sluiten',
    'Location': 'Huur',
    'Postuler pour ce bien': 'Kandidaat stellen voor dit pand',
    'Deux minutes suffisent. Nous étudions votre dossier et revenons vers vous sous 48 h. Les visites s\'organisent après l\'étude du dossier.':
      'Twee minuten volstaan. We bekijken uw dossier en nemen binnen 48 uur contact met u op. Bezoeken worden ingepland nadat we uw dossier bekeken hebben.',
    'Prénom': 'Voornaam',
    'Nom': 'Naam',
    'Postuler avec une deuxième personne': 'Kandidaat stellen met een tweede persoon',
    'Prénom de la deuxième personne': 'Voornaam van de tweede persoon',
    'Nom de la deuxième personne': 'Naam van de tweede persoon',
    'Revenu net de la deuxième personne': 'Nettoinkomen van de tweede persoon',
    'Choisir…': 'Kiezen…',
    'Adresse e-mail': 'E-mailadres',
    'vous@exemple.be': 'u@voorbeeld.be',
    'Numéro de téléphone': 'Telefoonnummer',
    'Rue': 'Straat',
    'Numéro': 'Nummer',
    'Code postal': 'Postcode',
    'Ville': 'Gemeente',
    'Pays': 'Land',
    'Belgique': 'België',
    'Revenu net de la première personne': 'Nettoinkomen van de eerste persoon',
    'Composition du ménage': 'Gezinssamenstelling',
    'Adultes, vous compris': 'Volwassenen, uzelf inbegrepen',
    'Enfants': 'Kinderen',
    'Âge des enfants': 'Leeftijd van de kinderen',
    'Par exemple 4 et 9 ans': 'Bijvoorbeeld 4 en 9 jaar',
    'Garant possible': 'Borgsteller mogelijk',
    'Nombre d\'animaux': 'Aantal huisdieren',
    'Type d\'animaux': 'Soort huisdieren',
    'Chien, chat…': 'Hond, kat…',
    'Date d\'emménagement souhaitée': 'Gewenste verhuisdatum',
    'Votre remarque': 'Uw opmerking',
    'Vos disponibilités pour une visite, une question, une remarque…': 'Wanneer past een bezoek voor u, een vraag, een opmerking…',
    'Justificatifs (fiches de paie, carte d\'identité, référence du propriétaire actuel) : ':
      'Bewijsstukken (loonfiches, identiteitskaart, referentie van uw huidige verhuurder): ',
    'nous vous les demanderons par e-mail après l\'étude de votre candidature.':
      'die vragen we u per e-mail nadat we uw kandidatuur bekeken hebben.',
    'J\'ai bien lu les mentions légales et les conditions d\'utilisation.':
      'Ik heb de juridische kennisgeving en de gebruiksvoorwaarden gelezen.',
    'Envoyer mon message !': 'Mijn bericht verzenden',
    'Réponse sous 48 h, jours ouvrables': 'Antwoord binnen 48 uur, op werkdagen',
    'Candidature bien reçue, merci.': 'Kandidatuur goed ontvangen, bedankt.',
    'Nous étudions votre dossier et vous recontactons sous 48 h.': 'We bekijken uw dossier en nemen binnen 48 uur contact met u op.',
    // formulaires
    'Envoi en cours…': 'Bezig met verzenden…',
    'Merci, nous vous recontactons': 'Bedankt, we nemen contact met u op',
    'Votre demande est bien partie. Nous revenons vers vous sous 48 h.': 'Uw aanvraag is verstuurd. We nemen binnen 48 uur contact met u op.',
    'L\'envoi n\'a pas abouti. Réessayez dans un instant ou appelez-nous au ': 'Het verzenden is niet gelukt. Probeer het zo dadelijk opnieuw of bel ons op ',
    'Connexion impossible. Vérifiez votre réseau ou appelez-nous au ': 'Geen verbinding. Controleer uw netwerk of bel ons op ',
    // photos
    'Photo du bien': 'Foto van het pand',
    'Photos du bien': 'Foto\'s van het pand',
    'Photo précédente': 'Vorige foto',
    'Photo suivante': 'Volgende foto',
    ' · photo ': ' · foto ',
    ' sur ': ' van ',
    // newsletter
    'Avant tout le monde': 'Als eerste op de hoogte',
    'Les nouveaux biens, une fois par mois': 'De nieuwe panden, één keer per maand',
    'Un seul e-mail par mois, avec ce qui est rentré chez nous en Famenne-Ardenne.': 'Eén e-mail per maand, met alles wat er bij ons binnenkwam in de Famenne-Ardennen.',
    'Tous les biens rentrés le mois écoulé, réunis dans un e-mail.': 'Alle panden van de voorbije maand, samen in één e-mail.',
    'Nos ventes exclusives, celles que nous ne publions pas sur le site.': 'Onze exclusieve verkopen, die we niet op de website publiceren.',
    'Votre e-mail': 'Uw e-mail',
    'Je veux les recevoir': 'Ik wil ze ontvangen',
    'Non merci, pas maintenant': 'Nee, bedankt, nu niet',
    'Un e-mail par mois, jamais plus. Désinscription en un clic. ': 'Eén e-mail per maand, nooit meer. Uitschrijven met één klik. ',
    'Nous ne communiquons votre adresse à personne.': 'We geven uw adres aan niemand door.',
    'C\'est noté, merci.': 'Genoteerd, bedankt.',
    'Vous recevrez la prochaine sélection dans votre boîte.': 'U ontvangt de volgende selectie in uw mailbox.',
    // cookies
    'Un mot sur les cookies': 'Even over cookies',
    'Nous utilisons des cookies nécessaires au fonctionnement du site et, avec votre accord, ':
      'We gebruiken cookies die nodig zijn voor de werking van de website en, met uw toestemming, ',
    'des cookies de mesure d\'audience et de personnalisation. ': 'cookies voor bezoekersstatistieken en personalisatie. ',
    'Politique de cookies': 'Cookiebeleid',
    'Vie privée': 'Privacy',
    'Nécessaires': 'Noodzakelijk',
    'Toujours actifs : fonctionnement du site, mémorisation de vos choix.': 'Altijd actief: werking van de website, onthouden van uw keuzes.',
    'Statistiques': 'Statistieken',
    'Mesure d\'audience anonyme pour améliorer le site.': 'Anonieme bezoekersstatistieken om de website te verbeteren.',
    'Personnalisation, réseaux sociaux et publicité ciblée.': 'Personalisatie, sociale netwerken en gerichte reclame.',
    'Tout accepter': 'Alles accepteren',
    'Refuser': 'Weigeren',
    'Personnaliser': 'Aanpassen',
    'Enregistrer mes choix': 'Mijn keuzes opslaan',
    // partage
    'Lien copié': 'Link gekopieerd',
    'Copie impossible': 'Kopiëren mislukt'
  };

  function tr(s) {
    return LANGUE === 'nl' && Object.prototype.hasOwnProperty.call(VERTALING, s) ? VERTALING[s] : s;
  }

  /* -----------------------------------------------------------------------
     1. Jeu d'icônes (sprite SVG injecté une fois)
     ----------------------------------------------------------------------- */
  var ICONS = {
    'chevron-down': '<path d="m6 9 6 6 6-6"/>',
    'chevron-right': '<path d="m9 18 6-6-6-6"/>',
    'phone': '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"/>',
    'phone-call': '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.79 19.79 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.91.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92Z"/><path d="M14.05 2a9 9 0 0 1 7.94 7.94M14.05 6A5 5 0 0 1 18 9.95"/>',
    'menu': '<path d="M4 6h16M4 12h16M4 18h16"/>',
    'x': '<path d="M18 6 6 18M6 6l12 12"/>',
    'arrow-left': '<path d="M19 12H5m7 7-7-7 7-7"/>',
    'arrow-right': '<path d="M5 12h14m-7-7 7 7-7 7"/>',
    'map-pin': '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
    'map': '<path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z"/><path d="M9 3v15M15 6v15"/>',
    'images': '<rect x="6" y="2" width="16" height="16" rx="2"/><path d="M18 22H4a2 2 0 0 1-2-2V6"/><circle cx="12" cy="8" r="2"/><path d="m22 14-3.5-3.5a2 2 0 0 0-3 0L10 18"/>',
    'bed-double': '<path d="M2 20v-8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8M4 10V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v4M12 4v6M2 18h20"/>',
    'bath': '<path d="M4 12V5.5a2 2 0 0 1 4 0V6M6 6h2M3 12h18v3a5 5 0 0 1-5 5H8a5 5 0 0 1-5-5v-3ZM7 20v2M17 20v2"/>',
    'car': '<path d="M19 17h2v-4l-2-5.5A2 2 0 0 0 17.1 6H6.9a2 2 0 0 0-1.9 1.5L3 13v4h2M5 17h14"/><circle cx="7.5" cy="17" r="1.5"/><circle cx="16.5" cy="17" r="1.5"/>',
    'ruler': '<path d="M21.3 8.7 8.7 21.3a1 1 0 0 1-1.4 0l-4.6-4.6a1 1 0 0 1 0-1.4L15.3 2.7a1 1 0 0 1 1.4 0l4.6 4.6a1 1 0 0 1 0 1.4Z"/><path d="m7.5 10.5 2 2M10.5 7.5l2 2M13.5 4.5l2 2M4.5 13.5l2 2"/>',
    'trees': '<path d="M12 22v-4M7 18h10l-5-7-5 7ZM9 12h6l-3-5-3 5Z"/>',
    'droplets': '<path d="M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05Z"/><path d="M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97"/>',
    'gauge': '<path d="m12 14 4-4M3.34 19a10 10 0 1 1 17.32 0"/>',
    'package': '<path d="m7.5 4.27 9 5.15M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5M12 22V12"/>',
    'tag': '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r=".5" fill="currentColor"/>',
    'calendar': '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
    'calendar-check': '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18m-9 5 2 2 4-4"/>',
    'flame': '<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5S5 13 5 15a7 7 0 0 0 7 7Z"/>',
    'sun': '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    'zap': '<path d="M13 2 3 14h9l-1 8 10-12h-9l1-8Z"/>',
    'home': '<path d="m3 10 9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V10Z"/><path d="M9 22V12h6v10"/>',
    'utensils': '<path d="M3 2v7a3 3 0 0 0 3 3v10M6 2v7M9 2v7a3 3 0 0 1-3 3M17 2c-1.5 2-2 4.2-2 6.2 0 1.6.6 2.6 2 3.1V22"/>',
    'square': '<rect x="3" y="3" width="18" height="18" rx="2"/>',
    'file-text': '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6M8 13h8M8 17h6"/>',
    'download': '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    'mail': '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m2 7 10 6 10-6"/>',
    'clock': '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    'key-round': '<circle cx="16" cy="8" r="5"/><path d="m12.3 11.7-8.6 8.6a1 1 0 0 0-.3.7V22h2.6l1.4-1.4V19h1.8v-1.8h1.8l2.3-2.3"/>',
    'award': '<circle cx="12" cy="9" r="6"/><path d="m8.2 14-1.7 7L12 18l5.5 3-1.7-7"/>',
    'users': '<path d="M17 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/>',
    'languages': '<path d="M4 5h10M9 3v2c0 5-2.5 8-6 9M6 10c0 3 2.5 5.5 7 6M14 21l4.5-11 4.5 11M16 17.5h5"/>',
    'eye': '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
    'flag': '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V4s-1 1-4 1-5-2-8-2-4 1-4 1ZM4 22v-7"/>',
    'building-2': '<path d="M6 22V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v18M14 9h4a2 2 0 0 1 2 2v11M2 22h20M9 6h2M9 10h2M9 14h2M17 13h1M17 17h1"/>',
    'bike': '<circle cx="5.5" cy="17.5" r="3.5"/><circle cx="18.5" cy="17.5" r="3.5"/><circle cx="15" cy="5" r="1"/><path d="M12 17.5V14l-3-3 4-3 2 3h2"/>',
    'palette': '<path d="M12 22a10 10 0 1 1 10-10c0 1.7-1.3 3-3 3h-2a2 2 0 0 0-1.4 3.4 2 2 0 0 1-1.4 3.4H12Z"/><circle cx="8" cy="9.5" r="1"/><circle cx="12" cy="7.5" r="1"/><circle cx="16" cy="9.5" r="1"/>',
    'trending-up': '<path d="m22 7-8.5 8.5-5-5L2 17M16 7h6v6"/>',
    'check': '<path d="M20 6 9 17l-5-5"/>',
    'search': '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    'shield-check': '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="m9 12 2 2 4-4"/>',
    'link': '<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7"/>',
    'share-2': '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
    'facebook': '<path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3Z"/>',
    'linkedin': '<path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-4 0v7h-4v-7a6 6 0 0 1 6-6Z"/><rect x="2" y="9" width="4" height="12"/><circle cx="4" cy="4" r="2"/>',
    'list': '<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>',
    'lightbulb': '<path d="M9 18h6M10 22h4"/><path d="M12 2a6 6 0 0 0-3.5 10.9c.6.5.9 1.2 1 1.9l.1 1.2h4.8l.1-1.2c.1-.7.4-1.4 1-1.9A6 6 0 0 0 12 2Z"/>',
    'info': '<circle cx="12" cy="12" r="9"/><path d="M12 16v-4M12 8h.01"/>',
    'arrow-up-right': '<path d="M7 17 17 7M8 7h9v9"/>',
    'paperclip': '<path d="m21 11.5-8.5 8.5a5.5 5.5 0 0 1-7.8-7.8l8.5-8.5a3.5 3.5 0 0 1 5 5l-8.5 8.5a1.5 1.5 0 0 1-2.1-2.1L15.5 7"/>',
    'pause': '<path d="M9 5v14M15 5v14"/>',
    'play': '<path d="M7 4.5v15l12-7.5-12-7.5Z"/>'
  };

  function paintIcons(root) {
    (root || document).querySelectorAll('.ico[data-i]').forEach(function (el) {
      if (el.firstElementChild) return;
      var d = ICONS[el.getAttribute('data-i')];
      if (!d) return;
      el.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + d + '</svg>';
    });
  }

  /* -----------------------------------------------------------------------
     2. Navigation collante
     ----------------------------------------------------------------------- */
  function initNav() {
    var nav = document.querySelector('.site-nav');
    if (!nav) return;
    var onScroll = function () {
      nav.classList.toggle('is-stuck', window.scrollY > 40);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* -----------------------------------------------------------------------
     3. Menu « fixed underlay »
     La page (.shell) glisse et découvre le menu fixe posé dessous.
     ----------------------------------------------------------------------- */
  // [libellé, cible, externe ?]
  var LIENS = [
    ['Acheter',          'acheter.html'],
    ['Vendre',           'vendre.html'],
    ['Biens exclusifs',  'acheter.html?exclusifs=1'],
    ['Louer',            'louer.html'],
    ['Gestion locative', 'gestion-locative.html'],
    ['Syndic',           'syndic.html'],
    ['Projets',          'projets.html'],
    ['Blog',             'blog.html'],
    ['Holidays Ardenne', 'https://holidaysardenne.be/fr', true],
    ['Contact',          'contact.html']
  ];

  function initMenu() {
    var shell = document.querySelector('.shell');
    var bascules = document.querySelectorAll('[data-menu-toggle]');
    if (!shell || !bascules.length) return;

    var page = (location.pathname.split('/').pop() || 'index.html');
    var courante = document.querySelector('.site-nav__links .nav-link[aria-current="page"]');
    // Les pages de détail (article, fiche bien) n'ont pas de lien dans la barre :
    // elles déclarent leur rubrique via data-nav sur <body> (en français, même
    // sur les pages néerlandaises).
    var actif = (courante ? courante.textContent.trim() : '') ||
                (document.body.getAttribute('data-nav') || '');
    var hrefCourant = courante ? courante.getAttribute('href') : '';

    var menu = document.createElement('nav');
    menu.className = 'underlay';
    menu.id = 'menu-underlay';
    menu.setAttribute('aria-label', tr('Menu principal'));
    var html =
      '<div class="underlay__top">' +
        '<span class="underlay__kicker">' + tr('Navigation') + '</span>' +
        '<button class="underlay__close" data-menu-close aria-label="' + tr('Fermer le menu') + '">' +
          '<span class="ico" data-i="x"></span></button>' +
      '</div><div class="underlay__nav">';
    LIENS.forEach(function (l) {
      var ici = (l[0] === actif) || (tr(l[0]) === actif) || (hrefCourant && l[1] === hrefCourant) ||
                (!actif && l[1] === page);
      if (l[2]) {
        html += '<a class="underlay__link underlay__link--ext" href="' + tr(l[1]) + '" target="_blank" rel="noopener">' +
                l[0] + '<span class="ico" data-i="arrow-up-right"></span>' +
                '<small>' + tr('Maisons de vacances') + '</small></a>';
        return;
      }
      html += '<a class="underlay__link" href="' + l[1] + '"' +
              (ici ? ' aria-current="page"' : '') + '>' + tr(l[0]) + '</a>';
    });
    // Changement de langue, si la page en propose un dans sa barre.
    var bascule = document.querySelector('.site-nav__lang [data-lang-switch]');
    var autre = bascule ? bascule.getAttribute('data-lang-switch') : '';
    var lienLangue = autre === 'nl'
      ? '<a href="/nl/" data-lang-switch="nl" hreflang="nl" lang="nl">Nederlands</a>'
      : autre === 'fr' ? '<a href="/" data-lang-switch="fr" hreflang="fr" lang="fr">Français</a>' : '';
    html += '</div>' +
      '<div class="underlay__foot">' +
        '<div><h3>' + tr('Nos agences') + '</h3>' +
          '<a href="tel:+3286323676"><b>Durbuy</b>086 32 36 76</a>' +
          '<a href="contact.html" style="margin-top:10px"><b>Marche-en-Famenne</b>' + tr('Vente, location &amp; syndic') + '</a>' +
        '</div>' +
        '<div><h3>' + tr('Liens utiles') + '</h3>' +
          '<a href="vendre.html#estimation">' + tr('Estimation gratuite') + '</a>' +
          '<a href="contact.html">' + tr('Nous écrire') + '</a>' +
          '<a href="' + tr('https://proprietaires.maisons-chalets-ardennes.be/fr/') + '" target="_blank" rel="noopener">' + tr('Espace propriétaire') + '</a>' +
          lienLangue +
        '</div>' +
      '</div>';
    menu.innerHTML = html;
    document.body.appendChild(menu);
    paintIcons(menu);

    var ouvert = false, focusPrecedent = null;

    function largeurBarre() {
      return window.innerWidth - document.documentElement.clientWidth;
    }

    function basculer(etat) {
      if (etat === ouvert) return;
      ouvert = etat;
      if (etat) {
        focusPrecedent = document.activeElement;
        var b = largeurBarre();
        if (b > 0) document.body.style.paddingRight = b + 'px';
      } else {
        document.body.style.paddingRight = '';
      }
      document.body.classList.toggle('menu-open', etat);
      bascules.forEach(function (t) {
        t.setAttribute('aria-expanded', etat ? 'true' : 'false');
        t.setAttribute('aria-label', tr(etat ? 'Fermer le menu' : 'Ouvrir le menu'));
      });
      if (etat) {
        var premier = menu.querySelector('.underlay__link');
        if (premier) setTimeout(function () { premier.focus(); }, 320);
      } else if (focusPrecedent && focusPrecedent.focus) {
        focusPrecedent.focus();
      }
    }

    bascules.forEach(function (t) {
      t.addEventListener('click', function () { basculer(!ouvert); });
    });
    menu.querySelectorAll('[data-menu-close]').forEach(function (b) {
      b.addEventListener('click', function () { basculer(false); });
    });
    // cliquer la page découverte referme
    shell.addEventListener('click', function (e) {
      if (!ouvert) return;
      if (e.target.closest('[data-menu-toggle]')) return;
      e.preventDefault();
      basculer(false);
    }, true);

    document.addEventListener('keydown', function (e) {
      if (!ouvert) return;
      if (e.key === 'Escape') { basculer(false); return; }
      if (e.key !== 'Tab') return;
      var cibles = menu.querySelectorAll('a[href], button');
      if (!cibles.length) return;
      var premier = cibles[0], dernier = cibles[cibles.length - 1];
      if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
      else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
    });
  }

  /* -----------------------------------------------------------------------
     4. Apparition au défilement
     ----------------------------------------------------------------------- */
  var observateur = null;

  /* Contenu injecté après coup (biens Omnicasa) : même apparition que le
     contenu d'origine, décalage plafonné pour ne pas faire attendre. */
  function reveler(root) {
    var items = (root || document).querySelectorAll('[data-reveal]:not(.is-in)');
    if (!items.length) return;
    if (reduced || !observateur) {
      items.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }
    var rangs = [];
    items.forEach(function (el) {
      var g = el.parentElement;
      if (g && g.hasAttribute('data-reveal-group')) {
        var step = parseInt(g.getAttribute('data-reveal-group'), 10) || 90;
        var n = rangs.filter(function (x) { return x === g; }).length;
        rangs.push(g);
        el.style.transitionDelay = (Math.min(n, 6) * step) + 'ms';
      }
      observateur.observe(el);
    });
    if (window.__mcaSweep) requestAnimationFrame(window.__mcaSweep);
  }

  function initReveal() {
    var items = document.querySelectorAll('[data-reveal]');
    if (!items.length) return;

    if (reduced || !('IntersectionObserver' in window)) {
      items.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }

    // Décalage automatique des enfants d'un groupe
    document.querySelectorAll('[data-reveal-group]').forEach(function (group) {
      var step = parseInt(group.getAttribute('data-reveal-group'), 10) || 90;
      group.querySelectorAll(':scope > [data-reveal]').forEach(function (child, i) {
        child.style.transitionDelay = (i * step) + 'ms';
      });
    });

    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-in');
        io.unobserve(entry.target);
      });
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0.08 });
    observateur = io;

    items.forEach(function (el) { io.observe(el); });

    // Filet de sécurité permanent.
    // L'observateur reste le chemin normal, mais il peut ne jamais se déclencher
    // (onglet en arrière-plan, compositeur en pause, rendu différé). Du contenu
    // porteur — les chiffres clés, la carte d'estimation — resterait alors
    // invisible ou décalé. On balaie donc aussi au défilement : c'est peu coûteux
    // puisqu'on n'interroge que les éléments pas encore révélés, et l'écouteur
    // se retire de lui-même une fois tout révélé.
    var raf = null;
    function sweep() {
      raf = null;
      var rest = document.querySelectorAll('[data-reveal]:not(.is-in)');
      if (!rest.length) {
        window.removeEventListener('scroll', onScroll);
        window.removeEventListener('resize', onScroll);
        return;
      }
      var vh = window.innerHeight || 0;
      if (!vh) {
        // Hauteur de fenêtre inconnue ou nulle : on ne peut pas décider quoi est
        // à l'écran. Dans le doute on montre, jamais l'inverse : du contenu
        // masqué est un défaut bien plus grave qu'une animation sautée.
        rest.forEach(function (el) { el.classList.add('is-in'); });
        return;
      }
      rest.forEach(function (el) {
        var r = el.getBoundingClientRect();
        if (r.top < vh * 0.92 && r.bottom > 0) el.classList.add('is-in');
      });
    }
    function onScroll() { if (!raf) raf = requestAnimationFrame(sweep); }
    window.__mcaSweep = sweep;   // utilisé au changement de page
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    window.addEventListener('load', function () { setTimeout(sweep, 300); });
    setTimeout(sweep, 1200);
  }

  /* -----------------------------------------------------------------------
     5. Parallaxe légère
     ----------------------------------------------------------------------- */
  function initParallax() {
    var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-px]'));
    if (!nodes.length || reduced) return;
    if (window.matchMedia('(max-width: 780px)').matches) return;

    var raf = null;
    function tick() {
      raf = null;
      var vh = window.innerHeight;
      nodes.forEach(function (el) {
        var sp = parseFloat(el.getAttribute('data-px')) || 0;
        var r = el.getBoundingClientRect();
        if (r.bottom < -400 || r.top > vh + 400) return;
        var mid = r.top + r.height / 2 - vh / 2;
        el.style.transform = 'translate3d(0,' + (-mid * sp).toFixed(1) + 'px,0)';
      });
    }
    function onScroll() { if (!raf) raf = requestAnimationFrame(tick); }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    tick();
    setTimeout(tick, 600);
  }

  /* -----------------------------------------------------------------------
     6. Compteurs
     ----------------------------------------------------------------------- */
  /* Cartes numérotées : l'escalier se résorbe à mesure qu'on défile.
     --p passe de 1 (décalé) à 0 (aligné). */
  function initCards() {
    var grilles = Array.prototype.slice.call(document.querySelectorAll('[data-cards]'));
    if (!grilles.length || reduced) return;

    var raf = null;
    function tick() {
      raf = null;
      var vh = window.innerHeight || 0;
      grilles.forEach(function (g) {
        var pa;
        if (!vh) {
          // hauteur inconnue : on affiche l'état d'arrivée, jamais l'état masqué
          pa = 1;
        } else {
          var r = g.getBoundingClientRect();
          // course : du haut de grille à 95 % de l'écran jusqu'à 17 %
          pa = (vh * 0.95 - r.top) / (vh * 0.78);
          pa = pa < 0 ? 0 : (pa > 1 ? 1 : pa);
          // lissage symétrique : le mouvement est le plus lisible à mi-course
          pa = pa * pa * (3 - 2 * pa);
        }
        g.style.setProperty('--p', (1 - pa).toFixed(3));
      });
    }
    function onScroll() { if (!raf) raf = requestAnimationFrame(tick); }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    window.__mcaCards = tick;   // recalcul immédiat après un changement de mise en page
    tick();
    setTimeout(tick, 600);
  }

  function initCounters() {
    var els = document.querySelectorAll('[data-count]');
    if (!els.length) return;
    if (reduced || !('IntersectionObserver' in window)) {
      els.forEach(function (el) { el.textContent = el.getAttribute('data-count'); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var el = entry.target;
        io.unobserve(el);
        var target = el.getAttribute('data-count');
        var sep = target.indexOf('.') > -1 ? '.' : '';
        var end = parseInt(target.replace(/\D/g, ''), 10);
        if (isNaN(end)) { el.textContent = target; return; }
        var pad = target.replace(/[^\d]/g, '').length;
        var t0 = performance.now();
        var dur = 1400;
        function frame(now) {
          var p = Math.min(1, (now - t0) / dur);
          var eased = 1 - Math.pow(1 - p, 3);
          var val = Math.round(end * eased);
          var s = String(val);
          if (pad > 1 && target[0] === '0') { while (s.length < pad) s = '0' + s; }
          if (sep && s.length > 3) s = s.slice(0, s.length - 3) + '.' + s.slice(s.length - 3);
          el.textContent = s;
          if (p < 1) requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
      });
    }, { threshold: 0.4 });
    els.forEach(function (el) { io.observe(el); });
  }

  /* -----------------------------------------------------------------------
     6b. Compteur « en direct »
     Le nombre monte d'abord jusqu'à sa valeur (comme data-count), puis
     continue d'augmenter par petits pas à intervalles irréguliers, pour
     donner l'impression d'un fichier qui grossit en temps réel.
     ----------------------------------------------------------------------- */
  function formatMilliers(n) {
    var str = String(n);
    return str.length > 3 ? str.slice(0, str.length - 3) + '.' + str.slice(str.length - 3) : str;
  }

  function initLive() {
    var els = document.querySelectorAll('[data-live]');
    if (!els.length) return;

    els.forEach(function (el) {
      var base = parseInt(el.getAttribute('data-live'), 10);
      if (isNaN(base)) return;
      var valeur = base;
      el.textContent = formatMilliers(valeur);

      function ticGoutte() {
        valeur += 1 + Math.floor(Math.random() * 2);       // +1 ou +2
        el.textContent = formatMilliers(valeur);
        if (!reduced) {
          el.classList.remove('is-tick');
          void el.offsetWidth;                              // relance l'animation
          el.classList.add('is-tick');
        }
        setTimeout(ticGoutte, 3500 + Math.random() * 5500); // toutes les 3,5 à 9 s
      }

      function demarrer() {
        if (reduced) { setTimeout(ticGoutte, 4000); return; }
        var depart = Math.max(0, base - 180), t0 = performance.now(), dur = 1500;
        function frame(now) {
          var p = Math.min(1, (now - t0) / dur);
          var eased = 1 - Math.pow(1 - p, 3);
          valeur = Math.round(depart + (base - depart) * eased);
          el.textContent = formatMilliers(valeur);
          if (p < 1) requestAnimationFrame(frame);
          else setTimeout(ticGoutte, 2500 + Math.random() * 3000);
        }
        requestAnimationFrame(frame);
      }

      if (!('IntersectionObserver' in window)) { demarrer(); return; }
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          io.unobserve(el);
          demarrer();
        });
      }, { threshold: 0.4 });
      io.observe(el);
    });
  }

  /* -----------------------------------------------------------------------
     Avis Google : défilement continu
     La liste est doublée (copie cachée aux lecteurs d'écran) pour boucler
     sans à-coup ; la durée suit la longueur, la vitesse reste la même.
     Sans JavaScript ou en mouvement réduit, la rangée défile au doigt.
     ----------------------------------------------------------------------- */
  function initAvis() {
    document.querySelectorAll('[data-avis]').forEach(function (zone) {
      var piste = zone.querySelector('.avis__piste');
      var groupe = piste && piste.querySelector('.avis__groupe');
      var bouton = document.querySelector('[data-avis-pause="' + zone.id + '"]');
      if (!groupe || reduced) { if (bouton) bouton.hidden = true; return; }

      var copie = groupe.cloneNode(true);
      copie.setAttribute('aria-hidden', 'true');
      piste.appendChild(copie);

      function duree() { piste.style.animationDuration = Math.round(groupe.offsetWidth / 42) + 's'; }
      duree();
      window.addEventListener('resize', duree);
      zone.classList.add('is-anime');

      if (!bouton) return;
      bouton.hidden = false;
      bouton.addEventListener('click', function () {
        var pause = zone.classList.toggle('is-pause');
        bouton.setAttribute('aria-label', tr(pause ? 'Relancer le défilement des avis' : 'Mettre en pause le défilement des avis'));
        var ico = bouton.querySelector('.ico');
        ico.innerHTML = '';
        ico.setAttribute('data-i', pause ? 'play' : 'pause');
        paintIcons(bouton);
      });
    });
  }

  /* -----------------------------------------------------------------------
     6c. Candidature locative — « Postuler pour ce bien »
     Une fenêtre modale construite à la demande sur les fiches de biens à
     louer. Tout bouton [data-postuler] l'ouvre. La candidature part vers
     /api/contact (voir 9 bis).
     ----------------------------------------------------------------------- */
  function construireCandidature() {
    var body = document.body;
    var bien = body.getAttribute('data-bien') || '';
    var ref = body.getAttribute('data-ref') || '';
    var sousTitre = ref ? tr(' · réf. ') + ref : '';

    function champ(label, inner) {
      return '<label class="field"><span class="field__label">' + tr(label) + '</span>' + inner + '</label>';
    }
    // La valeur envoyée reste le libellé français, seul le texte affiché change.
    function options(liste) {
      return liste.map(function (o) { return '<option value="' + o + '">' + tr(o) + '</option>'; }).join('');
    }
    var revenus = ['Moins de 1000 €', '1000 – 1200 €', '1200 – 1500 €', '1500 – 2000 €',
                   '2000 – 2500 €', '2500 – 3000 €', 'Plus de 3000 €'];

    var el = document.createElement('div');
    el.className = 'apop';
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-modal', 'true');
    el.setAttribute('aria-labelledby', 'apop-titre');
    el.innerHTML =
      '<div class="apop__scrim" data-apop-close></div>' +
      '<div class="apop__card">' +
        '<div class="apop__head">' +
          '<button type="button" class="apop__close" data-apop-close aria-label="' + tr('Fermer') + '">' +
            '<span class="ico" data-i="x"></span></button>' +
          '<span class="kicker">' + tr('Location') + sousTitre + '</span>' +
          '<h2 id="apop-titre">' + tr('Postuler pour ce bien') + '</h2>' +
          '<p>' + (bien ? '<b>' + bien + '</b><br>' : '') +
            tr('Deux minutes suffisent. Nous étudions votre dossier et revenons vers vous sous 48 h. Les visites s\'organisent après l\'étude du dossier.') + '</p>' +
        '</div>' +
        '<form class="apop__form" novalidate>' +
          '<div class="apop__grid apop__grid--2">' +
            champ('Prénom', '<input class="input" type="text" name="prenom" autocomplete="given-name" required>') +
            champ('Nom', '<input class="input" type="text" name="nom" autocomplete="family-name" required>') +
          '</div>' +
          '<label class="check"><input type="checkbox" name="deuxieme" data-apop-reveal="apop-p2">' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            tr('Postuler avec une deuxième personne') + '</label>' +
          '<div class="apop__grid apop__grid--2 apop__extra" id="apop-p2" hidden>' +
            champ('Prénom de la deuxième personne', '<input class="input" type="text" name="prenom2">') +
            champ('Nom de la deuxième personne', '<input class="input" type="text" name="nom2">') +
            champ('Revenu net de la deuxième personne', '<select class="select" name="revenu2"><option value="">' + tr('Choisir…') + '</option>' + options(revenus) + '</select>') +
          '</div>' +
          '<div class="apop__grid apop__grid--2">' +
            champ('Adresse e-mail', '<input class="input" type="email" name="email" autocomplete="email" placeholder="' + tr('vous@exemple.be') + '" required>') +
            champ('Numéro de téléphone', '<input class="input" type="tel" name="tel" autocomplete="tel" placeholder="04XX XX XX XX" required>') +
          '</div>' +
          '<div class="apop__grid apop__grid--rue">' +
            champ('Rue', '<input class="input" type="text" name="rue" autocomplete="address-line1" required>') +
            champ('Numéro', '<input class="input" type="text" name="numero" required>') +
          '</div>' +
          '<div class="apop__grid apop__grid--3">' +
            champ('Code postal', '<input class="input" type="text" name="cp" autocomplete="postal-code" inputmode="numeric" required>') +
            champ('Ville', '<input class="input" type="text" name="ville" autocomplete="address-level2" required>') +
            champ('Pays', '<input class="input" type="text" name="pays" autocomplete="country-name" value="' + tr('Belgique') + '" required>') +
          '</div>' +
          champ('Revenu net de la première personne', '<select class="select" name="revenu" required><option value="">' + tr('Choisir…') + '</option>' + options(revenus) + '</select>') +
          '<fieldset class="apop__groupe"><legend class="field__label">' + tr('Composition du ménage') + '</legend>' +
            '<div class="apop__grid apop__grid--3">' +
              champ('Adultes, vous compris', '<input class="input" type="number" name="adultes" min="1" max="20" value="1" required data-apop-adultes>') +
              champ('Enfants', '<input class="input" type="number" name="enfants" min="0" max="20" value="0" required data-apop-enfants>') +
              champ('Âge des enfants', '<input class="input" type="text" name="ages_enfants" maxlength="80" placeholder="' + tr('Par exemple 4 et 9 ans') + '" disabled data-apop-ages>') +
            '</div>' +
          '</fieldset>' +
          '<label class="check"><input type="checkbox" name="garant">' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            tr('Garant possible') + '</label>' +
          '<div class="apop__grid apop__grid--2">' +
            champ('Nombre d\'animaux', '<input class="input" type="number" name="animaux" min="0" max="20" value="0" data-apop-animaux>') +
            champ('Type d\'animaux', '<input class="input" type="text" name="animaux_type" placeholder="' + tr('Chien, chat…') + '" disabled data-apop-animaux-type>') +
          '</div>' +
          champ('Date d\'emménagement souhaitée', '<input class="input" type="date" name="date" required>') +
          '<label class="field"><span class="visually-hidden">' + tr('Votre remarque') + '</span>' +
            '<textarea class="textarea" name="remarque" rows="4" placeholder="' + tr('Vos disponibilités pour une visite, une question, une remarque…') + '"></textarea></label>' +
          '<p class="field__hint" style="margin:0"><span class="ico ico--sm" data-i="paperclip"></span> ' +
            tr('Justificatifs (fiches de paie, carte d\'identité, référence du propriétaire actuel) : ') +
            tr('nous vous les demanderons par e-mail après l\'étude de votre candidature.') + '</p>' +
          '<label class="hp" aria-hidden="true">Société<input type="text" name="societe" tabindex="-1" autocomplete="off"></label>' +
          '<label class="check"><input type="checkbox" name="legal" required>' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            tr('J\'ai bien lu les mentions légales et les conditions d\'utilisation.') + '</label>' +
          '<p class="form-status" role="status" aria-live="polite" hidden></p>' +
          '<div class="apop__actions">' +
            '<button type="submit" class="btn btn--primary btn--lg">' + tr('Envoyer mon message !') + '</button>' +
            '<span class="muted" style="font-size:16px">' + tr('Réponse sous 48 h, jours ouvrables') + '</span>' +
          '</div>' +
        '</form>' +
        '<div class="apop__done">' +
          '<span class="ico" data-i="shield-check"></span>' +
          '<h2>' + tr('Candidature bien reçue, merci.') + '</h2>' +
          '<p style="margin-top:10px">' + tr('Nous étudions votre dossier et vous recontactons sous 48 h.') + '</p>' +
        '</div>' +
      '</div>';
    document.body.appendChild(el);
    paintIcons(el);
    return el;
  }

  function initPostuler() {
    var boutons = document.querySelectorAll('[data-postuler]');
    if (!boutons.length) return;

    var pop = null, ouverte = false, focusPrecedent = null;

    function fermer() {
      if (!pop || !ouverte) return;
      ouverte = false;
      pop.classList.remove('is-open');
      document.body.style.overflow = '';
      if (focusPrecedent && focusPrecedent.focus) focusPrecedent.focus();
    }

    function piegerTabulation(e) {
      var cibles = pop.querySelectorAll('button:not([disabled]), input:not([disabled]), select, textarea, a[href]');
      if (!cibles.length) return;
      var premier = cibles[0], dernier = cibles[cibles.length - 1];
      if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
      else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
    }

    function construire() {
      pop = construireCandidature();
      pop.querySelectorAll('[data-apop-close]').forEach(function (b) {
        b.addEventListener('click', fermer);
      });

      // deuxième personne
      pop.querySelectorAll('[data-apop-reveal]').forEach(function (cb) {
        var cible = pop.querySelector('#' + cb.getAttribute('data-apop-reveal'));
        if (!cible) return;
        cb.addEventListener('change', function () {
          cible.hidden = !cb.checked;
          if (cb.checked) {
            var premier = cible.querySelector('input, select');
            if (premier) premier.focus();
          }
        });
      });

      // deuxième personne : le ménage compte au moins deux adultes
      var adultes = pop.querySelector('[data-apop-adultes]');
      var deuxieme = pop.querySelector('input[name="deuxieme"]');
      if (adultes && deuxieme) {
        deuxieme.addEventListener('change', function () {
          if (deuxieme.checked && !(parseInt(adultes.value, 10) >= 2)) adultes.value = 2;
        });
      }

      // âge des enfants : actif seulement s'il y en a
      var enfants = pop.querySelector('[data-apop-enfants]');
      var ages = pop.querySelector('[data-apop-ages]');
      if (enfants && ages) {
        var majEnfants = function () {
          var n = parseInt(enfants.value, 10);
          var actif = !isNaN(n) && n > 0;
          ages.disabled = !actif;
          ages.required = actif;
          if (!actif) ages.value = '';
        };
        enfants.addEventListener('input', majEnfants);
        majEnfants();
      }

      // type d'animaux : actif seulement s'il y en a
      var nb = pop.querySelector('[data-apop-animaux]');
      var type = pop.querySelector('[data-apop-animaux-type]');
      if (nb && type) {
        var majAnimaux = function () {
          var n = parseInt(nb.value, 10);
          var actif = !isNaN(n) && n > 0;
          type.disabled = !actif;
          type.required = actif;
          if (!actif) type.value = '';
        };
        nb.addEventListener('input', majAnimaux);
        majAnimaux();
      }

      var form = pop.querySelector('form');
      var statut = form.querySelector('.form-status');
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (!form.checkValidity()) { form.reportValidity(); return; }
        var btn = form.querySelector('[type="submit"]');
        if (btn.disabled) return;
        var libelle = btn.textContent;
        btn.disabled = true;
        btn.textContent = tr('Envoi en cours…');
        statut.hidden = true;

        var f = form.elements;
        var deux = f.deuxieme.checked;
        envoyerContact({
          type: 'candidature',
          bienId: document.body.getAttribute('data-ref'),
          prenom: f.prenom.value, nom: f.nom.value, email: f.email.value, tel: f.tel.value,
          message: f.remarque.value, societe: f.societe.value,
          page: location.pathname,
          candidature: {
            rue: f.rue.value, numero: f.numero.value, cp: f.cp.value, ville: f.ville.value,
            pays: f.pays.value, revenu: f.revenu.value,
            adultes: f.adultes.value, enfants: f.enfants.value, agesEnfants: f.ages_enfants.value,
            garant: f.garant.checked, animaux: f.animaux.value, animauxType: f.animaux_type.value,
            date: f.date.value,
            prenom2: deux ? f.prenom2.value : '', nom2: deux ? f.nom2.value : '',
            revenu2: deux ? f.revenu2.value : ''
          }
        }).then(function () {
          btn.disabled = false;
          btn.textContent = libelle;
          pop.classList.add('is-done');
          pop.querySelector('.apop__card').scrollTop = 0;
          setTimeout(function () {
            fermer();
            setTimeout(function () {
              pop.classList.remove('is-done');
              form.reset();
              pop.querySelectorAll('.apop__extra').forEach(function (x) { x.hidden = true; });
              if (nb && type) { type.disabled = true; type.required = false; }
              if (enfants && ages) { ages.disabled = true; ages.required = false; }
            }, 400);
          }, 3200);
        }, function (err) {
          btn.disabled = false;
          btn.textContent = libelle;
          statut.textContent = err.message;
          statut.className = 'form-status form-status--err';
          statut.hidden = false;
        });
      });
    }

    function ouvrir() {
      if (ouverte) return;
      if (!pop) construire();
      focusPrecedent = document.activeElement;
      ouverte = true;
      pop.classList.add('is-open');
      pop.querySelector('.apop__card').scrollTop = 0;
      document.body.style.overflow = 'hidden';
      var premier = pop.querySelector('input[name="prenom"]');
      if (premier) setTimeout(function () { premier.focus(); }, 340);
    }

    boutons.forEach(function (b) {
      b.addEventListener('click', function (e) { e.preventDefault(); ouvrir(); });
    });

    document.addEventListener('keydown', function (e) {
      if (!ouverte) return;
      if (e.key === 'Escape') { fermer(); return; }
      if (e.key === 'Tab') piegerTabulation(e);
    });
  }

  /* -----------------------------------------------------------------------
     7. Accordéon FAQ
     ----------------------------------------------------------------------- */
  function initFaq() {
    document.querySelectorAll('.faq').forEach(function (faq) {
      var items = faq.querySelectorAll('.faq__item');
      items.forEach(function (item) {
        var btn = item.querySelector('.faq__q');
        var panel = item.querySelector('.faq__a');
        if (!btn || !panel) return;

        function setOpen(open) {
          item.classList.toggle('is-open', open);
          btn.setAttribute('aria-expanded', open ? 'true' : 'false');
          var sign = btn.querySelector('.faq__sign');
          if (sign) sign.textContent = open ? '−' : '+';
          panel.style.height = open ? panel.firstElementChild.offsetHeight + 'px' : '0px';
        }

        // état initial
        setOpen(item.classList.contains('is-open'));

        btn.addEventListener('click', function () {
          var willOpen = !item.classList.contains('is-open');
          items.forEach(function (other) {
            if (other === item) return;
            other.classList.remove('is-open');
            var ob = other.querySelector('.faq__q');
            var op = other.querySelector('.faq__a');
            if (ob) {
              ob.setAttribute('aria-expanded', 'false');
              var os = ob.querySelector('.faq__sign');
              if (os) os.textContent = '+';
            }
            if (op) op.style.height = '0px';
          });
          setOpen(willOpen);
        });
      });

      window.addEventListener('resize', function () {
        faq.querySelectorAll('.faq__item.is-open .faq__a').forEach(function (p) {
          p.style.height = p.firstElementChild.offsetHeight + 'px';
        });
      });
    });
  }

  /* -----------------------------------------------------------------------
     8. Groupes de boutons (Acheter / Louer, filtres)
     ----------------------------------------------------------------------- */
  function initToggles() {
    document.querySelectorAll('[data-toggle-group]').forEach(function (group) {
      group.addEventListener('click', function (e) {
        var opt = e.target.closest('.toggle__opt, .chip');
        if (!opt || !group.contains(opt)) return;
        group.querySelectorAll('.toggle__opt, .chip').forEach(function (o) {
          o.classList.remove('toggle__opt--on', 'chip--active');
          o.setAttribute('aria-pressed', 'false');
        });
        opt.classList.add(opt.classList.contains('toggle__opt') ? 'toggle__opt--on' : 'chip--active');
        opt.setAttribute('aria-pressed', 'true');
        if (group.hasAttribute('data-units-filter')) filtrerLots(group, opt.getAttribute('data-filter'));
      });
    });
  }

  /* Filtre du tableau de lots d'une page projet : « Tous », « Disponibles »
     ou « n chambres ». Les lignes portent data-etat et data-ch. */
  function filtrerLots(group, filtre) {
    var table = document.querySelector('[data-units]');
    if (!table || !filtre) return;
    var visibles = 0;
    table.querySelectorAll('.units__row[data-etat]').forEach(function (row) {
      var ok = filtre === 'tous' ||
               (filtre === 'libre' && row.getAttribute('data-etat') !== 'vendu') ||
               (filtre.indexOf('ch:') === 0 && row.getAttribute('data-ch') === filtre.slice(3));
      row.hidden = !ok;
      if (ok) visibles++;
    });
    var vide = table.querySelector('[data-units-empty]');
    if (vide) vide.hidden = visibles > 0;
  }

  /* -----------------------------------------------------------------------
     9. Formulaires de démonstration
     ----------------------------------------------------------------------- */
  function initForms() {
    document.querySelectorAll('form[data-demo]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        var btn = form.querySelector('[type="submit"], .btn--primary');
        if (!btn || btn.dataset.busy) return;
        var label = btn.textContent;
        btn.dataset.busy = '1';
        btn.textContent = tr('Merci, nous vous recontactons');
        btn.style.background = 'var(--slate-800)';
        setTimeout(function () {
          btn.textContent = label;
          btn.style.background = '';
          delete btn.dataset.busy;
          form.reset();
        }, 2600);
      });
    });
  }

  /* -----------------------------------------------------------------------
     9 bis. Formulaires reliés à Omnicasa et au CMS
     Les demandes partent vers /api/contact (fonction Vercel), qui les
     transmet à Omnicasa (visite, candidature) et au CMS (toutes). Aucune
     clé ni adresse d'API côté navigateur.
     ----------------------------------------------------------------------- */
  function telAgence() {
    return document.body.getAttribute('data-nav') === 'Louer' ? '084 31 40 60' : '086 32 36 76';
  }

  function envoyerContact(donnees) {
    donnees.suivi = lireSuivi();
    return fetch('/api/contact', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(donnees)
    }).then(function (r) {
      return r.json().catch(function () { return {}; }).then(function (j) {
        if (r.ok && j.ok) return j;
        if (r.status === 422 && j.erreur) throw new Error(j.erreur);
        throw new Error(tr('L\'envoi n\'a pas abouti. Réessayez dans un instant ou appelez-nous au ') + telAgence() + '.');
      });
    }, function () {
      throw new Error(tr('Connexion impossible. Vérifiez votre réseau ou appelez-nous au ') + telAgence() + '.');
    });
  }

  /* Champs connus de /api/contact ; tous les autres champs nommés d'un
     formulaire partent dans « details », avec le texte de leur label. */
  var CHAMPS_CONTACT = ['prenom', 'nom', 'email', 'tel', 'message', 'objet', 'societe'];

  function libelleChamp(el) {
    var label = el.closest('label');
    var span = label && label.querySelector('.field__label');
    var texte = (span || label || el).textContent || el.name;
    return texte.replace(/\((facultatif|optionnel|optioneel|facultatief)\)/i, '').replace(/\s+/g, ' ').trim() || el.name;
  }

  function detailsFormulaire(form) {
    var lignes = [];
    Array.prototype.forEach.call(form.elements, function (el) {
      if (!el.name || el.disabled || CHAMPS_CONTACT.indexOf(el.name) >= 0) return;
      if (/^(submit|button|reset|hidden|file)$/.test(el.type)) return;
      if (el.type === 'checkbox') {
        if (!el.required) lignes.push([libelleChamp(el), el.checked ? 'Oui' : 'Non']); // case requise = accord
        return;
      }
      if (el.type === 'radio' && !el.checked) return;
      var v = String(el.value || '').trim();
      if (v) lignes.push([libelleChamp(el), v]);
    });
    return lignes;
  }

  /* Gestion locative : les liens [data-offre] (« gestion » ou « location »)
     et le paramètre ?offre= choisissent l'option correspondante du select
     [data-offre-choix] du formulaire « Confier mon bien ». */
  function initOffre() {
    var choix = document.querySelector('select[data-offre-choix]');
    if (!choix) return;
    function choisir(offre) {
      Array.prototype.forEach.call(choix.options, function (o) {
        if (o.getAttribute('data-offre') === offre) choix.value = o.value;
      });
    }
    var depuisUrl = new URLSearchParams(location.search).get('offre');
    if (depuisUrl) choisir(depuisUrl);
    document.querySelectorAll('a[data-offre]').forEach(function (a) {
      a.addEventListener('click', function () { choisir(a.getAttribute('data-offre')); });
    });
  }

  /* Formulaires [data-contact] : « visite » (fiche bien), « projet » (page
     projet, data-projet-id / data-projet-nom), « contact » (contact.html),
     « vendeur » (vendre.html). Le select name="objet" donne l'objet de la
     demande ; data-details-titre nomme le groupe des autres champs. */
  function initContact() {
    document.querySelectorAll('form[data-contact]').forEach(function (form) {
      var statut = form.querySelector('.form-status');
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        if (!form.checkValidity()) { form.reportValidity(); return; }
        var btn = form.querySelector('[type="submit"]');
        if (!btn || btn.disabled) return;
        var libelle = btn.textContent;
        btn.disabled = true;
        btn.textContent = tr('Envoi en cours…');
        if (statut) statut.hidden = true;

        var f = form.elements;
        var valeur = function (nom) { return f[nom] && typeof f[nom].value === 'string' ? f[nom].value : ''; };
        var donnees = {
          type: form.getAttribute('data-contact'),
          prenom: valeur('prenom'),
          nom: valeur('nom'),
          email: valeur('email'),
          tel: valeur('tel'),
          message: valeur('message'),
          objet: valeur('objet'),
          societe: valeur('societe'),
          page: location.pathname
        };
        if (form.getAttribute('data-bien-id')) donnees.bienId = form.getAttribute('data-bien-id');
        if (form.getAttribute('data-projet-id')) {
          donnees.projet = { id: form.getAttribute('data-projet-id'), nom: form.getAttribute('data-projet-nom') || '' };
        }
        var lignes = detailsFormulaire(form);
        if (lignes.length) donnees.details = [{ titre: form.getAttribute('data-details-titre') || 'Précisions', lignes: lignes }];

        envoyerContact(donnees).then(function () {
          form.reset();
          btn.textContent = tr('Merci, nous vous recontactons');
          btn.style.background = 'var(--slate-800)';
          if (statut) {
            statut.textContent = tr('Votre demande est bien partie. Nous revenons vers vous sous 48 h.');
            statut.className = 'form-status form-status--ok';
            statut.hidden = false;
          }
          setTimeout(function () {
            btn.textContent = libelle;
            btn.style.background = '';
            btn.disabled = false;
          }, 4000);
        }, function (err) {
          btn.textContent = libelle;
          btn.disabled = false;
          if (statut) {
            statut.textContent = err.message;
            statut.className = 'form-status form-status--err';
            statut.hidden = false;
          }
        });
      });
    });
  }

  /* Blog : au-delà de neuf articles, les suivants portent data-article-suite.
     Ils sont masqués ici et le bouton les affiche six par six. Sans script,
     tout reste visible. */
  function initPlusArticles() {
    var bouton = document.querySelector('[data-articles-plus]');
    var compte = document.querySelector('[data-articles-compte]');
    var caches = Array.prototype.slice.call(document.querySelectorAll('[data-article-suite]'));
    if (!bouton || !compte || !caches.length) return;
    var total = parseInt(compte.getAttribute('data-total'), 10) || 0;
    function maj() {
      compte.textContent = LANGUE === 'nl'
        ? (total - caches.length) + ' van ' + total + ' artikels'
        : (total - caches.length) + ' articles sur ' + total;
      bouton.hidden = !caches.length;
      bouton.style.display = caches.length ? '' : 'none';
    }
    caches.forEach(function (a) { a.style.display = 'none'; });
    bouton.addEventListener('click', function () {
      caches.splice(0, 6).forEach(function (a) { a.style.display = ''; });
      maj();
    });
    maj();
  }

  /* Provenance du visiteur (UTM, identifiants de clic publicitaire),
     transmise à Omnicasa avec la demande. Sans accord « marketing », elle
     n'est lue que sur la page d'arrivée, jamais enregistrée ; avec l'accord,
     elle est gardée 90 jours et complétée des cookies Meta _fbc / _fbp. */
  var SUIVI = {
    cle: 'mca.suivi',
    duree: 90 * 24 * 3600 * 1000,
    params: { utm_source: 'UTMSource', utm_medium: 'UTMMedium', utm_campaign: 'UTMCampaign',
              utm_term: 'UTMTerm', utm_content: 'UTMContent', gclid: 'GCLID',
              fbclid: 'FBCLID', msclkid: 'MSCLKID' }
  };

  function referentExterne() {
    var r = document.referrer || '';
    return r && r.indexOf(location.host) === -1 ? r.slice(0, 500) : '';
  }

  function suiviDeLUrl() {
    var p = new URLSearchParams(location.search), s = null;
    Object.keys(SUIVI.params).forEach(function (k) {
      var v = p.get(k);
      if (!v) return;
      s = s || {};
      s[SUIVI.params[k]] = v.slice(0, 300);
    });
    if (!s) return null;
    s.LandingPageURL = location.href.slice(0, 500);
    if (referentExterne()) s.ReferrerURL = referentExterne();
    return s;
  }

  function marketingAccepte() {
    var c = lireConsentement();
    return !!(c && c.marketing);
  }

  function cookie(nom) {
    var m = document.cookie.match(new RegExp('(?:^|; )' + nom + '=([^;]*)'));
    return m ? decodeURIComponent(m[1]) : '';
  }

  var suiviPage = null;

  function initSuivi() {
    suiviPage = suiviDeLUrl();
    if (!suiviPage) return;
    var garder = function () { ecrire(SUIVI.cle, JSON.stringify({ t: Date.now(), s: suiviPage })); };
    if (marketingAccepte()) garder();
    document.addEventListener('mca:consent', function (e) {
      if (e.detail && e.detail.marketing) garder();
    });
  }

  function lireSuivi() {
    var s = suiviPage;
    var accord = marketingAccepte();
    if (!s && accord) {
      try {
        var v = JSON.parse(lire(SUIVI.cle) || 'null');
        if (v && v.s && Date.now() - v.t < SUIVI.duree) s = v.s;
      } catch (e) {}
    }
    var out = {};
    if (s) Object.keys(s).forEach(function (k) { out[k] = s[k]; });
    if (!out.ReferrerURL && referentExterne()) out.ReferrerURL = referentExterne();
    if (accord) {
      if (cookie('_fbc')) out.FBC = cookie('_fbc');
      if (cookie('_fbp')) out.FBP = cookie('_fbp');
    }
    return out;
  }

  /* Fiche bien : « Retour aux biens » ramène à la liste avec ses filtres. */
  function initRetour() {
    var lien = document.querySelector('[data-retour-liste]');
    if (!lien) return;
    try {
      var r = JSON.parse(window.sessionStorage.getItem('mca.biens.retour') || 'null');
      var goal = document.body.getAttribute('data-nav') === 'Louer' ? 1 : 0;
      // Adresse absolue : /acheter.html… ou /nl/acheter.html…, de la même langue.
      var prefixe = LANGUE === 'nl' ? '/nl/' : '/';
      if (r && r.goal === goal && r.url.indexOf(prefixe) === 0 &&
          /^(acheter|louer)\.html(\?|$)/.test(r.url.slice(prefixe.length))) lien.href = r.url;
    } catch (e) {}
  }

  /* -----------------------------------------------------------------------
     9 ter. Visionneuse de photos (fiche bien)
     Les vignettes sont de vrais liens vers la photo : sans script, elles
     l'ouvrent simplement.
     ----------------------------------------------------------------------- */
  function initGalerie() {
    var galerie = document.querySelector('[data-galerie]');
    var source = document.querySelector('[data-galerie-photos]');
    if (!galerie || !source) return;
    var photos;
    try { photos = JSON.parse(source.textContent); } catch (e) { return; }
    if (!photos || !photos.length) return;

    var alt = galerie.getAttribute('data-galerie-alt') || tr('Photo du bien');
    var box = null, img = null, compteur = null, index = 0, ouverte = false, focusPrecedent = null, departX = null;

    function construire() {
      box = document.createElement('div');
      box.className = 'lightbox';
      box.setAttribute('role', 'dialog');
      box.setAttribute('aria-modal', 'true');
      box.setAttribute('aria-label', tr('Photos du bien'));
      box.innerHTML =
        '<div class="lightbox__top">' +
          '<span class="lightbox__count" aria-live="polite"></span>' +
          '<button type="button" class="lightbox__btn" data-lb="fermer" aria-label="' + tr('Fermer') + '"><span class="ico" data-i="x"></span></button>' +
        '</div>' +
        '<div class="lightbox__stage"><img class="lightbox__img" alt=""></div>' +
        (photos.length > 1
          ? '<button type="button" class="lightbox__btn lightbox__nav lightbox__nav--prev" data-lb="prec" aria-label="' + tr('Photo précédente') + '"><span class="ico" data-i="arrow-left"></span></button>' +
            '<button type="button" class="lightbox__btn lightbox__nav lightbox__nav--next" data-lb="suiv" aria-label="' + tr('Photo suivante') + '"><span class="ico" data-i="arrow-right"></span></button>'
          : '');
      document.body.appendChild(box);
      paintIcons(box);
      img = box.querySelector('.lightbox__img');
      compteur = box.querySelector('.lightbox__count');

      box.addEventListener('click', function (e) {
        var b = e.target.closest('[data-lb]');
        if (b) {
          var a = b.getAttribute('data-lb');
          if (a === 'fermer') fermer();
          else afficher(index + (a === 'suiv' ? 1 : -1));
          return;
        }
        if (e.target === box || e.target.classList.contains('lightbox__stage')) fermer();
      });
      box.addEventListener('touchstart', function (e) { departX = e.touches[0].clientX; }, { passive: true });
      box.addEventListener('touchend', function (e) {
        if (departX === null || photos.length < 2) return;
        var dx = e.changedTouches[0].clientX - departX;
        departX = null;
        if (Math.abs(dx) > 50) afficher(index + (dx < 0 ? 1 : -1));
      });
    }

    function afficher(i) {
      index = (i + photos.length) % photos.length;
      img.src = photos[index].src;
      img.alt = alt + tr(' · photo ') + (index + 1) + tr(' sur ') + photos.length;
      compteur.textContent = (index + 1) + ' / ' + photos.length;
      [index + 1, index - 1].forEach(function (j) {
        var p = photos[(j + photos.length) % photos.length];
        if (p) (new Image()).src = p.src;
      });
    }

    function ouvrir(i) {
      if (!box) construire();
      focusPrecedent = document.activeElement;
      afficher(i);
      ouverte = true;
      box.classList.add('is-open');
      document.body.style.overflow = 'hidden';
      box.querySelector('[data-lb="fermer"]').focus();
    }

    function fermer() {
      if (!ouverte) return;
      ouverte = false;
      box.classList.remove('is-open');
      document.body.style.overflow = '';
      if (focusPrecedent && focusPrecedent.focus) focusPrecedent.focus();
    }

    galerie.addEventListener('click', function (e) {
      var lien = e.target.closest('[data-galerie-i]');
      if (!lien) return;
      e.preventDefault();
      ouvrir(parseInt(lien.getAttribute('data-galerie-i'), 10) || 0);
    });

    document.addEventListener('keydown', function (e) {
      if (!ouverte) return;
      if (e.key === 'Escape') fermer();
      else if (e.key === 'ArrowRight') afficher(index + 1);
      else if (e.key === 'ArrowLeft') afficher(index - 1);
      else if (e.key === 'Tab') {
        var cibles = box.querySelectorAll('button');
        var premier = cibles[0], dernier = cibles[cibles.length - 1];
        if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
        else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
      }
    });
  }

  /* -----------------------------------------------------------------------
     9 quater. Sélecteur de langue
     Les liens [data-lang-switch="nl"|"fr"] (barre de navigation, menu)
     pointent vers la même page dans l'autre langue, avec sa recherche et
     son ancre : /x.html ↔ /nl/x.html, / ↔ /nl/, /bien-123-slug ↔
     /nl/bien-123-slug (la fiche corrige elle-même le slug).
     ----------------------------------------------------------------------- */
  function adresseLangue(cible) {
    var chemin = location.pathname || '/';
    var estNl = /^\/nl(\/|$)/.test(chemin);
    if (cible === 'nl') {
      if (!estNl) chemin = '/nl' + (chemin.charAt(0) === '/' ? chemin : '/' + chemin);
    } else if (estNl) {
      chemin = chemin.replace(/^\/nl(\/|$)/, '/');
    }
    return chemin + location.search + location.hash;
  }

  function initLangue() {
    document.querySelectorAll('a[data-lang-switch]').forEach(function (a) {
      var cible = a.getAttribute('data-lang-switch') === 'nl' ? 'nl' : 'fr';
      a.href = adresseLangue(cible);
      a.setAttribute('hreflang', cible);
    });
  }

  /* -----------------------------------------------------------------------
     10. Année courante
     ----------------------------------------------------------------------- */
  function initYear() {
    var y = String(new Date().getFullYear());
    document.querySelectorAll('[data-year]').forEach(function (el) { el.textContent = y; });
  }


  /* -----------------------------------------------------------------------
     11. Fenêtre d'inscription à la newsletter
     Se déclenche après 10 secondes de visite réelle, cumulées d'une page à
     l'autre et comptées seulement quand l'onglet est au premier plan.
     ----------------------------------------------------------------------- */
  var NL = {
    delai: 10,                 // secondes de visite avant affichage
    cle: 'mca.nl.etat',        // 'inscrit' | 'plus-tard'
    cleFin: 'mca.nl.jusqua',   // fin du silence après un refus
    cleTemps: 'mca.nl.secondes',
    silence: 30 * 24 * 3600 * 1000   // 30 jours
  };

  function lire(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
  function ecrire(k, v) { try { window.localStorage.setItem(k, v); } catch (e) {} }

  function newsletterFermee() {
    var etat = lire(NL.cle);
    if (etat === 'inscrit') return true;
    if (etat === 'plus-tard') {
      var fin = parseInt(lire(NL.cleFin) || '0', 10);
      if (Date.now() < fin) return true;
    }
    return false;
  }

  function construireFenetre() {
    var el = document.createElement('div');
    el.className = 'nlpop';
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-modal', 'true');
    el.setAttribute('aria-labelledby', 'nlpop-titre');
    el.innerHTML =
      '<div class="nlpop__scrim" data-nl-close></div>' +
      '<div class="nlpop__card">' +
        '<button class="nlpop__close" data-nl-close aria-label="' + tr('Fermer') + '">' +
          '<span class="ico" data-i="x"></span></button>' +
        '<div class="nlpop__media">' +
          '<img src="' + RACINE + 'assets/img/mos-1.jpg" alt="">' +
          '<span class="nlpop__badge">' + tr('Avant tout le monde') + '</span>' +
        '</div>' +
        '<div class="nlpop__body">' +
          '<h2 id="nlpop-titre">' + tr('Les nouveaux biens, une fois par mois') + '</h2>' +
          '<p>' + tr('Un seul e-mail par mois, avec ce qui est rentré chez nous en Famenne-Ardenne.') + '</p>' +
          '<ul class="nlpop__list">' +
            '<li><span class="ico ico--sm" data-i="check"></span>' + tr('Tous les biens rentrés le mois écoulé, réunis dans un e-mail.') + '</li>' +
            '<li><span class="ico ico--sm" data-i="check"></span>' + tr('Nos ventes exclusives, celles que nous ne publions pas sur le site.') + '</li>' +
          '</ul>' +
          '<form class="nlpop__form" data-demo novalidate>' +
            '<label class="field"><span class="visually-hidden">' + tr('Votre e-mail') + '</span>' +
              '<input class="input" type="email" name="email" placeholder="' + tr('vous@exemple.be') + '" required></label>' +
            '<button type="submit" class="btn btn--primary btn--lg btn--block">' + tr('Je veux les recevoir') + '</button>' +
            '<button type="button" class="nlpop__later" data-nl-close>' + tr('Non merci, pas maintenant') + '</button>' +
            '<p class="nlpop__legal">' + tr('Un e-mail par mois, jamais plus. Désinscription en un clic. ') +
              tr('Nous ne communiquons votre adresse à personne.') + '</p>' +
          '</form>' +
          '<div class="nlpop__done">' +
            '<span class="ico" data-i="shield-check"></span>' +
            '<h2>' + tr('C\'est noté, merci.') + '</h2>' +
            '<p style="margin-top:10px">' + tr('Vous recevrez la prochaine sélection dans votre boîte.') + '</p>' +
          '</div>' +
        '</div>' +
      '</div>';
    document.body.appendChild(el);
    paintIcons(el);
    return el;
  }

  function initNewsletter() {
    if (newsletterFermee()) return;

    var pop = null, ouverte = false, focusPrecedent = null, minuteur = null;
    var secondes = parseInt(lire(NL.cleTemps) || '0', 10);
    if (isNaN(secondes) || secondes < 0) secondes = 0;

    function fermer(raison) {
      if (!pop || !ouverte) return;
      ouverte = false;
      pop.classList.remove('is-open');
      document.body.style.overflow = '';
      if (raison === 'refus') {
        ecrire(NL.cle, 'plus-tard');
        ecrire(NL.cleFin, String(Date.now() + NL.silence));
      }
      if (focusPrecedent && focusPrecedent.focus) focusPrecedent.focus();
    }

    function piegerTabulation(e) {
      if (e.key !== 'Tab' || !ouverte) return;
      var cibles = pop.querySelectorAll('button, input, a[href]');
      if (!cibles.length) return;
      var premier = cibles[0], dernier = cibles[cibles.length - 1];
      if (e.shiftKey && document.activeElement === premier) { e.preventDefault(); dernier.focus(); }
      else if (!e.shiftKey && document.activeElement === dernier) { e.preventDefault(); premier.focus(); }
    }

    function ouvrir() {
      if (ouverte || newsletterFermee()) return;
      if (document.body.classList.contains('cookies-open')) return;   // le bandeau cookies d'abord
      if (!pop) {
        pop = construireFenetre();
        pop.querySelectorAll('[data-nl-close]').forEach(function (b) {
          b.addEventListener('click', function () { fermer('refus'); });
        });
        pop.querySelector('form').addEventListener('submit', function (e) {
          e.preventDefault();
          var champ = pop.querySelector('input[type="email"]');
          if (!champ.value || champ.value.indexOf('@') < 1) { champ.focus(); return; }
          ecrire(NL.cle, 'inscrit');
          pop.classList.add('is-done');
          setTimeout(function () { fermer(); }, 2200);
        });
      }
      focusPrecedent = document.activeElement;
      ouverte = true;
      pop.classList.add('is-open');
      document.body.style.overflow = 'hidden';
      var champ = pop.querySelector('input[type="email"]');
      if (champ) setTimeout(function () { champ.focus(); }, 340);
      if (minuteur) { clearInterval(minuteur); minuteur = null; }
    }

    document.addEventListener('keydown', function (e) {
      if (!ouverte) return;
      if (e.key === 'Escape') fermer('refus');
      else piegerTabulation(e);
    });

    function tic() {
      if (document.hidden) return;          // on ne compte que le temps réellement passé
      secondes += 1;
      ecrire(NL.cleTemps, String(secondes));
      if (secondes >= NL.delai) ouvrir();
    }
    minuteur = setInterval(tic, 1000);

    // déjà assez de temps cumulé sur les pages précédentes
    if (secondes >= NL.delai) setTimeout(ouvrir, 1200);
  }

  /* -----------------------------------------------------------------------
     11b. Consentement aux cookies
     Rien d'autre que le nécessaire n'est déposé sans accord. Le choix est
     conservé six mois dans localStorage (mca.cookies). Les scripts tiers
     (statistiques, marketing) doivent s'abonner à l'événement 'mca:consent'
     ou lire window.mcaConsent avant de se charger.
     ----------------------------------------------------------------------- */
  var CK = { cle: 'mca.cookies', duree: 182 * 24 * 3600 * 1000 };

  function lireConsentement() {
    try {
      var v = JSON.parse(lire(CK.cle) || 'null');
      if (!v || !v.date || Date.now() - v.date > CK.duree) return null;
      return v;
    } catch (e) { return null; }
  }

  function publierConsentement(c) {
    window.mcaConsent = c;
    try { document.dispatchEvent(new CustomEvent('mca:consent', { detail: c })); } catch (e) {}
  }

  function construireBandeau() {
    var el = document.createElement('div');
    el.className = 'ckb';
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-labelledby', 'ckb-titre');
    el.setAttribute('aria-describedby', 'ckb-texte');
    el.innerHTML =
      '<div class="ckb__card">' +
        '<div class="ckb__head">' +
          '<span class="ckb__mark"><img src="' + RACINE + 'assets/img/logomark-green.png" alt=""></span>' +
          '<div>' +
            '<h2 id="ckb-titre">' + tr('Un mot sur les cookies') + '</h2>' +
            '<p id="ckb-texte">' + tr('Nous utilisons des cookies nécessaires au fonctionnement du site et, avec votre accord, ') +
              tr('des cookies de mesure d\'audience et de personnalisation. ') +
              '<a href="cookies.html">' + tr('Politique de cookies') + '</a> · <a href="vie-privee.html">' + tr('Vie privée') + '</a></p>' +
          '</div>' +
        '</div>' +
        '<div class="ckb__prefs" hidden>' +
          '<label class="check ckb__opt"><input type="checkbox" checked disabled>' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            '<span><b>' + tr('Nécessaires') + '</b><small>' + tr('Toujours actifs : fonctionnement du site, mémorisation de vos choix.') + '</small></span></label>' +
          '<label class="check ckb__opt"><input type="checkbox" name="stats">' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            '<span><b>' + tr('Statistiques') + '</b><small>' + tr('Mesure d\'audience anonyme pour améliorer le site.') + '</small></span></label>' +
          '<label class="check ckb__opt"><input type="checkbox" name="marketing">' +
            '<span class="check__box"><svg viewBox="0 0 24 24"><path d="M20 6 9 17l-5-5"/></svg></span>' +
            '<span><b>Marketing</b><small>' + tr('Personnalisation, réseaux sociaux et publicité ciblée.') + '</small></span></label>' +
        '</div>' +
        '<div class="ckb__actions">' +
          '<button type="button" class="btn btn--primary" data-ck="tout">' + tr('Tout accepter') + '</button>' +
          '<button type="button" class="btn btn--secondary" data-ck="refus">' + tr('Refuser') + '</button>' +
          '<button type="button" class="ckb__link" data-ck="perso">' + tr('Personnaliser') + '</button>' +
          '<button type="button" class="btn btn--secondary" data-ck="save" hidden>' + tr('Enregistrer mes choix') + '</button>' +
        '</div>' +
      '</div>';
    document.body.appendChild(el);
    return el;
  }

  function initCookies() {
    var choix = lireConsentement();
    if (choix) publierConsentement(choix);

    var bandeau = null, ouvert = false;

    function fermer() {
      if (!bandeau || !ouvert) return;
      ouvert = false;
      bandeau.classList.remove('is-open');
      document.body.classList.remove('cookies-open');
    }

    function enregistrer(stats, marketing) {
      var c = { necessaire: true, stats: !!stats, marketing: !!marketing, date: Date.now() };
      ecrire(CK.cle, JSON.stringify(c));
      publierConsentement(c);
      fermer();
    }

    function ouvrir(mode) {
      if (!bandeau) {
        bandeau = construireBandeau();
        var prefs = bandeau.querySelector('.ckb__prefs');
        var save = bandeau.querySelector('[data-ck="save"]');
        var perso = bandeau.querySelector('[data-ck="perso"]');
        bandeau.querySelector('[data-ck="tout"]').addEventListener('click', function () { enregistrer(true, true); });
        bandeau.querySelector('[data-ck="refus"]').addEventListener('click', function () { enregistrer(false, false); });
        perso.addEventListener('click', function () {
          prefs.hidden = false; save.hidden = false; perso.hidden = true;
        });
        save.addEventListener('click', function () {
          enregistrer(prefs.querySelector('[name="stats"]').checked,
                      prefs.querySelector('[name="marketing"]').checked);
        });
      }
      var actuel = lireConsentement();
      var prefs2 = bandeau.querySelector('.ckb__prefs');
      if (actuel) {
        prefs2.querySelector('[name="stats"]').checked = !!actuel.stats;
        prefs2.querySelector('[name="marketing"]').checked = !!actuel.marketing;
      }
      var detail = (mode === 'perso');
      prefs2.hidden = !detail;
      bandeau.querySelector('[data-ck="save"]').hidden = !detail;
      bandeau.querySelector('[data-ck="perso"]').hidden = detail;
      ouvert = true;
      bandeau.classList.add('is-open');
      document.body.classList.add('cookies-open');
      setTimeout(function () { var b = bandeau.querySelector('[data-ck="tout"]'); if (b) b.focus(); }, 380);
    }

    document.querySelectorAll('[data-cookies-open]').forEach(function (b) {
      b.addEventListener('click', function () { ouvrir('perso'); });
    });

    if (!choix) setTimeout(function () { ouvrir(); }, 900);
  }

  /* -----------------------------------------------------------------------
     12. Page article — barre de progression, sommaire, partage
     ----------------------------------------------------------------------- */

  /* Barre de lecture : la progression se mesure sur le corps de l'article,
     pas sur la page entière. Sinon elle serait déjà à moitié pleine à
     l'ouverture, et n'atteindrait 100 % qu'au bas du pied de page. */
  function initReadbar() {
    var corps = document.querySelector('[data-read]');
    if (!corps) return;
    var barre = document.createElement('div');
    barre.className = 'readbar';
    barre.setAttribute('role', 'presentation');
    document.body.appendChild(barre);

    var raf = null;
    function calcul() {
      raf = null;
      var r = corps.getBoundingClientRect();
      var vh = window.innerHeight || 1;
      // course utile : du haut du corps jusqu'à sa fin passée sous la fenêtre
      var total = r.height - vh;
      var p = total <= 0 ? (r.bottom <= vh ? 1 : 0) : (-r.top) / total;
      barre.style.width = (Math.min(1, Math.max(0, p)) * 100) + '%';
    }
    function onScroll() { if (!raf) raf = requestAnimationFrame(calcul); }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    calcul();
  }

  /* Sommaire : les titres sont déjà dans le HTML avec leur id, on ne fait
     que suivre celui qui est en cours de lecture. Le titre actif est le
     dernier passé sous la ligne de lecture (un tiers de la fenêtre). */
  function initToc() {
    var toc = document.querySelector('[data-toc]');
    if (!toc) return;
    var liens = Array.prototype.slice.call(toc.querySelectorAll('.toc__link'));
    var cibles = liens.map(function (a) {
      return document.getElementById((a.getAttribute('href') || '').slice(1));
    });
    if (!cibles.length || cibles.indexOf(null) !== -1) return;

    var raf = null;
    function marquer() {
      raf = null;
      var ligne = (window.innerHeight || 0) / 3;
      var actif = 0;
      cibles.forEach(function (el, i) {
        if (el.getBoundingClientRect().top <= ligne) actif = i;
      });
      liens.forEach(function (a, i) {
        a.classList.toggle('is-on', i === actif);
        if (i === actif) a.setAttribute('aria-current', 'true');
        else a.removeAttribute('aria-current');
      });
    }
    function onScroll() { if (!raf) raf = requestAnimationFrame(marquer); }
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    marquer();
  }

  /* Partage : les réseaux sont de simples liens, seule la copie du lien
     demande du script. Le libellé du bouton confirme l'action 2 secondes. */
  function initShare() {
    document.querySelectorAll('[data-share-copy]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var url = location.href;

        function confirmer(ok) {
          var avant = btn.getAttribute('aria-label');
          btn.classList.add(ok ? 'is-done' : 'is-fail');
          btn.setAttribute('aria-label', tr(ok ? 'Lien copié' : 'Copie impossible'));
          setTimeout(function () {
            btn.classList.remove('is-done', 'is-fail');
            btn.setAttribute('aria-label', avant);
          }, 2000);
        }

        // Repli : sélection dans un champ hors écran. Sert aussi bien aux
        // navigateurs sans presse-papiers qu'aux pages servies en http, où
        // navigator.clipboard existe mais refuse d'écrire.
        function repli() {
          var z = document.createElement('textarea');
          z.value = url;
          z.setAttribute('readonly', '');
          z.style.cssText = 'position:fixed;top:-9999px';
          document.body.appendChild(z);
          z.select();
          var ok = false;
          try { ok = document.execCommand('copy'); } catch (e) {}
          document.body.removeChild(z);
          confirmer(ok);
        }

        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(url).then(function () { confirmer(true); }, repli);
          return;
        }
        repli();
      });
    });

    document.querySelectorAll('[data-share-native]').forEach(function (btn) {
      if (!navigator.share) { btn.hidden = true; return; }
      btn.addEventListener('click', function () {
        navigator.share({
          title: document.title,
          url: location.href
        }).catch(function () {});
      });
    });

    // Les liens réseaux portent l'URL de la page courante, pas une valeur figée.
    document.querySelectorAll('[data-share-url]').forEach(function (a) {
      a.href = a.getAttribute('data-share-url').replace('__URL__',
        encodeURIComponent(location.href)).replace('__T__',
        encodeURIComponent(document.title));
    });
  }

  /* -----------------------------------------------------------------------
     Démarrage
     ----------------------------------------------------------------------- */
  function boot() {
    paintIcons();
    initNav();
    initMenu();
    initLangue();
    initReveal();
    initParallax();
    initCards();
    initCounters();
    initLive();
    initAvis();
    initPostuler();
    initFaq();
    initToggles();
    initForms();
    initContact();
    initOffre();
    initPlusArticles();
    initSuivi();
    initRetour();
    initGalerie();
    initYear();
    initCookies();
    initNewsletter();
    initReadbar();
    initToc();
    initShare();
  }

  // Utilisé par assets/js/biens.js pour le contenu chargé depuis Omnicasa.
  window.MCA = { icones: paintIcons, reveler: reveler };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
