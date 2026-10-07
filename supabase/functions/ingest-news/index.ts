// Scheduled service-to-service endpoint. Never expose provider or admin keys to Android.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { XMLParser } from "npm:fast-xml-parser@4.5.3";
type FmpItem = Record<string, unknown>;
type Article = {
  content_hash: string; canonical_url: string; title: string; excerpt: string;
  source: string; provider: string; market: "CANADA" | "USA" | "MACRO";
  tickers: string[]; published_at: string;
};
const CANADA_RSS = "https://www.globenewswire.com/RssFeed/country/canada";
const xmlParser = new XMLParser({ ignoreAttributes: false, removeNSPrefix: true,
  processEntities: false, trimValues: true });

function value(raw: unknown): string {
  if (typeof raw === "string" || typeof raw === "number") return String(raw).trim();
  if (raw && typeof raw === "object") return value((raw as Record<string, unknown>)["#text"] || "");
  return "";
}

function plain(raw: unknown): string {
  return value(raw).replace(/<[^>]*>/g, " ").replace(/&(?:nbsp|#160);/gi, " ")
    .replace(/&amp;/gi, "&").replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">").replace(/\s+/g, " ").trim();
}

// Bare tickers can belong to a different issuer (notably GURU and PHOS).
// Associate only tickers accompanied by a Canadian exchange in the release.
function canadianSymbols(text: string): string[] {
  const symbols = new Set<string>();
  const listing = /\b(TSX[\s-]?VENTURE|TSX[\s-]?V|TSXV|TSX|CSE|NEO|CBOE\s+CANADA)\s*(?:SYMBOL\s*)?[:：]\s*([A-Z][A-Z0-9.-]{0,11})\b/gi;
  for (const match of text.matchAll(listing)) {
    const exchange = match[1].toUpperCase().replace(/[\s-]/g, "");
    const base = match[2].toUpperCase().replace(/\.$/, "");
    const suffix = exchange === "TSX" ? ".TO" :
      exchange === "TSXV" || exchange === "TSXVENTURE" ? ".V" :
      exchange === "CSE" ? ".CN" : ".NE";
    symbols.add(base.endsWith(suffix) ? base : base + suffix);
  }
  return [...symbols].slice(0, 12);
}

function adminKey(): string | null {
  const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}") as Record<string, string>;
  return keys.default || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || null;
}

async function authorized(req: Request, project: string, secret: string): Promise<boolean> {
  if (req.headers.get("apikey") === secret) return true;
  const token = req.headers.get("x-news-cron-token");
  if (!token || !/^[0-9a-f-]{72}$/i.test(token)) return false;
  try {
    const response = await fetch(`${project}/rest/v1/rpc/validate_news_cron_token`, {
      method: "POST", headers: { apikey: secret, "Content-Type": "application/json" },
      body: JSON.stringify({ provided: token }), signal: AbortSignal.timeout(5_000),
    });
    return response.ok && await response.json() === true;
  } catch { return false; }
}

function canonical(raw: string): string | null {
  try {
    const u = new URL(raw);
    if (u.protocol !== "https:") return null;
    for (const key of [...u.searchParams.keys()]) {
      if (key.startsWith("utm_") || ["fbclid", "gclid"].includes(key)) u.searchParams.delete(key);
    }
    u.hash = "";
    return u.toString();
  } catch { return null; }
}

async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(bytes)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function normalize(row: FmpItem): Promise<Article | null> {
  const title = String(row.title || "").trim();
  const url = canonical(String(row.url || ""));
  const source = String(row.site || row.source || "").trim();
  const published = new Date(String(row.publishedDate || row.date || ""));
  if (!title || !url || !source || !Number.isFinite(published.getTime())) return null;
  const age = Date.now() - published.getTime();
  if (age < -600_000 || age > 7 * 24 * 3600_000) return null;
  const symbols = Array.isArray(row.symbols) ? row.symbols :
    String(row.symbol || row.symbols || "").split(",");
  const tickers = symbols.map((s) => String(s).trim().toUpperCase())
    .filter((s) => /^[A-Z^][A-Z0-9.^-]{0,17}$/.test(s)).slice(0, 12);
  // The Canada country feed also contains non-financial press releases. Keep
  // stock news only when the release itself names a Canadian listing.
  if (row.market === "CANADA" && tickers.length === 0) return null;
  const market = row.market === "CANADA" ? "CANADA" :
    tickers.some((s) => /\.(TO|V|NE|CN)$/.test(s)) ? "CANADA" :
    tickers.length ? "USA" : "MACRO";
  const titleKey = title.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
  const contentHash = await digest(titleKey + "|" + published.toISOString().slice(0, 10));
  return {
    content_hash: contentHash, canonical_url: url, title: title.slice(0, 500),
    excerpt: String(row.text || row.content || "").slice(0, 2400),
    source: source.slice(0, 120), provider: String(row.provider || "Financial Modeling Prep"),
    market, tickers, published_at: published.toISOString(),
  };
}

async function canadaFeed(): Promise<FmpItem[]> {
  const response = await fetch(CANADA_RSS, {
    headers: { Accept: "application/rss+xml, application/xml" },
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) throw new Error(`GlobeNewswire Canada RSS HTTP ${response.status}`);
  const xml = await response.text();
  if (xml.length > 1_000_000) throw new Error("Canada RSS response too large");
  const feed = xmlParser.parse(xml) as { rss?: { channel?: { item?: unknown } } };
  const items = feed.rss?.channel?.item;
  if (!items) throw new Error("GlobeNewswire Canada RSS has no items");
  const rows = Array.isArray(items) ? items : [items];
  return rows.filter((item): item is Record<string, unknown> =>
    item !== null && typeof item === "object").map((item) => {
      const title = plain(item.title);
      const description = plain(item.description);
      return { title, url: value(item.link), site: "GlobeNewswire",
        publishedDate: value(item.pubDate || item.date), text: description.slice(0, 1800),
        symbols: canadianSymbols(`${title} ${description}`), market: "CANADA",
        provider: "GlobeNewswire · RSS Canada" };
    });
}

Deno.serve(async (req: Request) => {
  const secret = adminKey();
  if (!secret) return Response.json({ error: "Backend secret not configured" }, { status: 503 });
  const project = Deno.env.get("SUPABASE_URL");
  if (!project) return Response.json({ error: "Project URL missing" }, { status: 503 });
  if (!await authorized(req, project, secret))
    return Response.json({ error: "Unauthorized" }, { status: 401 });
  if (req.method !== "POST") return Response.json({ error: "POST required" }, { status: 405 });
  try {
    const received = await canadaFeed();
    const rows = (await Promise.all(received.map(normalize)))
      .filter((article): article is Article => article !== null);
    const uniqueByUrl = [...new Map(rows.map((article) =>
      [article.canonical_url, article])).values()];
    const unique = [...new Map(uniqueByUrl.map((article) =>
      [article.content_hash, article])).values()];
    const result = await fetch(`${project}/rest/v1/news_articles?on_conflict=content_hash`, {
      method: "POST", headers: { apikey: secret, "Content-Type": "application/json",
        Prefer: "resolution=ignore-duplicates,return=representation" },
      body: JSON.stringify(unique), signal: AbortSignal.timeout(15_000),
    });
    if (!result.ok) throw new Error(`News storage HTTP ${result.status}`);
    const inserted = (await result.json()) as Article[];
    // Keep sourced headlines separate from AI. No OpenAI call or paid feed is
    // made by this job; an unavailable analysis remains visibly non-analysed.
    return Response.json({ received: rows.length, inserted: inserted.length,
      identifiedTickers: rows.filter((row) => row.tickers.length).length });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Ingestion failed" },
      { status: 502 });
  }
});
