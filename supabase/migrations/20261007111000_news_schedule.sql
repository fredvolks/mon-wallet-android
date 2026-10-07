-- Run news ingestion and failed-analysis retries entirely on the backend.
-- A random per-project token is encrypted in Vault. Its value is never returned
-- to Android, committed to Git, or embedded in a cron job definition.
create extension if not exists pg_net;
create extension if not exists pg_cron;

do $$
begin
    if not exists (select 1 from vault.secrets where name = 'monwallet_news_cron_token') then
        perform vault.create_secret(
            gen_random_uuid()::text || gen_random_uuid()::text,
            'monwallet_news_cron_token',
            'Authorizes Mon Wallet news jobs only'
        );
    end if;
end $$;

create or replace function public.validate_news_cron_token(provided text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select coalesce((
        select provided = decrypted_secret
        from vault.decrypted_secrets
        where name = 'monwallet_news_cron_token'
    ), false);
$$;

revoke all on function public.validate_news_cron_token(text) from public, anon, authenticated;
grant execute on function public.validate_news_cron_token(text) to service_role;

select cron.schedule('monwallet-news-ingest', '*/30 * * * *', $$
    select net.http_post(
        url := 'https://prwlnxbqttxfjxpehmok.supabase.co/functions/v1/ingest-news',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-news-cron-token', (select decrypted_secret from vault.decrypted_secrets
                                  where name = 'monwallet_news_cron_token')
        ),
        body := '{}'::jsonb,
        timeout_milliseconds := 20000
    );
$$);

select cron.schedule('monwallet-news-analyze', '*/15 * * * *', $$
    select net.http_post(
        url := 'https://prwlnxbqttxfjxpehmok.supabase.co/functions/v1/analyze-news',
        headers := jsonb_build_object(
            'Content-Type', 'application/json',
            'x-news-cron-token', (select decrypted_secret from vault.decrypted_secrets
                                  where name = 'monwallet_news_cron_token')
        ),
        body := '{}'::jsonb,
        timeout_milliseconds := 20000
    );
$$);

-- Activate only after the real provider and OpenAI calls pass. An API key
-- existing in Edge Function Secrets does not prove dataset or credit access.
select cron.alter_job(jobid, active := false)
from cron.job
where jobname in ('monwallet-news-ingest', 'monwallet-news-analyze');
