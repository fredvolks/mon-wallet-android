-- Shared sourced headlines. User portfolio/watchlist data remains in wallet_records.
create table if not exists public.news_articles (
    id uuid primary key default gen_random_uuid(),
    content_hash text not null unique,
    canonical_url text not null unique,
    title text not null,
    excerpt text,
    source text not null,
    provider text not null,
    market text not null check (market in ('CANADA', 'USA', 'MACRO')),
    tickers text[] not null default '{}',
    published_at timestamptz not null,
    ingested_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    status text not null default 'PENDING'
        check (status in ('PENDING', 'PROCESSING', 'ANALYZED', 'ERROR'))
);

create index if not exists news_articles_published_at_idx
    on public.news_articles (published_at desc);
create index if not exists news_articles_tickers_idx
    on public.news_articles using gin (tickers);
create index if not exists news_articles_status_idx
    on public.news_articles (status, updated_at);

create table if not exists public.news_analysis (
    article_id uuid primary key references public.news_articles(id) on delete cascade,
    summary_fr text not null,
    importance text not null check (importance in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    sentiment text not null check (sentiment in ('POSITIVE', 'NEGATIVE', 'NEUTRAL', 'MIXED')),
    event_type text not null,
    why_it_matters text not null,
    short_term_impact text not null,
    long_term_impact text not null,
    confidence numeric(4,3) not null check (confidence between 0 and 1),
    notification_worthy boolean not null default false,
    key_numbers jsonb not null default '[]'::jsonb,
    model text not null,
    analysis_version integer not null default 1,
    processed_at timestamptz not null default now()
);

alter table public.news_articles enable row level security;
alter table public.news_analysis enable row level security;

revoke all on public.news_articles from anon, authenticated;
revoke all on public.news_analysis from anon, authenticated;
grant select on public.news_articles to anon, authenticated;
grant select on public.news_analysis to anon, authenticated;

create policy "Read published sourced headlines"
    on public.news_articles for select to anon, authenticated
    using (published_at <= now() and status in ('PENDING', 'PROCESSING', 'ANALYZED', 'ERROR'));
create policy "Read analyses of published headlines"
    on public.news_analysis for select to anon, authenticated
    using (exists (
        select 1 from public.news_articles a
        where a.id = article_id and a.published_at <= now()
    ));
