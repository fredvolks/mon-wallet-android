// Scheduled service-to-service endpoint. Never expose provider or admin keys to Android.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
type FmpItem = Record<string, unknown>;
type Article = {
  content_hash: string; canonical_url: string; title: string; excerpt: string;
  source: string; provider: string; market: "CANADA" | "USA" | "MACRO";
  tickers: string[]; published_at: string;
};

function adminKey(): string | null {
  const keys = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}") as Record<string, string>;
  return keys.default || Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || null;
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
  const market = tickers.some((s) => /\.(TO|V|NE|CN)$/.test(s)) ? "CANADA" :
    tickers.length ? "USA" : "MACRO";
  const titleKey = title.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
  const contentHash = await digest(titleKey + "|" + published.toISOString().slice(0, 10));
  return {
    content_hash: contentHash, canonical_url: url, title: title.slice(0, 500),
    excerpt: String(row.text || row.content || "").slice(0, 2400),
    source: source.slice(0, 120), provider: "Financial Modeling Prep",
    market, tickers, published_at: published.toISOString(),
  };
}

async function fmp(path: string, key: string): Promise<FmpItem[]> {
  const u = new URL(`https://financialmodelingprep.com/stable/news/${path}`);
  u.searchParams.set("page", "0"); u.searchParams.set("limit", "100");
  u.searchParams.set("apikey", key);
  const response = await fetch(u, { signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw new Error(`FMP HTTP ${response.status}`);
  const data: unknown = await response.json();
  if (!Array.isArray(data)) throw new Error("FMP returned no article array");
  return data as FmpItem[];
}

Deno.serve(async (req: Request) => {
  const secret = adminKey();
  if (!secret) return Response.json({ error: "Backend secret not configured" }, { status: 503 });
  if (req.headers.get("apikey") !== secret) return Response.json({ error: "Unauthorized" }, { status: 401 });
  if (req.method !== "POST") return Response.json({ error: "POST required" }, { status: 405 });
  const fmpKey = Deno.env.get("FMP_API_KEY");
  if (!fmpKey) return Response.json({ error: "News provider not configured" }, { status: 503 });
  try {
    const [stock, releases] = await Promise.all([fmp("stock-latest", fmpKey),
      fmp("press-releases-latest", fmpKey)]);
    const rows = (await Promise.all((stock.concat(releases)).map(normalize)))
      .filter((article): article is Article => article !== null);
    const uniqueByUrl = [...new Map(rows.map((article) =>
      [article.canonical_url, article])).values()];
    const unique = [...new Map(uniqueByUrl.map((article) =>
      [article.content_hash, article])).values()];
    const project = Deno.env.get("SUPABASE_URL");
    if (!project) throw new Error("Project URL missing");
    const result = await fetch(`${project}/rest/v1/news_articles?on_conflict=content_hash`, {
      method: "POST", headers: { apikey: secret, "Content-Type": "application/json",
        Prefer: "resolution=ignore-duplicates,return=representation" },
      body: JSON.stringify(unique), signal: AbortSignal.timeout(15_000),
    });
    if (!result.ok) throw new Error(`News storage HTTP ${result.status}`);
    const inserted = (await result.json()) as Article[];
    // OpenAI is optional at ingestion time; keep raw sourced news for retry.
    if (inserted.length && Deno.env.get("OPENAI_API_KEY")) {
      EdgeRuntime.waitUntil(fetch(`${project}/functions/v1/analyze-news`, {
        method: "POST", headers: { apikey: secret, "Content-Type": "application/json" },
        body: "{}", signal: AbortSignal.timeout(45_000),
      }).catch(() => null));
    }
    return Response.json({ received: rows.length, inserted: inserted.length });
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Ingestion failed" },
      { status: 502 });
  }
});
