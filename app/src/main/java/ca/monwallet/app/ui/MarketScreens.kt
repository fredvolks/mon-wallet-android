package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.*

@Composable
fun QuoteRow(w: Wallet, s: Security, onClick: () -> Unit, menu: (@Composable () -> Unit)? = null) {
    val q = w.quotes[s.id]
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(s)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.ticker, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (w.alerts.any { it.securityId == s.id && it.enabled })
                    Icon(
                        Icons.Outlined.Notifications,
                        stringResource(R.string.watchlist_alert_active),
                        Modifier.padding(start = 4.dp).size(12.dp),
                        tint = Blue,
                    )
                if (w.transactions.any { it.securityId == s.id })
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        stringResource(R.string.watchlist_holding_history),
                        Modifier.padding(start = 4.dp).size(12.dp),
                        tint = Muted,
                    )
            }
            Text(
                s.name,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Caption("${s.exchange} · ${s.currency}")
        }
        val history = w.prices.filter { it.securityId == s.id }.takeLast(24)
        if (history.size > 1)
            Chart(
                history.map { it.close },
                modifier = Modifier.width(42.dp).height(23.dp),
                color = tint(q?.change),
            )
        Column(
            Modifier.widthIn(min = 90.dp).padding(start = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(number(q?.price), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(signed(q?.change, s.currency), color = tint(q?.change), fontSize = 11.sp)
            Text(percent(q?.percent), color = tint(q?.change), fontSize = 11.sp)
        }
        menu?.invoke()
    }
    HorizontalDivider(color = Border)
}

@Composable
fun WatchlistScreen(
    w: Wallet,
    vm: WalletViewModel,
    onDetail: (Security) -> Unit,
    onBuy: (Security) -> Unit,
    onAlert: (Security) -> Unit,
    onAdd: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    val list = w.watchlists.find { it.id == selected } ?: w.watchlists.firstOrNull()
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var choose by remember { mutableStateOf<Pair<Security, WatchItem?>?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Chips(w.watchlists.map { it.name }, w.watchlists.indexOf(list)) {
                        selected = w.watchlists[it].id
                    }
                }
                IconButton(
                    onClick = {
                        editing = false
                        name = ""
                        naming = true
                    }
                ) {
                    Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.watchlist_new), tint = Blue)
                }
                Box {
                    IconButton(onClick = { options = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.watchlist_options))
                    }
                    DropdownMenu(options, { options = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_renommer_8e8a8)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                editing = true
                                name = list!!.name
                                naming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_deplacer_a_gauche_02df9)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                list?.let { l ->
                                    vm.run {
                                        val sorted = w.watchlists.toMutableList()
                                        val i = sorted.indexOf(l)
                                        if (i > 0) {
                                            java.util.Collections.swap(sorted, i, i - 1)
                                            sorted.forEachIndexed { index, item ->
                                                vm.services.repo.put(
                                                    "watchlist",
                                                    item.id,
                                                    item.copy(order = index),
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ui_supprimer_la_liste_bff90)) },
                            enabled = list != null,
                            onClick = {
                                options = false
                                deleting = true
                            },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Caption(stringResource(R.string.ui_titre_marche_71340))
                Caption(stringResource(R.string.ui_prix_var_du_jour_0d4a6))
            }
        }
        val items = w.items.filter { it.watchlistId == list?.id }.sortedBy { it.order }
        if (items.isEmpty())
            item {
                Empty(
                    stringResource(R.string.ui_ta_liste_en_un_coup_dil_cc0e0),
                    stringResource(R.string.watchlist_empty),
                    stringResource(if (list == null) R.string.watchlist_create else R.string.ui_ajouter_un_titre_bf42c),
                ) {
                    if (list == null) {
                        editing = false
                        name = ""
                        naming = true
                    } else onAdd(list.id)
                }
            }
        items(items, key = { it.id }) { item ->
            w.security(item.securityId)?.let { s ->
                var open by remember { mutableStateOf(false) }
                QuoteRow(w, s, { onDetail(s) }) {
                    Box {
                        IconButton(onClick = { open = true }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.watchlist_actions), Modifier.size(18.dp))
                        }
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_creer_une_alerte_e1be5)) },
                                onClick = {
                                    open = false
                                    onAlert(s)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_ajouter_au_portefeuille_1080f)) },
                                onClick = {
                                    open = false
                                    onBuy(s)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_ajouter_a_une_autre_liste_a8e15)) },
                                onClick = {
                                    open = false
                                    choose = s to null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_deplacer_dans_une_liste_cd26f)) },
                                onClick = {
                                    open = false
                                    choose = s to item
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_monter_dd1b7)) },
                                onClick = {
                                    open = false
                                    vm.run {
                                        val index = items.indexOf(item)
                                        if (index > 0) {
                                            val before = items[index - 1]
                                            vm.services.repo.put(
                                                "watch_item",
                                                before.id,
                                                before.copy(order = item.order),
                                            )
                                            vm.services.repo.put(
                                                "watch_item",
                                                item.id,
                                                item.copy(order = before.order),
                                            )
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_retirer_54ec2)) },
                                onClick = {
                                    open = false
                                    vm.run { vm.services.repo.remove(item.id) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ui_voir_le_titre_0e845)) },
                                onClick = {
                                    open = false
                                    onDetail(s)
                                },
                            )
                        }
                    }
                }
            }
        }
        if (list != null && items.isNotEmpty())
            item {
                TextButton(onClick = { onAdd(list.id) }) {
                    Icon(Icons.Outlined.Add, null)
                    Text(stringResource(R.string.ui_ajouter_un_titre_bf42c))
                }
            }
        item {
            Caption(
                stringResource(R.string.ui_sparklines_dernieres_clotures_disponibles_app_d1034)
            )
        }
    }
    if (naming)
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(if (editing) R.string.ui_renommer_8e8a8 else R.string.watchlist_new)) },
            text = { TextEntry(stringResource(R.string.ui_nom_7ff20), name, { name = it }) },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val new =
                            Watchlist(
                                if (editing) list!!.id else uuid(),
                                name.trim(),
                                if (editing) list!!.order else w.watchlists.size,
                            )
                        vm.run { vm.services.repo.put("watchlist", new.id, new) }
                        selected = new.id
                        naming = false
                    },
                ) {
                    Text(stringResource(R.string.ui_enregistrer_f7c8b))
                }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.ui_annuler_49ba3)) } },
        )
    if (deleting && list != null)
        Confirm(
            stringResource(R.string.watchlist_delete, list.name),
            stringResource(R.string.watchlist_delete_caption),
            { deleting = false },
        ) {
            vm.run { vm.services.repo.remove(list.id) }
        }
    choose?.let { (security, item) ->
        ChooseWatchlist(w, { choose = null }) { target ->
            vm.run("Liste mise à jour") {
                vm.services.repo.watch(security, target)
                if (item != null && item.watchlistId != target) vm.services.repo.remove(item.id)
            }
            choose = null
        }
    }
}

@Composable
fun ChooseWatchlist(w: Wallet, onClose: () -> Unit, onChoose: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.ui_choisir_une_watchlist_751e9)) },
        text = {
            Column {
                if (w.watchlists.isEmpty()) Caption(stringResource(R.string.ui_cree_une_liste_dans_watchlist_7260e))
                w.watchlists.forEach { list ->
                    TextButton(
                        onClick = { onChoose(list.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(list.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ui_fermer_5ab4e)) } },
    )
}

@Composable
fun MarketsScreen(w: Wallet, vm: WalletViewModel, onDetail: (Security) -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val markets =
        Catalog.markets
            .filter { it.symbol != "CAD=X" }
            .filter {
                when (selected) {
                    1 -> it.type == "INDEX"
                    2 -> it.type == "FX"
                    3 -> it.type == "COMMODITY"
                    4 -> it.type == "CRYPTO"
                    else -> true
                }
            }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Chips(listOf("Aperçu", "Indices", "Devises", "Matières", "Crypto"), selected) {
                selected = it
            }
        }
        items(markets.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                pair.forEach { s ->
                    val q = w.quotes[s.id]
                    Column(
                        Modifier.weight(1f)
                            .background(
                                Panel,
                                androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            )
                            .clickable { onDetail(s) }
                            .padding(12.dp)
                    ) {
                        Caption(s.name)
                        Text(number(q?.price), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${signed(q?.change,s.currency)} (${percent(q?.percent)})",
                            color = tint(q?.change),
                            fontSize = 10.sp,
                        )
                        Chart(
                            w.prices.filter { it.securityId == s.id }.takeLast(30).map { it.close },
                            modifier = Modifier.height(36.dp),
                            color = tint(q?.change),
                        )
                        Caption(q?.let { time(it.timestamp) } ?: "Aucun cours")
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item {
            Caption(
                stringResource(R.string.ui_les_marches_affichent_des_indices_et_des_inst_47f49)
            )
            Section(stringResource(R.string.ui_secteurs_etf_de_reference_b755b), "Actualiser") {
                vm.run { vm.services.refresh(Catalog.sectors) }
            }
            Caption(stringResource(R.string.ui_variation_des_etf_sectoriels_americains_et_no_cf45d))
        }
        items(Catalog.sectors) { s ->
            val q = w.quotes[s.id]
            Row(
                Modifier.fillMaxWidth().clickable { onDetail(s) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(s.name, fontSize = 14.sp)
                Text(percent(q?.percent), color = tint(q?.change), fontSize = 14.sp)
            }
            HorizontalDivider(color = Border)
        }
    }
}
