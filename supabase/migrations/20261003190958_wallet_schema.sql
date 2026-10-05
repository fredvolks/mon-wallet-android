-- Mon Wallet private records. No market data and no service-role key in the APK.
create schema if not exists monwallet_private;
revoke all on schema monwallet_private from public, anon;
grant usage on schema monwallet_private to authenticated;
create table public.wallet_records (
 user_id uuid not null references auth.users(id) on delete cascade,
 id text not null check (length(id) between 1 and 120),
 kind text not null check (kind in ('portfolio','security','transaction','watchlist','watch_item','alert','notification_preference','event','setting')),
 payload jsonb not null check (jsonb_typeof(payload)='object' and payload ? 'id' and jsonb_typeof(payload->'id')='string' and payload->>'id'=id and octet_length(payload::text)<262144),
 created_at bigint not null, updated_at bigint not null,
 deleted_at bigint, version bigint not null default 1,
 primary key(user_id,id)
);
create index wallet_records_user_kind on public.wallet_records(user_id,kind);
create table public.wallet_devices (
 session_id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
 name text not null check(length(name)<=120), last_seen bigint not null,
 revoked boolean not null default false
);
create index wallet_devices_user_id on public.wallet_devices(user_id);
alter table public.wallet_records enable row level security;
alter table public.wallet_devices enable row level security;
-- The only SECURITY DEFINER helper returns a boolean for the calling JWT only.
-- auth.sessions cannot be exposed to app clients. Keep monwallet_private unexposed.
create function monwallet_private.session_active() returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null
 and exists(select 1 from auth.sessions s where s.id=(auth.jwt()->>'session_id')::uuid and s.user_id=auth.uid())
 and not exists(select 1 from public.wallet_devices d where d.session_id=(auth.jwt()->>'session_id')::uuid and d.revoked)
$$;
revoke all on function monwallet_private.session_active() from public,anon;
grant execute on function monwallet_private.session_active() to authenticated;
create policy records_read on public.wallet_records for select to authenticated using(user_id=(select auth.uid()) and (select monwallet_private.session_active()));
create policy records_insert on public.wallet_records for insert to authenticated with check(user_id=(select auth.uid()) and (select monwallet_private.session_active()));
create policy records_update on public.wallet_records for update to authenticated using(user_id=(select auth.uid()) and (select monwallet_private.session_active())) with check(user_id=(select auth.uid()) and (select monwallet_private.session_active()));
create policy devices_read on public.wallet_devices for select to authenticated using(user_id=(select auth.uid()) and (select monwallet_private.session_active()));
create policy devices_insert on public.wallet_devices for insert to authenticated with check(user_id=(select auth.uid()) and session_id=(select (auth.jwt()->>'session_id')::uuid) and not revoked and (select monwallet_private.session_active()));
create policy devices_update on public.wallet_devices for update to authenticated using(user_id=(select auth.uid()) and (select monwallet_private.session_active())) with check(user_id=(select auth.uid()));
revoke all on public.wallet_records,public.wallet_devices from anon,authenticated;
grant select,insert,update on public.wallet_records,public.wallet_devices to authenticated;
create function monwallet_private.stamp_record() returns trigger language plpgsql set search_path='' as $$
begin
 if TG_OP='UPDATE' then
  if new.user_id<>old.user_id or new.id<>old.id or new.kind<>old.kind then raise exception 'immutable identity'; end if;
  new.created_at=old.created_at; new.version=old.version+1;
 else new.version=1; end if;
 new.updated_at=floor(extract(epoch from clock_timestamp())*1000)::bigint;
 return new;
end $$;
create trigger stamp_record before insert or update on public.wallet_records for each row execute function monwallet_private.stamp_record();
create function monwallet_private.guard_device() returns trigger language plpgsql set search_path='' as $$
begin
 if new.user_id<>old.user_id or new.session_id<>old.session_id then raise exception 'immutable identity'; end if;
 if old.revoked and not new.revoked then raise exception 'revoked session'; end if;
 return new;
end $$;
create trigger guard_device before update on public.wallet_devices for each row execute function monwallet_private.guard_device();
create function public.register_device(device_name text) returns jsonb language plpgsql security invoker set search_path='' as $$
begin
 if not monwallet_private.session_active() then raise exception 'invalid session'; end if;
 insert into public.wallet_devices(session_id,user_id,name,last_seen) values((auth.jwt()->>'session_id')::uuid,auth.uid(),left(device_name,120),floor(extract(epoch from clock_timestamp())*1000)::bigint)
 on conflict(session_id) do update set name=excluded.name,last_seen=excluded.last_seen;
 return jsonb_build_object('ok',true);
end $$;
create function public.list_devices() returns jsonb language plpgsql security invoker set search_path='' as $$
begin
 if not monwallet_private.session_active() then raise exception 'invalid session'; end if;
 return jsonb_build_object('devices',coalesce((select jsonb_agg(to_jsonb(d) order by d.last_seen desc) from public.wallet_devices d where d.user_id=auth.uid()),'[]'::jsonb));
end $$;
create function public.revoke_device(target_session uuid) returns jsonb language plpgsql security invoker set search_path='' as $$
begin
 if not monwallet_private.session_active() then raise exception 'invalid session'; end if;
 update public.wallet_devices set revoked=true where session_id=target_session and user_id=auth.uid();
 return jsonb_build_object('ok',true);
end $$;
create function public.push_record(record_id text,record_kind text,record_payload jsonb,record_created_at bigint,record_deleted_at bigint,expected_version bigint) returns jsonb language plpgsql security invoker set search_path='' as $$
declare previous public.wallet_records; saved public.wallet_records;
begin
 if not monwallet_private.session_active() then raise exception 'invalid session'; end if;
 perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text||':'||record_id,0));
 select * into previous from public.wallet_records where user_id=auth.uid() and id=record_id for update;
 if found then
  if previous.version<>expected_version then return jsonb_build_object('accepted',false,'record',to_jsonb(previous)); end if;
  if previous.kind<>record_kind then raise exception 'immutable record kind'; end if;
  update public.wallet_records set payload=record_payload,deleted_at=record_deleted_at where user_id=auth.uid() and id=record_id returning * into saved;
 else
  if expected_version<>0 then raise exception 'unknown record version'; end if;
  insert into public.wallet_records(user_id,id,kind,payload,created_at,updated_at,deleted_at) values(auth.uid(),record_id,record_kind,record_payload,record_created_at,record_created_at,record_deleted_at) returning * into saved;
 end if;
 return jsonb_build_object('accepted',true,'record',to_jsonb(saved));
end $$;
create function public.pull_records(after_id text default '') returns jsonb language plpgsql security invoker set search_path='' as $$
begin
 if not monwallet_private.session_active() then raise exception 'invalid session'; end if;
 return jsonb_build_object('records',coalesce((select jsonb_agg(to_jsonb(r) order by r.id) from (select * from public.wallet_records where user_id=auth.uid() and id>after_id order by id limit 200) r),'[]'::jsonb));
end $$;
revoke all on function public.register_device(text),public.list_devices(),public.revoke_device(uuid),public.push_record(text,text,jsonb,bigint,bigint,bigint),public.pull_records(text) from public,anon;
grant execute on function public.register_device(text),public.list_devices(),public.revoke_device(uuid),public.push_record(text,text,jsonb,bigint,bigint,bigint),public.pull_records(text) to authenticated;
revoke all on function monwallet_private.stamp_record(),monwallet_private.guard_device() from public,anon,authenticated;
