-- Lot 4 : annonceur, précision de position, nouveaux types, dates par source,
-- rapport de couverture. Idempotent (relançable) ; exécuté par installer-supabase.yml
-- avant schema.sql, qui recrée ensuite les vues.

-- Qui publie : particulier / agence / notaire / inconnu.
alter table annonces add column if not exists annonceur text not null default 'inconnu';
alter table annonces drop constraint if exists annonces_annonceur_check;
alter table annonces add constraint annonces_annonceur_check
    check (annonceur in ('particulier','agence','notaire','inconnu'));

-- Position : « exacte » (GPS de la source) ou « commune » (centre de la commune).
alter table annonces add column if not exists precision_position text;

-- Nouveaux types de biens (chambre/colocation, immeuble, commerce/bureau, garage/parking).
alter table annonces drop constraint if exists annonces_type_check;
alter table annonces add constraint annonces_type_check
    check (type in ('appartement','maison','studio','kot','chambre','terrain','immeuble',
                    'commerce','garage','autre'));

-- Date de publication conservée par source (C7).
alter table annonce_sources add column if not exists date_publication date;

-- Journal des collectes : passage profond, couverture, particuliers.
alter table collectes add column if not exists profond boolean not null default false;
alter table collectes add column if not exists nb_disponibles integer;
alter table collectes add column if not exists nb_particuliers integer not null default 0;

-- Reprise des données existantes : plus jamais « PRIVATE » affiché comme agence.
update annonces a set annonceur = case
        when a.agence = 'PRIVATE' then 'particulier'
        when exists (select 1 from annonce_sources x where x.annonce_id = a.id and x.source = 'notaire')
            then 'notaire'
        when exists (select 1 from annonce_sources x where x.annonce_id = a.id and x.source like 'agences:%')
            then 'agence'
        when a.agence is not null then 'agence'
        else 'inconnu' end
    where a.annonceur = 'inconnu';
update annonces set agence = null where agence = 'PRIVATE' or annonceur = 'particulier';

-- Annonces actives sans aucune publication (C4) : désactivées.
update annonces a set actif = false
    where a.actif and not exists (select 1 from annonce_sources x where x.annonce_id = a.id);
