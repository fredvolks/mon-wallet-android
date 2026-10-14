create or replace function public.roll_love_occasion_once(
 p_couple uuid,p_kind text,p_owner uuid default null,p_roll text default 'surprise')
returns public.couple_occasion_rolls language plpgsql security definer set search_path = '' as $$
declare localday date; yr int; candidate date; target date; eventyear int;
 eventkey text; first_roll public.couple_occasion_rolls%rowtype;
 rowout public.couple_occasion_rolls%rowtype;n int; owner_id uuid; payload jsonb; ev jsonb; d20 jsonb; modifier jsonb; former public.couple_occasion_rolls%rowtype;
begin
 if auth.uid() is null or not couple_private.is_member(p_couple) then
  raise exception 'Accès refusé';end if;
 if p_kind not in ('valentine','anniversary','mothers_day','fathers_day','womens_day','mens_day','birthday') then
  raise exception 'Occasion invalide';end if;
 if p_roll not in ('surprise','host','modifier') then raise exception 'Type de dé invalide';end if;
 if p_roll='host' and p_kind in ('mothers_day','fathers_day','womens_day','mens_day','birthday','anniversary','valentine')
 then raise exception 'Pour cette fête, la personne qui offre est déjà déterminée';end if;
 if p_kind='birthday' and (p_owner is null or not exists(
   select 1 from public.couple_members where couple_id=p_couple and user_id=p_owner
 )) then raise exception 'L’anniversaire doit appartenir à un membre du couple';end if;
 select owner_user_id into owner_id from public.couples where id=p_couple for update;
 if p_kind in ('mothers_day','fathers_day','womens_day','mens_day') and
  not exists(select 1 from public.couple_occasion_settings o,
   public.couple_members m where o.couple_id=p_couple and m.couple_id=p_couple
   and m.user_id=(o.celebrants->>p_kind)::uuid)
 then raise exception 'L’administrateur doit désigner la personne célébrée';end if;
 localday:=(now() at time zone 'America/Toronto')::date;
 -- Allow planning 14 days ahead for babysitting and 7 days after the event.
 for yr in extract(year from localday)::int-1..extract(year from localday)::int+1 loop
  candidate:=couple_private.love_occasion_date(p_couple,p_kind,p_owner,yr);
  if candidate between localday-7 and localday+14 then
   target:=candidate;eventyear:=yr;exit;
  end if;
 end loop;
 if target is null then raise exception 'Cette occasion n’est pas encore ouverte (14 jours avant, 7 jours après)';end if;
 eventkey:=p_kind||case when p_kind='birthday' then ':'||p_owner::text else '' end;
 select * into rowout from public.couple_occasion_rolls where couple_id=p_couple
  and event_year=eventyear and occasion_key=eventkey and roll_type=p_roll;
 if found then return rowout;end if;
 if p_roll='modifier' then
  select * into former from public.couple_occasion_rolls
    where couple_id=p_couple and event_year=eventyear and occasion_key=eventkey
      and roll_type='surprise';
  if former.couple_id is null then raise exception 'Brassez d’abord le D20';end if;
  select modifier_options into modifier from public.love_special_modifier_snapshots
    where couple_id=p_couple and event_year=eventyear and occasion_key=eventkey;
  if modifier is null then raise exception 'Aucun D6 de modification pour cette fête';end if;
  n:=floor(random()*6)::int+1;
  insert into public.couple_occasion_rolls(couple_id,event_year,occasion_key,roll_type,face,actor,
    modifier_title,modifier_text)
  select p_couple,eventyear,eventkey,p_roll,n,auth.uid(),s.modifier_title,
    s.modifier_options->>(n::text)
  from public.love_special_modifier_snapshots s
  where s.couple_id=p_couple and s.event_year=eventyear and s.occasion_key=eventkey
  returning * into rowout;
 elsif p_roll='host' then
  select * into first_roll from public.couple_occasion_rolls
   where couple_id=p_couple and event_year=eventyear and occasion_key=eventkey
   and roll_type='surprise';
  if not found then raise exception 'Brassez le D20 de la surprise avant le D6';end if;
  n:=floor(random()*6)::int+1;
  insert into public.couple_occasion_rolls(couple_id,event_year,occasion_key,roll_type,face,actor)
  values(p_couple,eventyear,eventkey,p_roll,n) returning * into rowout;
 else
  select events into payload from public.love_special_event_catalogue where couple_id=p_couple;
  if eventyear>=2027 then
   ev:=payload->'evenements_speciaux'->(
    case p_kind when 'valentine' then 'saint_valentin'
    when 'anniversary' then 'anniversaire_de_couple'
    when 'mothers_day' then 'fete_des_meres'
    when 'fathers_day' then 'fete_des_peres'
    when 'birthday' then case when p_owner=owner_id then 'mon_anniversaire'
      else 'anniversaire_de_mon_partenaire' end else '' end);
  end if;
  if ev is not null then
   d20:=ev->'surprise'->'D20_activite';
   n:=(d20->>'resultat')::int;
   insert into public.couple_occasion_rolls(
    couple_id,event_year,occasion_key,roll_type,face,actor,special_title,special_description)
   values(p_couple,eventyear,eventkey,p_roll,n,auth.uid(),d20->>'titre',d20->>'description')
   returning * into rowout;
   insert into public.love_special_modifier_snapshots(
    couple_id,event_year,occasion_key,modifier_title,modifier_options)
   select p_couple,eventyear,eventkey,
    ev->'surprise'->'D6_modificateur'->>'titre',
    jsonb_object_agg(i::text,
     ev->'surprise'->'D6_modificateur'->'options'->(i-1)->>(i::text))
   from generate_series(1,6) i
   on conflict(couple_id,event_year,occasion_key) do nothing;
  else
   n:=floor(random()*20)::int+1;
   insert into public.couple_occasion_rolls(couple_id,event_year,occasion_key,roll_type,face,actor)
   values(p_couple,eventyear,eventkey,p_roll,n) returning * into rowout;
  end if;
 end if;
 insert into public.couple_events(couple_id,event_type,actor_id,title)
 values(p_couple,'roll',auth.uid(),'LoveRoll · '||eventkey||' · '||p_roll);
 return rowout;
end; $$;


revoke all on function public.roll_love_occasion_once(uuid,text,uuid,text) from public,anon;
grant execute on function public.roll_love_occasion_once(uuid,text,uuid,text) to authenticated;
notify pgrst,'reload schema';
