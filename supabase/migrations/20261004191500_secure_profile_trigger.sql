-- Keep the signup trigger's privileged function outside the exposed public API.
create function monwallet_private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.wallet_profiles(id, email, display_name, avatar_url, created_at)
  values (new.id, new.email,
    nullif(new.raw_user_meta_data ->> 'full_name', ''),
    nullif(new.raw_user_meta_data ->> 'avatar_url', ''),
    coalesce(new.created_at, now()))
  on conflict (id) do nothing;
  return new;
end;
$$;
revoke all on function monwallet_private.create_profile() from public, anon, authenticated;
drop trigger monwallet_profile_on_signup on auth.users;
create trigger monwallet_profile_on_signup after insert on auth.users
for each row execute function monwallet_private.create_profile();
drop function public.monwallet_create_profile();

revoke all on public.wallet_profiles from anon;
grant select, update on public.wallet_profiles to authenticated;
