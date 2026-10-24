-- Public RPCs are service-only; owner IDs come exclusively from verified Auth users in the function.
create table private.wallet_screen_batches (
 user_id uuid not null references auth.users(id) on delete cascade,
 batch_key text not null check (batch_key ~ '^[0-9a-f]{64}$'),
 state text not null check (state in ('pending','completed','failed')),
 token uuid not null,
 updated_at timestamptz not null default now(),
 result jsonb,
 primary key(user_id,batch_key)
);
alter table private.wallet_screen_batches enable row level security;
revoke all on private.wallet_screen_batches from public,anon,authenticated;
grant select,insert,update,delete on private.wallet_screen_batches to service_role;

create function public.claim_wallet_screen_batch(owner_id uuid,batch_key text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare current_row private.wallet_screen_batches%rowtype; next_token uuid;
begin
 if owner_id is null or batch_key !~ '^[0-9a-f]{64}$' then raise exception 'Invalid batch'; end if;
 -- Serialize per owner: retries and two devices cannot double-charge the same evidence group.
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(owner_id::text,0));
 delete from private.wallet_screen_batches b where b.user_id=owner_id and b.updated_at<now()-interval '32 days';
 select * into current_row from private.wallet_screen_batches b where b.user_id=owner_id and b.batch_key=claim_wallet_screen_batch.batch_key;
 if found and current_row.state='completed' then return jsonb_build_object('status','cached','result',current_row.result); end if;
 if current_row.state='pending' and current_row.updated_at>now()-interval '3 minutes' then return jsonb_build_object('status','busy'); end if;
 if not public.consume_wallet_ai_quota(owner_id) then return jsonb_build_object('status','quota'); end if;
 next_token=pg_catalog.gen_random_uuid();
 insert into private.wallet_screen_batches as b(user_id,batch_key,state,token,updated_at,result)
 values(owner_id,batch_key,'pending',next_token,now(),null)
 on conflict on constraint wallet_screen_batches_pkey do update set state='pending',token=next_token,updated_at=now(),result=null;
 return jsonb_build_object('status','claimed','token',next_token);
end; $$;

create function public.finish_wallet_screen_batch(owner_id uuid,batch_key text,lease_token uuid,batch_result jsonb)
returns boolean language plpgsql security invoker set search_path='' as $$
declare affected integer;
begin
 update private.wallet_screen_batches b set state=case when batch_result is null then 'failed' else 'completed' end,
 result=batch_result,updated_at=now()
 where b.user_id=owner_id and b.batch_key=finish_wallet_screen_batch.batch_key and b.token=lease_token and b.state='pending';
 get diagnostics affected=row_count;
 return affected=1;
end; $$;
revoke all on function public.claim_wallet_screen_batch(uuid,text) from public,anon,authenticated;
revoke all on function public.finish_wallet_screen_batch(uuid,text,uuid,jsonb) from public,anon,authenticated;
grant execute on function public.claim_wallet_screen_batch(uuid,text) to service_role;
grant execute on function public.finish_wallet_screen_batch(uuid,text,uuid,jsonb) to service_role;
