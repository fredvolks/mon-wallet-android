package ca.monwallet.app.marketdata

import ca.monwallet.app.domain.Security

/**
 * Free fallback universe for Discover > Sectors.
 * The list is a curated set of liquid large caps; rankings use the selected market-data
 * period and omit entries whose quote/history is unavailable. A configured screener may
 * contribute additional candidates.
 */
object SectorUniverse {
    val labels = linkedMapOf(
        "Technology" to "Technologie",
        "Financial Services" to "Services financiers",
        "Healthcare" to "Santé",
        "Consumer Cyclical" to "Consommation cyclique",
        "Consumer Defensive" to "Consommation défensive",
        "Energy" to "Énergie",
        "Basic Materials" to "Matériaux",
        "Industrials" to "Industrie",
        "Communication Services" to "Communication",
        "Utilities" to "Services publics",
        "Real Estate" to "Immobilier",
    )

    private val symbolsBySector = mapOf(
        "Technology" to "AAPL MSFT NVDA AVGO ORCL CRM AMD CSCO IBM QCOM TXN AMAT MU INTC ADI LRCX KLAC NOW PANW SNPS CDNS INTU PLTR ANET DELL SHOP.TO CSU.TO OTEX.TO GIB-A.TO ACN WDAY",
        "Financial Services" to "JPM BAC WFC GS MS SCHW BLK BX AXP V MA COF PNC USB BRK-B CB PGR ALL ICE CME SPGI MCO RY.TO TD.TO BMO.TO BNS.TO CM.TO NA.TO BN.TO SLF.TO MFC.TO",
        "Healthcare" to "LLY JNJ ABBV MRK ABT TMO ISRG AMGN GILD PFE BMY MDT SYK BSX REGN VRTX UNH CVS CI HUM DHR MCK COR ELV HCA ZTS IDXX EW BIIB DXCM",
        "Consumer Cyclical" to "AMZN TSLA HD MCD NKE TJX LOW BKNG SBUX TGT F GM ORLY AZO MAR ROST CMG YUM ABNB DASH LULU DRI CCL RCL NVR PHM LEN DOL.TO ATD.TO MG.TO",
        "Consumer Defensive" to "WMT COST PG KO PEP PM MO MDLZ CL KHC KR EL KDP GIS HSY SYY KMB CAG CHD ADM TSN BG DG DLTR WBA MNST STZ CPB EMP-A.TO L.TO",
        "Energy" to "XOM CVX COP SLB EOG PSX MPC VLO OXY HAL OKE WMB KMI ET EPD CTRA DVN FANG EQT ENB.TO CNQ.TO SU.TO CVE.TO IMO.TO TRP.TO KEY.TO ARX.TO BTE.TO MEG.TO TOU.TO",
        "Basic Materials" to "LIN SHW APD ECL NEM FCX SCCO NUE STLD AA CLF ALB CTVA DD PPG MLM VMC CF MOS IP PKG AVY CE EMN CC TECK-B.TO FM.TO ABX.TO AGI.TO WPM.TO CCO.TO",
        "Industrials" to "GE CAT RTX BA UNP UPS FDX DE ETN PH EMR ITW MMM HON WM ADP CTAS TDG GD LMT NOC CSX NSC URI PWR FAST ROK CP.TO CNR.TO WSP.TO TFII.TO",
        "Communication Services" to "GOOGL META NFLX DIS T TMUS VZ CMCSA CHTR EA TTWO SPOT OMC IPG FOX FOXA LYV MTCH PINS ROKU PARA SNAP Z RBLX BCE.TO RCI-B.TO QBR-B.TO TIXT.TO CGO.TO IAC",
        "Utilities" to "NEE DUK SO CEG VST AEP EXC ETR SRE XEL D ED PEG WEC ES AWK PCG EIX FE NRG TLN FTS.TO EMA.TO H.TO CU.TO ALA.TO CPX.TO BEP-UN.TO BLX.TO INE.TO",
        "Real Estate" to "PLD AMT EQIX CCI PSA SPG DLR CBRE CSGP IRM EXR AVB EQR ESS MAA UDR ARE VICI INVH SBAC CHCT PEAK KIM REG NNN SLG BXP REI-UN.TO SRU-UN.TO CAR-UN.TO",
    )

    val stocks: List<Security> = symbolsBySector.flatMap { (sector, symbols) ->
        symbols.split(' ').map { symbol ->
            val canadian = symbol.endsWith(".TO")
            Security.of(symbol, symbol, if (canadian) "TSX" else "NYSE",
                if (canadian) "CAD" else "USD", "STOCK").copy(sector = sector)
        }
    }
}
