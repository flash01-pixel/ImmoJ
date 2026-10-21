/* Rendu des biens Omnicasa, partagé entre le navigateur (listes de
   acheter.html, louer.html, accueil) et les fonctions serveur (fiche bien,
   biens similaires). Module ES sans dépendance ni accès au DOM.

   Langue : les fonctions qui produisent du texte prennent un paramètre
   langue, 'fr' par défaut ou 'nl'. Le module ne lit jamais document : le
   navigateur passe document.documentElement.lang, le serveur ?langue=.

   Un « bien » est l'objet allégé produit par api/_lib/biens.js :
   { id, goal, exclusif, etat, type, typeNom, titre, ville, cp, adresse,
     prix, chambres, sdb, surface, terrain, photo, date, dateFin, projet } */

export function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

/* Libellés néerlandais des textes de ce module, indexés par le français. */
var NL = {
  'Nouveau': 'Nieuw',
  'Option': 'Onder optie',
  'Vendu': 'Verkocht',
  'Loué': 'Verhuurd',
  'Nouvelles conditions': 'Nieuwe voorwaarden',
  'Offre à partir de': 'Bod vanaf',
  'Exclusivité': 'Exclusief',
  'Prix sur demande': 'Prijs op aanvraag',
  '/ mois': '/ maand',
  'Photo à venir': 'Foto volgt',
  'Bien': 'Pand',
  ' à ': ' in '
};

export function tr(s, langue) {
  return langue === 'nl' && Object.prototype.hasOwnProperty.call(NL, s) ? NL[s] : s;
}

/* 1650000 → « 1 650 000 » (espaces insécables : un prix ne se coupe pas),
   « 1.650.000 » en néerlandais. */
export function nombre(n, langue) {
  return String(Math.round(Number(n) || 0)).replace(/\B(?=(\d{3})+(?!\d))/g, langue === 'nl' ? '.' : '\u00a0');
}

/* Montant en euros : « 250 000 € », « € 250.000 » en néerlandais. */
export function montant(n, langue) {
  return langue === 'nl' ? '€\u00a0' + nombre(n, langue) : nombre(n) + '\u00a0€';
}

const PETITS = new Set(['en', 'sur', 'de', 'la', 'le', 'les', 'du', 'des', 'aux', 'au', 'lez', 'sous', 'd', 'l']);

/* Omnicasa mélange « DURBUY », « Durbuy » et « BARVAUX-SUR-OURTHE ». */
export function villeTitre(v) {
  return String(v || '').trim().toLowerCase().replace(/\p{L}+/gu, function (m, i) {
    return i > 0 && PETITS.has(m) ? m : m.charAt(0).toUpperCase() + m.slice(1);
  });
}

export function slug(s) {
  return String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

export function urlBien(b) {
  var s = slug(b.typeNom + ' ' + b.ville);
  return 'bien-' + b.id + (s ? '-' + s : '');
}

export function termine(b) {
  return b.etat === 'vendu' || b.etat === 'loue';
}

/* Photos : passage par l'optimiseur d'images de Vercel (redimensionnement,
   WebP/AVIF). Les largeurs doivent figurer dans images.sizes de vercel.json. */
export function photo(url, largeur) {
  if (!url) return '';
  return '/_vercel/image?url=' + encodeURIComponent(url) + '&w=' + largeur + '&q=70';
}

export function srcset(url) {
  return photo(url, 640) + ' 640w, ' + photo(url, 1080) + ' 1080w';
}

var BADGES = {
  nouveau:    ['badge--new', 'Nouveau'],
  option:     ['badge--option', 'Option'],
  vendu:      ['badge--sold', 'Vendu'],
  loue:       ['badge--sold', 'Loué'],
  conditions: ['badge--sale', 'Nouvelles conditions'],
  offre:      ['badge--sale', 'Offre à partir de']
};

export function badges(b, langue) {
  var html = '';
  var e = BADGES[b.etat];
  if (e) html += '<span class="badge ' + e[0] + '">' + tr(e[1], langue) + '</span>';
  if (b.exclusif && !termine(b)) html += '<span class="badge badge--rent">' + tr('Exclusivité', langue) + '</span>';
  return html;
}

export function prixHtml(b, taillePetite, langue) {
  if (b.etat === 'vendu') return tr('Vendu', langue);
  if (b.etat === 'loue') return tr('Loué', langue);
  if (b.prix == null) return tr('Prix sur demande', langue);
  var p = montant(b.prix, langue);
  if (b.goal === 1) {
    p += ' <span style="font-size:' + (taillePetite || '14px') + ';font-weight:700;color:var(--text-muted)">' + tr('/ mois', langue) + '</span>';
  }
  return p;
}

/* Titre éditorial saisi dans Omnicasa, nettoyé :
   « VILLA D'EXCEPTION – GOLF DE DURBUY » → « Villa d'exception – golf de Durbuy »,
   « Appartement 1 chambre - VENDU » → « Appartement 1 chambre ». */
export function titreBien(b, langue) {
  var t = String(b.titre || '').trim()
    .replace(/\s*[-–(]\s*(vendu|option|optie|lou[ée]|sous option|sous compromis)\)?\s*$/i, '');
  if (!t || t.toLowerCase() === String(b.typeNom || '').toLowerCase()) return altPhoto(b, langue);
  if (t.length > 12 && t === t.toUpperCase()) {
    t = t.toLowerCase();
    var v = String(b.ville || '').toLowerCase();
    if (v) t = t.split(v).join(b.ville);
  }
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export function titreCarte(b, langue) {
  return b.ville + ' · ' + (b.typeNom || tr('Bien', langue));
}

export function adresseLigne(b) {
  return (b.adresse ? b.adresse + ' ' : '') + b.cp + ' ' + b.ville.toUpperCase();
}

export function metaHtml(b, langue) {
  var m = '';
  if (b.chambres) m += '<span><span class="ico ico--sm" data-i="bed-double"></span>' + b.chambres + '</span>';
  if (b.sdb) m += '<span><span class="ico ico--sm" data-i="bath"></span>' + b.sdb + '</span>';
  var s = b.surface || b.terrain;
  if (s) m += '<span><span class="ico ico--sm" data-i="ruler"></span>' + nombre(s, langue) + '\u00a0m²</span>';
  return m;
}

export function altPhoto(b, langue) {
  return (b.typeNom || tr('Bien', langue)) + tr(' à ', langue) + b.ville;
}

/* opts : { reveal: false } pour ne pas animer, langue: 'nl'. */
export function carte(b, opts) {
  opts = opts || {};
  var lg = opts.langue;
  var img = b.photo
    ? '<img src="' + esc(photo(b.photo, 640)) + '" srcset="' + esc(srcset(b.photo)) + '" ' +
      'sizes="(max-width: 700px) 100vw, (max-width: 1080px) 50vw, 400px" ' +
      'alt="' + esc(altPhoto(b, lg)) + '" loading="lazy" decoding="async">'
    : '<div class="ph"><span>' + tr('Photo à venir', lg) + '</span></div>';
  var meta = metaHtml(b, lg);
  return '<a class="property' + (termine(b) ? ' property--done' : '') + '" href="' + esc(urlBien(b)) + '"' +
      (opts.reveal === false ? '' : ' data-reveal') + '>' +
    '<div class="property__media"><div class="property__badges">' + badges(b, lg) + '</div>' + img + '</div>' +
    '<div class="property__body"><div class="property__price">' + prixHtml(b, null, lg) + '</div>' +
      '<div class="property__title">' + esc(titreCarte(b, lg)) + '</div>' +
      '<div class="property__loc">' + esc(adresseLigne(b)) + '</div>' +
      (meta ? '<div class="property__meta">' + meta + '</div>' : '') +
    '</div>' +
  '</a>';
}

/* Typographie française : espace insécable avant « : ; ! ? » et à l'intérieur
   des guillemets, pour que la ponctuation ne passe jamais seule à la ligne.
   Seul le texte entre les balises est touché (pas les attributs, scripts, styles). */
export function espacesInsecables(html) {
  return html.replace(/(<(script|style)\b[\s\S]*?<\/\2>)|(<[^>]*>)|([^<]+)/gi, (tout, bloc, nom, balise, texte) => {
    if (!texte) return tout;
    return texte.replace(/[ \u00a0]+([:;!?»])/g, '&nbsp;$1').replace(/«[ \u00a0]+/g, '«&nbsp;');
  });
}
