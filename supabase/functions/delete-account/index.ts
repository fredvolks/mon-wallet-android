import { createClient } from 'npm:@supabase/supabase-js@2.117.2';
// Service-role credentials exist only in the Edge Function environment.
Deno.serve(async (request: Request) => {
  if (request.method !== 'POST') return Response.json({ error: 'POST required' }, { status: 405 });
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) return Response.json({ error: 'Unauthorized' }, { status: 401 });
  const url = Deno.env.get('SUPABASE_URL')!;
  const publicKey = Deno.env.get('SUPABASE_ANON_KEY')!;
  const admin = createClient(url, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, { auth: { persistSession: false, autoRefreshToken: false } });
  const token = authorization.slice(7);
  const { data: { user }, error } = await admin.auth.getUser(token);
  if (error || !user) return Response.json({ error: 'Unauthorized' }, { status: 401 });
  const scoped = createClient(url, publicKey, { global: { headers: { Authorization: authorization } }, auth: { persistSession: false, autoRefreshToken: false } });
  const active = await scoped.rpc('pull_records', { after_id: '' });
  if (active.error) return Response.json({ error: 'Session revoked' }, { status: 403 });
  const signedOut = await admin.auth.admin.signOut(token, 'global');
  if (signedOut.error) return Response.json({ error: 'Could not revoke sessions' }, { status: 500 });
  const deleted = await admin.auth.admin.deleteUser(user.id);
  if (deleted.error) return Response.json({ error: 'Could not delete account' }, { status: 500 });
  return Response.json({ deleted: true });
});
