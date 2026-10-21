/* =========================================================================
   MCA Immobilier — biens Omnicasa côté navigateur
   - listes filtrables de acheter.html et louer.html ([data-biens-goal])
   - trois derniers biens de l'accueil ([data-biens-accueil])
   - mosaïque de l'accueil : dernier bien vendu, bien à la une ([data-hero-…])
   Les données viennent de /api/biens (fonction Vercel, cache CDN).
   Pages néerlandaises (<html lang="nl">) : données Omnicasa et textes en NL.
   ========================================================================= */

import {
  esc, nombre, carte, urlBien, termine, photo, srcset, prixHtml, adresseLigne, altPhoto, titreBien
} from './biens-rendu.js?v=3376c279';

const LANGUE = document.documentElement.lang === 'nl' ? 'nl' : 'fr';

/* Textes néerlandais, indexés par le français. */
const NL = {
  'Exclusivité': 'Exclusief',
  'À la une': 'In de kijker',
  'Nouveau': 'Nieuw',
  'chambre': 'slaapkamer',
  'chambres': 'slaapkamers',
  'salle de bain': 'badkamer',
  'salles de bain': 'badkamers',
  'Voir le bien': 'Bekijk het pand',
  'Postuler pour ce bien': 'Kandidaat stellen voor dit pand',
  'Demander une visite': 'Bezoek aanvragen',
  'bien disponible': 'pand beschikbaar',
  'biens disponibles': 'panden beschikbaar',
  ' déjà loués': ' al verhuurd',
  ' déjà vendus': ' al verkocht',
  'Aucun bien ne correspond': 'Geen enkel pand gevonden',
  'Élargissez votre recherche, ou laissez-nous vos critères : nous vous prévenons dès qu\'un bien correspond.':
    'Verruim uw zoekopdracht, of laat ons uw criteria weten: we verwittigen u zodra er een pand beschikbaar is dat bij u past.',
  'Effacer les filtres': 'Filters wissen',
  ' sur ': ' van ',
  ' biens': ' panden',
  'Charger ': 'Nog ',
  ' biens de plus': ' panden laden',
  'Tous les types de biens': 'Alle types panden',
  'Les biens n\'ont pas pu être chargés': 'De panden konden niet geladen worden',
  'Rechargez la page dans un instant, ou appelez-nous au ': 'Laad de pagina zo dadelijk opnieuw, of bel ons op ',
  'Recharger': 'Opnieuw laden',
  ' · vendu': ' · verkocht'
};

function t(s) { return LANGUE === 'nl' && Object.prototype.hasOwnProperty.call(NL, s) ? NL[s] : s; }

const TYPES = LANGUE === 'nl'
  ? { 1: 'Woning', 2: 'Appartement', 3: 'Studio', 4: 'Grond', 5: 'Gebouw', 6: 'Handelspand', 7: 'Garage' }
  : { 1: 'Maison', 2: 'Appartement', 3: 'Studio', 4: 'Terrain', 5: 'Immeuble', 6: 'Commerce', 7: 'Garage' };
const PAR_PAGE = 11;               // + la tuile « Rien qui vous plaît » = 12 cases
const CLE_RETOUR = 'mca.biens.retour';

const donnees = {};
function charger(goal) {
  if (!donnees[goal]) {
    donnees[goal] = fetch('/api/biens?goal=' + goal + (LANGUE === 'nl' ? '&langue=nl' : ''), { headers: { Accept: 'application/json' } })
      .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
      .then(j => j.biens || []);
  }
  return donnees[goal];
}

function pret() {
  // mca.js démarre sur DOMContentLoaded : on passe après lui.
  return document.readyState === 'loading'
    ? new Promise(r => document.addEventListener('DOMContentLoaded', r, { once: true }))
    : Promise.resolve();
}

function finir(root) {
  if (window.MCA) { window.MCA.icones(root); window.MCA.reveler(root); }
}

function sansAccents(s) {
  return String(s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
}

function pl(n, un, plusieurs) { return n + ' ' + t(n > 1 ? plusieurs : un); }

/* Bien à la une d'une liste sans filtre : exclusivité la plus récente
   (vente), location la plus récente. Partagé avec la mosaïque de l'accueil,
   qui doit montrer le même bien que le haut de acheter.html. */
function choisirVedette(liste, goal) {
  const dispo = liste.filter(b => !termine(b) && b.photo);
  const recents = dispo.slice().sort((a, b) => String(b.date).localeCompare(String(a.date)));
  if (goal === 0) {
    const exclu = recents.find(b => b.exclusif);
    if (exclu) return [exclu, t('Exclusivité')];
    const cher = dispo.slice().sort((a, b) => (b.prix || 0) - (a.prix || 0))[0];
    return cher ? [cher, t('À la une')] : null;
  }
  return recents[0] ? [recents[0], t(recents[0].etat === 'nouveau' ? 'Nouveau' : 'À la une')] : null;
}

/* ------------------------------------------------------------------------ */
/* Liste                                                                     */

function initListe(main) {
  const goal = main.getAttribute('data-biens-goal') === '1' ? 1 : 0;
  const $ = s => main.querySelector(s);
  const champs = {
    q: $('[data-filtre="q"]'),
    type: $('[data-filtre="type"]'),
    prix: $('[data-filtre="prix"]'),
    ch: $('[data-filtre="ch"]'),
    tri: $('[data-filtre="tri"]')
  };
  const grille = $('[data-biens-grille]');
  const tuile = grille && grille.querySelector('.dark-tile');
  const vedette = $('[data-biens-vedette]');
  const resultat = $('[data-biens-resultat]');
  const total = main.querySelector('[data-biens-total]');
  const exclusifChip = $('[data-biens-exclusifs]');
  const pagination = $('[data-biens-pagination]');
  const pagesTexte = $('[data-biens-pages-texte]');
  const pager = $('[data-biens-pager]');
  const plus = $('[data-biens-plus]');
  if (!grille) return;

  const triDefaut = champs.tri ? champs.tri.options[0].value : 'recent';
  const etat = { debut: 1, fin: 1, exclusifs: false };
  let tous = [];

  // État initial depuis l'URL (retour depuis une fiche, lien partagé)
  const p = new URLSearchParams(location.search);
  if (champs.q) champs.q.value = p.get('q') || '';
  etat.exclusifs = p.get('exclusifs') === '1';
  etat.debut = etat.fin = Math.max(1, parseInt(p.get('page'), 10) || 1);

  function valeur(nom) { return champs[nom] ? champs[nom].value : ''; }

  function filtresActifs() {
    return !!(valeur('q').trim() || valeur('type') || valeur('prix') || valeur('ch') || etat.exclusifs);
  }

  function filtrer() {
    const mots = sansAccents(valeur('q')).split(/\s+/).filter(Boolean);
    const type = parseInt(valeur('type'), 10) || 0;
    const [min, max] = (valeur('prix') || '-').split('-').map(v => parseInt(v, 10));
    const ch = parseInt(valeur('ch'), 10) || 0;
    return tous.filter(b => {
      if (etat.exclusifs && !b.exclusif) return false;
      if (type && b.type !== type) return false;
      if (ch && b.chambres < ch) return false;
      if (min > 0 || max > 0) {
        if (b.prix == null) return false;
        if (min > 0 && b.prix < min) return false;
        if (max > 0 && b.prix >= max) return false;
      }
      if (mots.length) {
        const texte = sansAccents([b.ville, b.cp, b.adresse, b.typeNom, b.titre, b.id].join(' '));
        if (!mots.every(m => texte.includes(m))) return false;
      }
      return true;
    });
  }

  function trier(liste) {
    const tri = valeur('tri') || triDefaut;
    const prix = (b, sens) => b.prix == null ? Infinity : sens * b.prix;
    const cle = {
      'prix-desc': b => prix(b, -1),
      'prix-asc': b => prix(b, 1),
      'surface': b => -(b.surface || b.terrain || 0)
    }[tri];
    return liste.slice().sort((a, b) => {
      // Les biens vendus ou loués restent visibles, mais après les autres.
      const t = termine(a) - termine(b);
      if (t) return t;
      if (cle) { const d = cle(a) - cle(b); if (d) return d; }
      return String(b.date).localeCompare(String(a.date)) || b.id - a.id;
    });
  }

  function rendreVedette(v) {
    if (!vedette) return;
    if (!v) { vedette.hidden = true; vedette.innerHTML = ''; return; }
    const [b, label] = v;
    const faits = [];
    if (b.chambres) faits.push(pl(b.chambres, 'chambre', 'chambres'));
    if (b.sdb) faits.push(pl(b.sdb, 'salle de bain', 'salles de bain'));
    if (b.surface || b.terrain) faits.push(nombre(b.surface || b.terrain, LANGUE) + '\u00a0m²');
    vedette.hidden = false;
    vedette.innerHTML =
      '<a class="feature-listing" href="' + esc(urlBien(b)) + '" data-reveal>' +
        '<div class="feature-listing__media">' +
          '<img src="' + esc(photo(b.photo, 1080)) + '" srcset="' + esc(srcset(b.photo)) + '" sizes="(max-width: 900px) 100vw, 55vw" alt="' + esc(altPhoto(b, LANGUE)) + '">' +
          '<span class="badge ' + (goal ? 'badge--rent' : 'badge--sale') + '" style="position:absolute;left:16px;top:16px">' + label + '</span>' +
        '</div>' +
        '<div class="feature-listing__body">' +
          '<span class="kicker">' + esc(b.ville) + '</span>' +
          '<h2 style="font-size:clamp(28px,3.2vw,44px);letter-spacing:-.035em;line-height:1.02">' + esc(titreBien(b, LANGUE)) + '</h2>' +
          '<div style="font-size:16px" class="muted">' + esc(adresseLigne(b)) + '</div>' +
          '<div class="feature-listing__price">' + prixHtml(b, '.5em', LANGUE) + '</div>' +
          (faits.length ? '<div class="feature-listing__meta">' + faits.map(f => '<span>' + f + '</span>').join('') + '</div>' : '') +
          '<div style="margin-top:18px;display:flex;gap:12px;flex-wrap:wrap">' +
            '<span class="btn btn--primary">' + t('Voir le bien') + '</span>' +
            '<span class="btn btn--secondary">' + t(goal ? 'Postuler pour ce bien' : 'Demander une visite') + '</span>' +
          '</div>' +
        '</div>' +
      '</a>';
    finir(vedette);
  }

  function lienPage(n, courante) {
    return n === courante
      ? '<span class="pager__i pager__i--on" aria-current="page">' + n + '</span>'
      : '<a href="?page=' + n + '" class="pager__i" data-page="' + n + '">' + n + '</a>';
  }

  function rendrePager(nbPages) {
    if (!pager) return;
    const c = etat.fin;
    const nums = new Set([1, nbPages, c - 1, c, c + 1].filter(n => n >= 1 && n <= nbPages));
    let html = '', prec = 0;
    [...nums].sort((a, b) => a - b).forEach(n => {
      if (prec && n - prec > 1) html += '<span style="font-size:16px;padding-inline:4px" class="muted">…</span>';
      html += lienPage(n, c);
      prec = n;
    });
    pager.innerHTML = html;
  }

  function memoriserUrl() {
    const q = new URLSearchParams();
    if (valeur('q').trim()) q.set('q', valeur('q').trim());
    if (valeur('type')) q.set('type', valeur('type'));
    if (valeur('prix')) q.set('prix', valeur('prix'));
    if (valeur('ch')) q.set('ch', valeur('ch'));
    if (valeur('tri') && valeur('tri') !== triDefaut) q.set('tri', valeur('tri'));
    if (etat.exclusifs) q.set('exclusifs', '1');
    if (etat.fin > 1) q.set('page', etat.fin);
    const url = location.pathname + (q.toString() ? '?' + q : '');
    history.replaceState(null, '', url);
    try { sessionStorage.setItem(CLE_RETOUR, JSON.stringify({ goal, url })); } catch (e) {}
  }

  function rendre(options) {
    options = options || {};
    const resultats = trier(filtrer());
    const actifs = filtresActifs();
    const v = !actifs && etat.debut === 1 ? choisirVedette(resultats, goal) : null;
    rendreVedette(v);
    const liste = v ? resultats.filter(b => b !== v[0]) : resultats;

    const nbPages = Math.max(1, Math.ceil(liste.length / PAR_PAGE));
    etat.fin = Math.min(etat.fin, nbPages);
    etat.debut = Math.min(etat.debut, etat.fin);
    const tranche = liste.slice((etat.debut - 1) * PAR_PAGE, etat.fin * PAR_PAGE);

    const dispo = resultats.filter(b => !termine(b)).length;
    const finis = resultats.length - dispo;
    if (resultat) {
      resultat.textContent = pl(dispo, 'bien disponible', 'biens disponibles');
      const s = resultat.nextElementSibling;
      if (s && s.hasAttribute('data-biens-termines')) {
        s.textContent = finis ? ' · ' + finis + t(goal ? ' déjà loués' : ' déjà vendus') : '';
      }
    }
    if (exclusifChip) exclusifChip.hidden = !etat.exclusifs;

    if (options.ajout) {
      const avant = grille.querySelectorAll('.property').length;
      const debutAjout = (etat.fin - 1) * PAR_PAGE - (etat.debut - 1) * PAR_PAGE;
      const nouveaux = tranche.slice(debutAjout).map(b => carte(b, { langue: LANGUE })).join('');
      if (tuile) tuile.insertAdjacentHTML('beforebegin', nouveaux); else grille.insertAdjacentHTML('beforeend', nouveaux);
      const cartes = grille.querySelectorAll('.property');
      if (cartes[avant]) cartes[avant].focus({ preventScroll: true });
    } else {
      grille.querySelectorAll('.property, .biens-vide').forEach(el => el.remove());
      if (!tranche.length) {
        const vide = '<div class="biens-vide"><h3>' + t('Aucun bien ne correspond') + '</h3>' +
          '<p class="muted">' + t('Élargissez votre recherche, ou laissez-nous vos critères : nous vous prévenons dès qu\'un bien correspond.') + '</p>' +
          '<button type="button" class="btn btn--secondary" data-biens-reset>' + t('Effacer les filtres') + '</button></div>';
        if (tuile) tuile.insertAdjacentHTML('beforebegin', vide); else grille.insertAdjacentHTML('beforeend', vide);
      } else {
        const html = tranche.map(b => carte(b, { langue: LANGUE })).join('');
        if (tuile) tuile.insertAdjacentHTML('beforebegin', html); else grille.insertAdjacentHTML('beforeend', html);
      }
    }
    grille.querySelectorAll(':scope > [data-reveal]').forEach(el => { el.style.transitionDelay = ''; });
    finir(grille);

    if (pagination) {
      pagination.hidden = liste.length <= PAR_PAGE;
      const premier = (etat.debut - 1) * PAR_PAGE + 1;
      const dernier = Math.min(etat.fin * PAR_PAGE, liste.length);
      if (pagesTexte) pagesTexte.textContent = (liste.length ? premier + '–' + dernier : '0') + t(' sur ') + liste.length + t(' biens');
      rendrePager(nbPages);
      if (plus) {
        const reste = liste.length - dernier;
        plus.hidden = reste <= 0;
        plus.textContent = t('Charger ') + Math.min(PAR_PAGE, reste) + t(' biens de plus');
      }
    }
    memoriserUrl();
  }

  function remplirTypes() {
    if (!champs.type) return;
    const present = new Set(tous.map(b => b.type));
    const voulu = new URLSearchParams(location.search).get('type') || '';
    champs.type.innerHTML = '<option value="">' + t('Tous les types de biens') + '</option>' +
      Object.keys(TYPES).filter(k => present.has(Number(k)))
        .map(k => '<option value="' + k + '">' + TYPES[k] + '</option>').join('');
    champs.type.value = present.has(Number(voulu)) ? voulu : '';
  }

  function remettreSelects() {
    const p = new URLSearchParams(location.search);
    ['prix', 'ch', 'tri'].forEach(n => {
      if (!champs[n] || !p.get(n)) return;
      const ok = [...champs[n].options].some(o => o.value === p.get(n));
      if (ok) champs[n].value = p.get(n);
    });
  }

  function remonter() {
    const cible = vedette && !vedette.hidden ? vedette : grille;
    const y = cible.getBoundingClientRect().top + window.scrollY - 120;
    if (window.scrollY > y) window.scrollTo({ top: y, behavior: 'smooth' });
  }

  function surChangement() { etat.debut = etat.fin = 1; rendre(); }

  let minuterie;
  if (champs.q) {
    champs.q.addEventListener('input', () => { clearTimeout(minuterie); minuterie = setTimeout(surChangement, 220); });
    const form = champs.q.closest('form');
    if (form) form.addEventListener('submit', e => { e.preventDefault(); surChangement(); });
  }
  ['type', 'prix', 'ch', 'tri'].forEach(n => champs[n] && champs[n].addEventListener('change', surChangement));

  main.addEventListener('click', e => {
    const page = e.target.closest('[data-page]');
    if (page) {
      e.preventDefault();
      etat.debut = etat.fin = parseInt(page.getAttribute('data-page'), 10);
      rendre();
      remonter();
      return;
    }
    if (e.target.closest('[data-biens-plus]')) {
      e.preventDefault();
      etat.fin += 1;
      rendre({ ajout: true });
      return;
    }
    if (e.target.closest('[data-biens-exclusifs]')) {
      e.preventDefault();
      etat.exclusifs = false;
      surChangement();
      return;
    }
    if (e.target.closest('[data-biens-reset]')) {
      e.preventDefault();
      Object.values(champs).forEach(c => { if (c && c !== champs.tri) c.value = ''; });
      etat.exclusifs = false;
      surChangement();
    }
  });

  charger(goal).then(biens => {
    tous = biens;
    main.classList.remove('is-loading');
    remplirTypes();
    remettreSelects();
    if (total) total.textContent = biens.filter(b => !termine(b)).length;
    rendre();
  }).catch(() => {
    main.classList.remove('is-loading');
    grille.querySelectorAll('.property').forEach(el => el.remove());
    if (vedette) vedette.hidden = true;
    const tel = goal ? ['+3284314060', '084 31 40 60'] : ['+3286323676', '086 32 36 76'];
    grille.insertAdjacentHTML('afterbegin',
      '<div class="biens-vide"><h3>' + t('Les biens n\'ont pas pu être chargés') + '</h3>' +
      '<p class="muted">' + t('Rechargez la page dans un instant, ou appelez-nous au ') + '<a href="tel:' + tel[0] + '">' + tel[1] + '</a>.</p>' +
      '<button type="button" class="btn btn--secondary" onclick="location.reload()">' + t('Recharger') + '</button></div>');
    if (pagination) pagination.hidden = true;
  });
}

/* ------------------------------------------------------------------------ */
/* Accueil : les trois biens les plus récents                                */

function initAccueil(zone) {
  const cases = zone.querySelectorAll('[data-biens-case]');
  charger(0).then(biens => {
    const choix = biens.filter(b => !termine(b) && b.photo && b.prix)
      .sort((a, b) => String(b.date).localeCompare(String(a.date)))
      .slice(0, cases.length);
    cases.forEach((c, i) => {
      if (choix[i]) c.innerHTML = carte(choix[i], { reveal: false, langue: LANGUE });
      else c.remove();
    });
    finir(zone);
  }).catch(() => {
    cases.forEach(c => c.remove());
  });
}

/* ------------------------------------------------------------------------ */
/* Accueil : mosaïque                                                        */
/* Grande photo : le dernier bien passé en « Vendu », pastille « Ville · vendu ».
   Photo de droite : le bien à la une de acheter.html, avec son lien.
   Les images d'origine (data-repli) ne servent que si Omnicasa ne répond pas. */

function poserPhoto(tuile, b, largeurs) {
  const img = tuile.querySelector('img[data-repli]');
  if (!img) return Promise.resolve();
  return new Promise(fin => {
    img.onload = img.onerror = fin;
    if (b) {
      img.sizes = largeurs;
      img.srcset = srcset(b.photo);
      img.src = photo(b.photo, 1080);
      img.alt = altPhoto(b, LANGUE);
    } else {
      img.src = img.getAttribute('data-repli');
      img.alt = img.getAttribute('data-repli-alt') || '';
    }
  }).then(() => tuile.classList.add('is-pret'));
}

function initMosaique(vendu, une) {
  charger(0).then(biens => {
    if (vendu) {
      const b = biens.filter(x => x.etat === 'vendu' && x.photo)
        .sort((x, y) => String(y.dateFin).localeCompare(String(x.dateFin)) || y.id - x.id)[0];
      const pastille = vendu.querySelector('[data-hero-pastille]');
      if (b && pastille) {
        pastille.querySelector('span').textContent = b.ville + t(' · vendu');
        pastille.hidden = false;
      }
      poserPhoto(vendu, b, '(max-width: 660px) 100vw, (max-width: 1080px) 62vw, 50vw');
    }
    if (une) {
      const v = choisirVedette(biens, 0);
      if (v) {
        const [b, label] = v;
        const champ = n => une.querySelector('[data-hero-une-' + n + ']');
        champ('label').textContent = label;
        champ('lieu').textContent = b.ville;
        champ('prix').innerHTML = prixHtml(b, null, LANGUE);
        champ('titre').textContent = titreBien(b, LANGUE) + ', ';
        champ('lien').href = urlBien(b);
        une.classList.add('is-bien');
      }
      poserPhoto(une, v && v[0], '(max-width: 1080px) 1px, 25vw');
    }
  }).catch(() => {
    [vendu, une].forEach(t => t && poserPhoto(t, null));
  });
}

/* ------------------------------------------------------------------------ */

pret().then(() => {
  const main = document.querySelector('[data-biens-goal]');
  if (main) initListe(main);
  const accueil = document.querySelector('[data-biens-accueil]');
  if (accueil) initAccueil(accueil);
  const vendu = document.querySelector('[data-hero-vendu]');
  const une = document.querySelector('[data-hero-une]');
  if (vendu || une) initMosaique(vendu, une);
});
