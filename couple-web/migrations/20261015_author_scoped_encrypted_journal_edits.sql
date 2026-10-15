-- LoveRoll v56: journal edits remain encrypted and restricted to their author.
-- Additive functions: existing data and RLS policies are left intact.
create or replace function public.update_my_love_event(
 p_event uuid,p_happened_on date,p_ciphertext text)
returns void language plpgsql security definer set search_path='' as $$
declare v_couple uuid; v_author uuid; v_old text;
begin
 perform public.love_require_verified_identity();
 if auth.uid() is null then raise exception 'Connexion requise';end if;
 if p_happened_on is null or p_happened_on>(now() at time zone 'America/Toronto')::date then
  raise exception 'Date invalide';end if;
 if p_ciphertext is null or char_length(p_ciphertext) not between 20 and 30000 then
  raise exception 'Contenu chiffré invalide';end if;
 select couple_id,author_id,ciphertext into v_couple,v_author,v_old
 from public.couple_intimacy where id=p_event;
 if not found or v_author<>auth.uid() then raise exception 'Modification interdite';end if;
 perform 1 from public.couples where id=v_couple for update;
 if not found or not couple_private.is_member(v_couple) then
  raise exception 'Vous ne faites plus partie de ce couple';end if;
 update public.couple_intimacy set happened_on=p_happened_on,ciphertext=p_ciphertext
 where id=p_event and author_id=auth.uid();
 -- Preserve independent edits in the private archive.
 update public.love_personal_intimacy
 set happened_on=p_happened_on,ciphertext=p_ciphertext
 where user_id=auth.uid() and source_event_id=p_event and ciphertext=v_old;
end;$$;
revoke all on function public.update_my_love_event(uuid,date,text) from public,anon;
grant execute on function public.update_my_love_event(uuid,date,text) to authenticated;

create or replace function public.update_my_personal_love_event(
 p_archive uuid,p_happened_on date,p_ciphertext text)
returns void language plpgsql security definer set search_path='' as $$
begin
 perform public.love_require_verified_identity();
 if auth.uid() is null then raise exception 'Connexion requise';end if;
 if p_happened_on is null or p_happened_on>(now() at time zone 'America/Toronto')::date then
  raise exception 'Date invalide';end if;
 if p_ciphertext is null or char_length(p_ciphertext) not between 20 and 30000 then
  raise exception 'Contenu chiffré invalide';end if;
 update public.love_personal_intimacy set happened_on=p_happened_on,ciphertext=p_ciphertext
 where id=p_archive and user_id=auth.uid();
 if not found then raise exception 'Note personnelle introuvable ou accès refusé';end if;
 -- A personal edit never modifies the shared couple entry.
end;$$;
revoke all on function public.update_my_personal_love_event(uuid,date,text) from public,anon;
grant execute on function public.update_my_personal_love_event(uuid,date,text) to authenticated;

create or replace function public.delete_my_personal_love_event(p_archive uuid)
returns void language plpgsql security definer set search_path='' as $$
begin
 perform public.love_require_verified_identity();
 if auth.uid() is null then raise exception 'Connexion requise';end if;
 delete from public.love_personal_intimacy where id=p_archive and user_id=auth.uid();
 if not found then raise exception 'Note personnelle introuvable ou accès refusé';end if;
 -- A personal deletion never deletes a shared couple entry.
end;$$;
revoke all on function public.delete_my_personal_love_event(uuid) from public,anon;
grant execute on function public.delete_my_personal_love_event(uuid) to authenticated;

-- An internal trigger requires no direct unauthenticated RPC access.
revoke all on function public.love_push_on_invite_accept() from public,anon;
