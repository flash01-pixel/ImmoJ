-- ============================================================================
-- ImmoJ — schéma de la base de données Supabase (Postgres)
--
-- À exécuter UNE fois dans Supabase : menu « SQL Editor » → « New query »,
-- coller tout ce fichier, cliquer « Run ». Voir docs/02-configurer-supabase.md.
-- Ensuite exécuter policies.sql (règles de sécurité).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Référentiel des communes (44 communes de la province de Luxembourg).
-- Utilisé par l'appli pour la liste de sélection et par la vue carte.
-- ---------------------------------------------------------------------------
create table if not exists communes (
    nom          text primary key,
    codes        text[] not null,
    latitude     double precision not null,
    longitude    double precision not null
);

-- ---------------------------------------------------------------------------
-- Un BIEN (après dédoublonnage). Une ligne par logement, quel que soit le
-- nombre de sites qui le publient.
-- ---------------------------------------------------------------------------
create table if not exists annonces (
    id                  uuid primary key default gen_random_uuid(),
    type                text not null check (type in ('appartement','maison','studio','kot','chambre','terrain',
                                                      'immeuble','commerce','garage','autre')),
    transaction         text not null check (transaction in ('location','achat')),
    titre               text,
    prix                integer,                 -- loyer mensuel ou prix de vente, en euros
    prix_precedent      integer,                 -- prix avant le dernier changement
    prix_baisse_le      timestamptz,             -- date de la dernière baisse (badge « Prix baissé »)
    charges             integer,
    commune             text references communes(nom),  -- commune officielle (géocodée)
    localite            text,                    -- localité telle qu'affichée par la source
    code_postal         text,
    latitude            double precision,
    longitude           double precision,
    chambres            integer,
    surface             integer,
    peb                 text,
    jardin              boolean,
    terrasse            boolean,
    animaux             boolean,
    meuble              boolean,
    date_publication    date,
    premiere_detection  timestamptz not null default now(),
    derniere_vue        timestamptz not null default now(),
    photos              text[] not null default '{}',
    description         text,
    details             jsonb not null default '{}'::jsonb,  -- infos complémentaires (libellé → valeur)
    agence              text,
    telephone           text,
    annonceur           text not null default 'inconnu'
                        check (annonceur in ('particulier','agence','notaire','inconnu')),
    precision_position  text,                    -- 'exacte' (GPS de la source) ou 'commune' (centre)
    actif               boolean not null default true
);
-- Projets créés avant l'ajout de colonnes (voir aussi supabase/migrations/) :
alter table annonces add column if not exists details jsonb not null default '{}'::jsonb;
alter table annonces add column if not exists annonceur text not null default 'inconnu';
alter table annonces add column if not exists precision_position text;
create index if not exists annonces_actif_transaction_idx on annonces (actif, transaction);
create index if not exists annonces_commune_idx on annonces (commune);
create index if not exists annonces_premiere_detection_idx on annonces (premiere_detection desc);

-- ---------------------------------------------------------------------------
-- Une PUBLICATION : le même bien vu sur un site donné.
-- ---------------------------------------------------------------------------
create table if not exists annonce_sources (
    id            bigint generated always as identity primary key,
    annonce_id    uuid not null references annonces(id) on delete cascade,
    source        text not null,                 -- 'immovlan', 'zimmo', 'agence:honesty'...
    id_externe    text not null,                 -- identifiant chez la source
    url           text not null,
    titre         text,
    prix_affiche  integer,
    agence        text,
    telephone     text,
    date_publication date,                       -- date de publication sur CETTE source (C7)
    premiere_vue  timestamptz not null default now(),
    derniere_vue  timestamptz not null default now(),
    unique (source, id_externe)
);
alter table annonce_sources add column if not exists date_publication date;
create index if not exists annonce_sources_annonce_idx on annonce_sources (annonce_id);

-- ---------------------------------------------------------------------------
-- Historique des prix (une ligne à chaque changement, et une à la création).
-- ---------------------------------------------------------------------------
create table if not exists historique_prix (
    id          bigint generated always as identity primary key,
    annonce_id  uuid not null references annonces(id) on delete cascade,
    prix        integer not null,
    source      text,
    date        timestamptz not null default now()
);
create index if not exists historique_prix_annonce_idx on historique_prix (annonce_id, date desc);

-- ---------------------------------------------------------------------------
-- Mes statuts personnels (écrits par l'appli).
-- ---------------------------------------------------------------------------
create table if not exists statuts (
    annonce_id  uuid primary key references annonces(id) on delete cascade,
    statut      text not null default 'nouveau'
                check (statut in ('nouveau','vu','interesse','contacte','visite_prevue','refuse')),
    note        text,
    modifie_le  timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Recherches sauvegardées (créées dans l'appli, lues par le collecteur).
-- Un critère NULL = non appliqué. communes ET/OU rayon : l'un des deux suffit.
-- ---------------------------------------------------------------------------
create table if not exists recherches_sauvegardees (
    id            uuid primary key default gen_random_uuid(),
    nom           text not null,
    transaction   text check (transaction in ('location','achat')),
    communes      text[],
    centre_lat    double precision,
    centre_lon    double precision,
    rayon_km      double precision,
    prix_min      integer,
    prix_max      integer,
    types         text[],
    chambres_min  integer,
    surface_min   integer,
    jardin        boolean,
    animaux       boolean,
    meuble        boolean,
    peb_max       text,
    sources       text[],
    notifier      boolean not null default true,
    actif         boolean not null default true,
    cree_le       timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Notifications déjà envoyées (évite les doublons entre deux exécutions).
-- recherche_cle = recherche_id ou '' : permet une contrainte unique même
-- quand recherche_id est NULL (notification « sans recherche »).
-- ---------------------------------------------------------------------------
create table if not exists notifications_envoyees (
    id            bigint generated always as identity primary key,
    annonce_id    uuid not null references annonces(id) on delete cascade,
    type          text not null,                 -- 'nouvelle' ou 'baisse:<prix>'
    recherche_id  uuid references recherches_sauvegardees(id) on delete cascade,
    recherche_cle text generated always as (coalesce(recherche_id::text, '')) stored,
    envoyee_le    timestamptz not null default now(),
    unique (annonce_id, type, recherche_cle)
);

-- ---------------------------------------------------------------------------
-- Journal des collectes (écran « État des sources »).
-- ---------------------------------------------------------------------------
create table if not exists collectes (
    id            bigint generated always as identity primary key,
    source        text not null,
    debut         timestamptz not null,
    fin           timestamptz not null default now(),
    ok            boolean not null,
    bloque        boolean not null default false,
    nb_trouvees   integer not null default 0,
    nb_nouvelles  integer not null default 0,
    duree_s       real,
    erreur        text,
    profond       boolean not null default false,  -- passage profond (toutes les pages)
    nb_disponibles integer,                        -- annonces annoncées par le site
    nb_particuliers integer not null default 0
);
alter table collectes add column if not exists profond boolean not null default false;
alter table collectes add column if not exists nb_disponibles integer;
alter table collectes add column if not exists nb_particuliers integer not null default 0;
create index if not exists collectes_source_debut_idx on collectes (source, debut desc);

-- ============================================================================
-- Vues et fonctions pour l'appli
-- ============================================================================

-- Liste des annonces avec statut, badges et sources agrégées, en une requête.
-- « a.* » change quand on ajoute une colonne : on recrée la vue (et la fonction
-- annonces_dans_rayon qui en dépend, redéfinie plus bas).
drop view if exists v_annonces_liste cascade;
create view v_annonces_liste as
select
    a.*,
    a.photos[1]                                                    as photo,   -- vignette de liste
    coalesce(s.statut, 'nouveau')                                  as statut,
    s.note,
    (a.premiere_detection > now() - interval '48 hours'
        and coalesce(s.statut, 'nouveau') = 'nouveau')             as badge_nouveau,
    (a.prix_baisse_le is not null
        and a.prix_baisse_le > now() - interval '7 days')          as badge_prix_baisse,
    coalesce(src.sources, '{}'::text[])                            as sources,
    coalesce(src.urls, '{}'::text[])                               as urls
from annonces a
left join statuts s on s.annonce_id = a.id
left join lateral (
    select array_agg(x.source order by x.premiere_vue) as sources,
           array_agg(x.url order by x.premiere_vue)    as urls
    from annonce_sources x where x.annonce_id = a.id
) src on true;

-- État des sources : dernière collecte réussie, dernière erreur, nb d'annonces actives.
drop view if exists v_etat_sources;
create view v_etat_sources as
with derniere as (
    select distinct on (source) source, debut, fin, ok, bloque, erreur, nb_trouvees, nb_nouvelles,
           profond, nb_disponibles, nb_particuliers
    from collectes order by source, debut desc
),
derniere_ok as (
    select source, max(fin) as derniere_reussite from collectes where ok group by source
),
derniere_profonde as (
    select source, max(fin) as derniere_profonde from collectes where ok and profond group by source
),
actives as (
    select x.source, count(distinct x.annonce_id) as nb_actives
    from annonce_sources x join annonces a on a.id = x.annonce_id
    where a.actif group by x.source
)
select d.source,
       d.fin               as derniere_collecte,
       d.ok                as derniere_ok,
       d.bloque,
       d.erreur,
       d.nb_trouvees,
       d.nb_nouvelles,
       o.derniere_reussite,
       coalesce(ac.nb_actives, 0) as nb_actives,
       d.profond,
       d.nb_disponibles,
       d.nb_particuliers,
       p.derniere_profonde
from derniere d
left join derniere_ok o on o.source = d.source
left join derniere_profonde p on p.source = d.source
left join actives ac on ac.source = d.source;

-- Distance à vol d'oiseau en km (haversine), sans extension PostGIS.
create or replace function distance_km(lat1 double precision, lon1 double precision,
                                       lat2 double precision, lon2 double precision)
returns double precision language sql immutable as $$
    select 2 * 6371 * asin(sqrt(
        power(sin(radians(lat2 - lat1) / 2), 2) +
        cos(radians(lat1)) * cos(radians(lat2)) * power(sin(radians(lon2 - lon1) / 2), 2)));
$$;

-- Annonces dans un rayon autour d'un point : appelée par l'appli via /rest/v1/rpc/annonces_dans_rayon
create or replace function annonces_dans_rayon(lat double precision, lon double precision, km double precision)
returns setof v_annonces_liste language sql stable as $$
    select * from v_annonces_liste v
    where v.latitude is not null
      and distance_km(lat, lon, v.latitude, v.longitude) <= km;
$$;

-- ============================================================================
-- Données de référence : les 44 communes
-- ============================================================================
insert into communes (nom, codes, latitude, longitude) values
  ('Arlon', '{6700,6704,6706}', 49.683, 5.817),
  ('Attert', '{6717}', 49.750, 5.783),
  ('Aubange', '{6790,6791,6792}', 49.567, 5.800),
  ('Martelange', '{6630}', 49.833, 5.733),
  ('Messancy', '{6780,6781,6782}', 49.600, 5.817),
  ('Bastogne', '{6600}', 50.000, 5.717),
  ('Bertogne', '{6686,6687,6688}', 50.083, 5.667),
  ('Fauvillers', '{6637}', 49.850, 5.667),
  ('Gouvy', '{6670,6671,6672,6673,6674}', 50.183, 5.933),
  ('Houffalize', '{6660,6661,6662,6663,6666}', 50.133, 5.783),
  ('Sainte-Ode', '{6680,6681}', 50.017, 5.517),
  ('Vaux-sur-Sûre', '{6640,6642}', 49.917, 5.583),
  ('Vielsalm', '{6690,6692,6698}', 50.283, 5.917),
  ('Durbuy', '{6940,6941}', 50.350, 5.450),
  ('Érezée', '{6997}', 50.283, 5.550),
  ('Hotton', '{6990}', 50.267, 5.450),
  ('La Roche-en-Ardenne', '{6980,6982,6983,6984,6986}', 50.183, 5.583),
  ('Manhay', '{6960}', 50.300, 5.683),
  ('Marche-en-Famenne', '{6900}', 50.233, 5.333),
  ('Nassogne', '{6950,6951,6952,6953}', 50.133, 5.350),
  ('Rendeux', '{6987}', 50.233, 5.500),
  ('Tenneville', '{6970,6971,6972}', 50.100, 5.533),
  ('Bertrix', '{6880}', 49.850, 5.250),
  ('Bouillon', '{6830,6831,6832,6833,6834,6836}', 49.800, 5.067),
  ('Daverdisse', '{6929}', 50.017, 5.117),
  ('Herbeumont', '{6887}', 49.783, 5.233),
  ('Léglise', '{6860}', 49.800, 5.533),
  ('Libin', '{6890}', 49.983, 5.250),
  ('Libramont-Chevigny', '{6800}', 49.917, 5.383),
  ('Neufchâteau', '{6840}', 49.850, 5.433),
  ('Paliseul', '{6850,6851,6852,6853}', 49.900, 5.133),
  ('Saint-Hubert', '{6870}', 50.033, 5.383),
  ('Tellin', '{6927}', 50.083, 5.217),
  ('Wellin', '{6920,6921,6922,6924}', 50.083, 5.117),
  ('Chiny', '{6810,6811,6812,6813}', 49.733, 5.333),
  ('Étalle', '{6740,6741,6742,6743}', 49.667, 5.600),
  ('Florenville', '{6820,6821,6823,6824}', 49.700, 5.300),
  ('Habay', '{6720,6721,6723,6724}', 49.717, 5.633),
  ('Meix-devant-Virton', '{6769}', 49.600, 5.483),
  ('Musson', '{6750}', 49.550, 5.700),
  ('Rouvroy', '{6767}', 49.533, 5.483),
  ('Saint-Léger', '{6747}', 49.617, 5.650),
  ('Tintigny', '{6730}', 49.683, 5.517),
  ('Virton', '{6760,6761,6762}', 49.567, 5.533)
on conflict (nom) do nothing;
