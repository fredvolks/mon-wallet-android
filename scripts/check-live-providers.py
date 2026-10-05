#!/usr/bin/env python3
"""Read-only smoke check of the same SEC, Nasdaq and official-domain routes as Android.

Run manually with network access. Real prices and analyst counts are deliberately not fixtures.
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request

IDENTITIES = [
    ("AAPL", "Nasdaq", "apple.com"),
    ("MSFT", "Nasdaq", "microsoft.com"),
    ("NVDA", "Nasdaq", "nvidia.com"),
    ("TSM", "NYSE", "tsmc.com"),
    ("MCD", "NYSE", "mcdonalds.com"),
    ("GURU.TO", "TSX", "guruenergy.com"),
    ("PHOS.CN", "CSE", "firstphosphate.com"),
    ("BLDP.TO", "TSX", "ballard.com"),
    ("XEQT.TO", "TSX", "ishares.com"),
    ("DOL.TO", "TSX", "dollarama.com"),
]


def get(url, accept="application/json", sec=False):
    headers = {"User-Agent": "MonWalletAndroid/0.2.1 (personal portfolio tracker; contact via application owner)" if sec else "Mozilla/5.0 MonWallet/0.2.1", "Accept": accept}
    if "nasdaq.com" in url:
        headers["Origin"] = "https://www.nasdaq.com"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=25) as response:
        return response.read()


def main():
    index = json.loads(get("https://www.sec.gov/files/company_tickers_exchange.json", sec=True))
    fields = index["fields"]
    companies = {(row[fields.index("ticker")], row[fields.index("exchange")]): row[fields.index("cik")] for row in index["data"]}
    report = {}
    failures = []
    for symbol, exchange, domain in IDENTITIES:
        row = {"identity": f"{symbol}|{exchange}", "finance": "unavailable", "analyst": "no coverage", "logo": "unavailable"}
        try:
            logo = get("https://www.google.com/s2/favicons?" + urllib.parse.urlencode({"domain": domain, "sz": 128}), "image/png")
            row["logo"] = f"PNG {len(logo)} B sha256:{hashlib.sha256(logo).hexdigest()[:12]}" if logo.startswith(b"\x89PNG") and len(logo) > 150 else "invalid"
        except Exception as e:
            row["logo"] = f"error: {type(e).__name__}"
        if exchange in ("Nasdaq", "NYSE"):
            cik = companies.get((symbol, exchange))
            if cik is not None:
                try:
                    data = json.loads(get(f"https://data.sec.gov/api/xbrl/companyfacts/CIK{cik:010}.json", sec=True))
                    names = data.get("facts", {}).get("us-gaap", {})
                    present = [name for name in ("RevenueFromContractWithCustomerExcludingAssessedTax", "NetIncomeLoss", "EarningsPerShareDiluted", "CashAndCashEquivalentsAtCarryingValue", "NetCashProvidedByUsedInOperatingActivities") if names.get(name, {}).get("units")]
                    row["finance"] = f"SEC CIK {cik}: {', '.join(present)}" if present else "SEC: no mapped concepts"
                except Exception as e:
                    row["finance"] = f"SEC error: {type(e).__name__}"
            try:
                data = json.loads(get(f"https://api.nasdaq.com/api/analyst/{symbol}/targetprice"))["data"]
                summary = data.get("consensusOverview") if data else None
                if summary:
                    row["analyst"] = {k: summary.get(k) for k in ("buy", "hold", "sell", "priceTarget", "highPriceTarget", "lowPriceTarget")}
            except Exception as e:
                row["analyst"] = f"error: {type(e).__name__}"
        report[symbol] = row
        if row["logo"].startswith(("error", "invalid", "unavailable")) or (symbol == "AAPL" and ("SEC CIK" not in row["finance"] or not isinstance(row["analyst"], dict))):
            failures.append(symbol)
        print(json.dumps({symbol: row}, ensure_ascii=False), flush=True)
    print("PASS" if not failures else f"FAIL: {', '.join(failures)}", flush=True)
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
