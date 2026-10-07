package ca.monwallet.app.data

import ca.monwallet.app.domain.Security

object Catalog {
    val stocks =
        listOf(
            Security.of("XEQT.TO", "iShares Core Equity ETF Portfolio", "TSX", "CAD", "ETF"),
            Security.of("VFV.TO", "Vanguard S&P 500 Index ETF", "TSX", "CAD", "ETF"),
            Security.of("QQC.TO", "Invesco NASDAQ 100 Index ETF", "TSX", "CAD", "ETF"),
            Security.of("TSM", "Taiwan Semiconductor Manufacturing", "NYSE", "USD"),
            Security.of("MCD.NE", "McDonald's CDR (CAD Hedged)", "Cboe Canada", "CAD"),
            Security.of("MCD", "McDonald's Corporation", "NYSE", "USD"),
            Security.of("GURU.TO", "GURU Organic Energy", "TSX", "CAD"),
            Security.of("DOL.TO", "Dollarama", "TSX", "CAD"),
            Security.of("PHOS.CN", "First Phosphate", "CSE", "CAD"),
            Security.of("BLDP.TO", "Ballard Power Systems", "TSX", "CAD"),
            Security.of("BLDP", "Ballard Power Systems", "NASDAQ", "USD"),
            Security.of("EFR.TO", "Energy Fuels", "TSX", "CAD"),
            Security.of("NVDA", "NVIDIA", "NASDAQ", "USD"),
            Security.of("AAPL", "Apple", "NASDAQ", "USD"),
            Security.of("MSFT", "Microsoft", "NASDAQ", "USD"),
            Security.of("GOOGL", "Alphabet", "NASDAQ", "USD"),
            Security.of("META", "Meta Platforms", "NASDAQ", "USD"),
            Security.of("AMZN", "Amazon", "NASDAQ", "USD"),
            Security.of("TSLA", "Tesla", "NASDAQ", "USD"),
            Security.of("PLTR", "Palantir", "NASDAQ", "USD"),
            Security.of("AVGO", "Broadcom", "NASDAQ", "USD"),
        )
    val markets =
        listOf(
            Security.of("^GSPC", "S&P 500", "S&P", "USD", "INDEX"),
            Security.of("^IXIC", "NASDAQ Composite", "NASDAQ", "USD", "INDEX"),
            Security.of("^DJI", "Dow Jones Industrial Average", "NYSE", "USD", "INDEX"),
            Security.of("^GSPTSE", "S&P/TSX Composite", "TSX", "CAD", "INDEX"),
            Security.of("BTC-USD", "Bitcoin", "Crypto", "USD", "CRYPTO"),
            Security.of("^VIX", "VIX", "CBOE", "USD", "INDEX"),
            Security.of("GC=F", "Or · Futures", "COMEX", "USD", "COMMODITY"),
            Security.of("CADUSD=X", "CAD / USD", "FX", "USD", "FX"),
            Security.of("CAD=X", "USD / CAD", "FX", "CAD", "FX"),
            Security.of("ETH-USD", "Ethereum", "Crypto", "USD", "CRYPTO"),
            Security.of("CL=F", "WTI · Futures", "NYMEX", "USD", "COMMODITY"),
        )
    val sectors =
        listOf(
                "XLK" to "Technologie",
                "XLF" to "Services financiers",
                "XLV" to "Santé",
                "XLY" to "Consommation",
                "XLE" to "Énergie",
                "XLB" to "Matériaux",
                "XLI" to "Industrie",
                "XLC" to "Communication",
                "XLRE" to "Immobilier",
                "XLU" to "Services publics",
            )
            .map { Security.of(it.first, it.second, "NYSE Arca", "USD", "ETF") }
    val all = (stocks + markets + sectors).distinctBy { it.id }
}
