-- ============================================================================
-- ImmoJ — règles de sécurité (Row Level Security)
--
-- À exécuter après schema.sql, dans le SQL Editor de Supabase.
--
-- Principe :
--  * le collecteur (GitHub Actions) utilise la clé `service_role`, qui ignore
--    ces règles : il peut tout faire ;
--  * l'appli Android utilise la clé `anon` (publishable) : elle peut LIRE toutes
--    les tables, et n'ÉCRIRE que dans `statuts` et `recherches_sauvegardees`.
-- Même si la clé anon fuitait, personne ne pourrait effacer les annonces.
-- ============================================================================

alter table communes                 enable row level security;
alter table annonces                 enable row level security;
alter table annonce_sources          enable row level security;
alter table historique_prix          enable row level security;
alter table statuts                  enable row level security;
alter table recherches_sauvegardees  enable row level security;
alter table notifications_envoyees   enable row level security;
alter table collectes                enable row level security;

-- Lecture pour l'appli
create policy "lecture anon" on communes                for select to anon using (true);
create policy "lecture anon" on annonces                for select to anon using (true);
create policy "lecture anon" on annonce_sources         for select to anon using (true);
create policy "lecture anon" on historique_prix         for select to anon using (true);
create policy "lecture anon" on statuts                 for select to anon using (true);
create policy "lecture anon" on recherches_sauvegardees for select to anon using (true);
create policy "lecture anon" on collectes               for select to anon using (true);

-- Écriture pour l'appli : uniquement mes statuts et mes recherches
create policy "ecriture anon" on statuts
    for all to anon using (true) with check (true);
create policy "ecriture anon" on recherches_sauvegardees
    for all to anon using (true) with check (true);
