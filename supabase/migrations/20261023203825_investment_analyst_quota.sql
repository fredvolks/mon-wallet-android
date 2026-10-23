create schema if not exists private;
create table if not exists private.wallet_ai_quota (
  user_id uuid not null,
  quota_day date not null,
  requests integer not null check (requests between 0 and 20),
  primary key(user_id, quota_day)
);
alter table private.wallet_ai_quota enable row level security;
revoke all on private.wallet_ai_quota from public, anon, authenticated;
grant usage on schema private to service_role;
grant select, insert, update on private.wallet_ai_quota to service_role;
create or replace function public.consume_wallet_ai_quota(owner_id uuid)
returns boolean language plpgsql security invoker set search_path = '' as $$
declare accepted integer;
begin
  insert into private.wallet_ai_quota(user_id, quota_day, requests)
  values(owner_id, (now() at time zone 'UTC')::date, 1)
  on conflict(user_id, quota_day) do update
    set requests = private.wallet_ai_quota.requests + 1
    where private.wallet_ai_quota.requests < 20
  returning requests into accepted;
  return accepted is not null;
end;
$$;
revoke all on function public.consume_wallet_ai_quota(uuid) from public, anon, authenticated;
grant execute on function public.consume_wallet_ai_quota(uuid) to service_role;