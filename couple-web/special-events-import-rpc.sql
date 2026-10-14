-- Save the six original user-written event descriptions exactly as JSON.
create or replace function public.import_love_special_events(p_couple uuid,p_events jsonb)
returns void language plpgsql security definer set search_path='' as $$
declare k text; e jsonb; d20 jsonb; d6 jsonb; options jsonb; n int;
begin
 if auth.uid() is null then raise exception 'Connexion requise';end if;
 perform 1 from public.couples where id=p_couple for update;
 if not found or not couple_private.is_member(p_couple)
 or not exists(select 1 from public.couples c where c.id=p_couple
  and c.owner_user_id=auth.uid())
 then raise exception 'Seul le créateur du couple peut importer les événements';end if;
 if jsonb_typeof(p_events->'evenements_speciaux')<>'object'
 or (select count(*) from jsonb_object_keys(p_events->'evenements_speciaux'))<>6
 then raise exception 'Six événements spéciaux sont requis';end if;
 foreach k in array array['saint_valentin','anniversaire_de_couple',
 'fete_des_meres','fete_des_peres','mon_anniversaire',
 'anniversaire_de_mon_partenaire'] loop
  e:=p_events->'evenements_speciaux'->k;
  d20:=e->'surprise'->'D20_activite';
  d6:=e->'surprise'->'D6_modificateur';
  options:=d6->'options';
  if e is null
   or length(btrim(coalesce(e->>'evenement',''))) not between 3 and 120
   or length(btrim(coalesce(e->>'prochaine_fete',''))) not between 4 and 50
   or coalesce(d20->>'resultat','') !~ '^[0-9]{1,2}$'
   or (d20->>'resultat')::int not between 1 and 20
   or length(btrim(coalesce(d20->>'titre',''))) not between 3 and 160
   or length(btrim(coalesce(d20->>'description',''))) not between 10 and 2000
   or length(btrim(coalesce(d6->>'titre',''))) not between 3 and 160
   or jsonb_typeof(options)<>'array'
   or jsonb_array_length(options)<>6
  then raise exception 'Événement incorrect: %',k;end if;
  for n in 1..6 loop
   if jsonb_typeof(options->(n-1))<>'object'
    or (select count(*) from jsonb_object_keys(options->(n-1)))<>1
    or length(btrim(coalesce(options->(n-1)->>(n::text),''))) not between 5 and 900
   then raise exception 'Option D6 % incorrecte pour %',n,k;end if;
  end loop;
 end loop;
 insert into public.love_special_event_catalogue(couple_id,events)
 values(p_couple,p_events)
 on conflict(couple_id) do update set events=excluded.events,updated_at=now();
end;$$;
revoke all on function public.import_love_special_events(uuid,jsonb) from public,anon;
grant execute on function public.import_love_special_events(uuid,jsonb) to authenticated;
