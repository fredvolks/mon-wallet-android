-- A Google or email sign-in creates the minimal Mon Wallet profile automatically.
create table if not exists public.wallet_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  display_name text,
  avatar_url text,
  created_at timestamptz not null default now()
);
alter table public.wallet_profiles enable row level security;
create policy wallet_profile_select on public.wallet_profiles for select to authenticated
  using (id = (select auth.uid()));
create policy wallet_profile_update on public.wallet_profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

create or replace function public.monwallet_create_profile() returns trigger
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
revoke all on function public.monwallet_create_profile() from public;
drop trigger if exists monwallet_profile_on_signup on auth.users;
create trigger monwallet_profile_on_signup after insert on auth.users
for each row execute function public.monwallet_create_profile();

insert into public.wallet_profiles(id, email, display_name, avatar_url, created_at)
select id, email, nullif(raw_user_meta_data ->> 'full_name', ''),
  nullif(raw_user_meta_data ->> 'avatar_url', ''), coalesce(created_at, now())
from auth.users on conflict (id) do nothing;
