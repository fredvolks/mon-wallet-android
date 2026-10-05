package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.*
import kotlinx.coroutines.*

@Composable
fun DiscoverScreen(vm: WalletViewModel, onDetail: (Security) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableIntStateOf(1) }
    var cap by remember { mutableStateOf("1000000000") }
    var volume by remember { mutableStateOf("100000") }
    var minPrice by remember { mutableStateOf("5") }
    var pe by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var exchange by remember { mutableStateOf("NASDAQ,NYSE,TSX") }
    var etf by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<DiscoveryRow>>(emptyList()) }
    var message by remember {
        mutableStateOf("Filtre un univers liquide et compare sa progression dans le temps.")
    }
    var busy by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf("Tendance") }
    var reverse by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun runScreen() {
        scope.launch {
            busy = true
            try {
                val filters =
                    Filters(
                        cap.dec(),
                        volume.dec(),
                        minPrice.dec(),
                        pe.takeIf { it.isNotBlank() }?.dec(),
                        country,
                        sector,
                        exchange,
                        etf,
                    )
                require(filters.cap >= ZERO && filters.volume >= ZERO && filters.price >= ZERO)
                rows = Discovery(vm.services).screen(filters) { message = it }
                message =
                    "${rows.size} titres analysés · échantillon limité à 30 candidats du fournisseur"
                tab = 0
            } catch (e: Exception) {
                message = e.message ?: "Recherche indisponible."
            } finally {
                busy = false
            }
        }
    }
    val sorted =
        when (sort) {
            "Ticker" -> rows.sortedBy { it.security.ticker }
            "Prix" -> rows.sortedBy { it.price }
            "P/E" -> rows.sortedBy { it.pe }
            "Cap." -> rows.sortedBy { it.cap }
            "1M" -> rows.sortedBy { it.one }
            "3M" -> rows.sortedBy { it.three }
            "6M" -> rows.sortedBy { it.six }
            else -> rows.filter { it.trend != null }.sortedBy { it.trend }
        }.let { if (reverse) it.reversed() else it }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Chips(listOf("Top performance", "Filtres", "Idées"), tab) { tab = it }
            Caption(message)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when (tab) {
            1 -> {
                item {
                    TextEntry(stringResource(R.string.ui_capitalisation_minimale_c9c56), cap, { cap = it }, true)
                    TextEntry(stringResource(R.string.ui_volume_minimum_parts_jour_273f7), volume, { volume = it }, true)
                    TextEntry(stringResource(R.string.ui_prix_minimum_8704e), minPrice, { minPrice = it }, true)
                    TextEntry(stringResource(R.string.ui_p_e_positif_inferieur_a_facultatif_9e34c), pe, { pe = it }, true)
                    TextEntry(
                        stringResource(R.string.ui_marches_separes_par_virgules_45407),
                        exchange,
                        { exchange = it.uppercase() },
                    )
                    TextEntry(
                        stringResource(R.string.ui_pays_ex_us_ca_vide_tous_1dcc6),
                        country,
                        { country = it.uppercase() },
                    )
                    TextEntry(stringResource(R.string.ui_secteur_fmp_ex_technology_07da9), sector, { sector = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(etf, { etf = it })
                        Text(stringResource(R.string.ui_exclure_les_etf_6d6d6), Modifier.padding(start = 10.dp))
                    }
                    Caption(
                        stringResource(R.string.ui_le_prix_minimal_de_5_exclut_par_defaut_les_pe_8bbd1)
                    )
                    Button(
                        onClick = { runScreen() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ui_rechercher_91f7d))
                    }
                }
            }
            2 -> {
                item {
                    Empty(
                        stringResource(R.string.ui_une_progression_plus_reguliere_c3ffa),
                        "Le score combine les performances 1M, 3M et 6M, pénalise la volatilité et les baisses depuis un sommet, et tient compte des séances positives. Il compare uniquement les titres chargés.",
                    )
                    Caption(
                        stringResource(R.string.ui_score_descriptif_interne_sans_valeur_predicti_d0cd4)
                    )
                    OutlinedButton(
                        onClick = {
                            cap = "1000000000"
                            volume = "100000"
                            minPrice = "5"
                            pe = "20"
                            tab = 1
                        }
                    ) {
                        Text(stringResource(R.string.ui_explorer_les_p_e_inferieurs_a_20_ad2a1))
                    }
                    OutlinedButton(
                        onClick = {
                            sector = "Technology"
                            tab = 1
                        }
                    ) {
                        Text(stringResource(R.string.ui_explorer_la_technologie_adf7b))
                    }
                }
            }
            else -> {
                item {
                    Chips(listOf("1M", "3M", "6M", "1A"), period) {
                        period = it
                        sort = listOf("1M", "3M", "6M", "Tendance")[it]
                    }
                    Button(
                        onClick = { runScreen() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (rows.isEmpty()) "Lancer l’analyse" else "Actualiser")
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("Tendance", "Ticker", "Prix", "P/E", "Cap.", "1M", "3M", "6M")
                            .forEach { column ->
                                TextButton(
                                    onClick = {
                                        if (sort == column) reverse = !reverse
                                        else {
                                            sort = column
                                            reverse = true
                                        }
                                    }
                                ) {
                                    Text(
                                        column +
                                            (if (sort == column) if (reverse) " ↓" else " ↑"
                                            else ""),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                    }
                }
                items(sorted, key = { it.security.id }) { r ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                vm.run {
                                    vm.services.repo.put("security", r.security.id, r.security)
                                    vm.services.repo.points(r.security.id, r.points)
                                }
                                onDetail(r.security)
                            }
                            .padding(vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Logo(r.security)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(r.security.ticker, fontWeight = FontWeight.Bold)
                                Caption("${r.security.exchange} · ${r.security.currency}")
                            }
                            Chart(
                                r.points.takeLast(63).map { it.close },
                                modifier = Modifier.width(50.dp).height(30.dp),
                            )
                            Column(
                                Modifier.padding(start = 10.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(money(r.price, r.security.currency))
                                val performance = listOf(r.one, r.three, r.six, r.year)[period]
                                Text(percent(performance), color = tint(performance))
                            }
                        }
                        Caption(
                            "P/E ${number(r.pe)} · Cap. ${number(r.cap,0)} · Score ${r.trend?.let{"%.1f".format(it)}?:"—"}"
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Caption("1M ${percent(r.one)}")
                            Caption("3M ${percent(r.three)}")
                            Caption("6M ${percent(r.six)}")
                        }
                    }
                    HorizontalDivider(color = Border)
                }
                item {
                    Caption(
                        stringResource(R.string.ui_source_financial_modeling_prep_et_historiques_634b6)
                    )
                }
            }
        }
    }
}
