-- Run after bootstrap-local and the migration on a disposable PostgreSQL DB.
set role authenticated;
set request.jwt.claims='{"sub":"11111111-1111-1111-1111-111111111111","session_id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}';
select public.register_device('Android A');
do $$declare result jsonb; begin
 result=public.push_record('p1','portfolio','{"id":"p1","name":"CELI"}',1,null,0);
 assert (result->>'accepted')::boolean,'insert failed';
 assert (result#>>'{record,version}')::int=1,'initial version failed';
 result=public.push_record('p1','portfolio','{"id":"p1","name":"REER"}',1,null,1);
 assert (result#>>'{record,version}')::int=2,'version increment failed';
 result=public.push_record('p1','portfolio','{"id":"p1","name":"stale"}',1,null,1);
 assert not (result->>'accepted')::boolean,'conflicting write not rejected';
 assert result#>>'{record,payload,name}'='REER','conflict overwrote data';
 begin
  insert into public.wallet_records(user_id,id,kind,payload,created_at,updated_at) values('22222222-2222-2222-2222-222222222222','attack','portfolio','{"id":"attack"}',1,1);
  raise exception 'cross-user insert allowed';
 exception when insufficient_privilege then null;end;
end $$;
set request.jwt.claims='{"sub":"22222222-2222-2222-2222-222222222222","session_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}';
select public.register_device('Android B');
do $$begin
 assert (select count(*) from public.wallet_records)=0,'cross-user read leak';
 assert jsonb_array_length(public.pull_records('')->'records')=0,'RPC read leak';
 assert jsonb_array_length(public.list_devices()->'devices')=1,'device list leak';
 update public.wallet_records set payload='{"id":"p1","name":"attack"}' where id='p1';
 assert not found,'cross-user update allowed';
end $$;
set request.jwt.claims='{"sub":"11111111-1111-1111-1111-111111111111","session_id":"cccccccc-cccc-cccc-cccc-cccccccccccc"}';
select public.register_device('Android C');
do $$declare result jsonb;begin
 assert jsonb_array_length(public.pull_records('')->'records')=1,'second device cannot read';
 result=public.push_record('p1','portfolio','{"id":"p1","name":"REER"}',1,123,2);
 assert (result#>>'{record,deleted_at}')::int=123,'tombstone missing';
 assert jsonb_array_length(public.pull_records('')->'records')=1,'tombstone not delivered';
 perform public.revoke_device('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
end $$;
set request.jwt.claims='{"sub":"11111111-1111-1111-1111-111111111111","session_id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"}';
do $$begin
 assert (select count(*) from public.wallet_records)=0,'revoked session read allowed';
 assert not monwallet_private.session_active(),'revoked session active';
 begin
  perform public.register_device('Reactivation');
  raise exception 'revoked session registered';
 exception when others then if SQLERRM<>'invalid session' then raise;end if;end;
end $$;
reset role;
delete from auth.users where id='11111111-1111-1111-1111-111111111111';
do $$begin assert (select count(*) from public.wallet_records)=0,'delete account did not cascade';end $$;
set role anon;
do $$begin
 begin perform * from public.wallet_records;raise exception 'anon read allowed';exception when insufficient_privilege then null;end;
 begin perform public.pull_records('');raise exception 'anon RPC allowed';exception when insufficient_privilege then null;end;
end $$;
reset role;
select 'PASS: ownership, anonymous denial, CAS conflicts, tombstones, multi-device, revocation, deletion' as result;
