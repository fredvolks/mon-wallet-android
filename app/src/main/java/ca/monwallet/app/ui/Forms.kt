@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import java.time.*
import kotlinx.coroutines.*

@Composable
fun SearchDialog(s: Services, w: Wallet, onClose: () -> Unit, onChoose: (Security) -> Unit) {
    val searchUnavailable = stringResource(R.string.search_unavailable)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Security>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        error = null
        results = emptyList()
        if (query.length >= 2) {
            delay(450)
            loading = true
            try {
                results =
                    s.market.search(query).map { r ->
                        w.securities.find { it.symbol == r.symbol && it.currency == r.currency }
                            ?: r
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = searchUnavailable
            } finally {
                loading = false
            }
        }
    }
    FullDialog(stringResource(R.string.ui_rechercher_un_titre_d9a8e), onClose) {
        TextEntry(stringResource(R.string.ui_ticker_nom_ou_marche_2ccf9), query, { query = it })
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Caption(it) }
        val local =
            w.securities.filter {
                it.type !in listOf("INDEX", "FX", "COMMODITY") &&
                    (query.isBlank() ||
                        "${it.symbol} ${it.name} ${it.exchange}".contains(query, true))
            }
        val all = (local + results).distinctBy { it.symbol + it.currency + it.exchange }
        if (all.isEmpty()) Caption(stringResource(R.string.ui_aucun_resultat_53a59))
        all.take(40).forEach { security ->
            Row(
                Modifier.fillMaxWidth().clickable { onChoose(security) }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Logo(security)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(security.ticker)
                    Caption(security.name)
                }
                Badge("${security.exchange} · ${security.currency}")
            }
            HorizontalDivider(color = Border)
        }
        OutlinedButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_saisir_un_titre_manuellement_6184b))
        }
        Caption(stringResource(R.string.ui_verifie_toujours_le_marche_et_la_devise_avant_3328d))
    }
    if (manual) {
        var ticker by remember { mutableStateOf(query.uppercase()) }
        var name by remember { mutableStateOf("") }
        var exchange by remember { mutableStateOf("TSX") }
        var currency by remember { mutableStateOf("CAD") }
        var etf by remember { mutableStateOf(false) }
        FullDialog(stringResource(R.string.ui_nouveau_titre_ce6d1), { manual = false }) {
            TextEntry(stringResource(R.string.ui_symbole_du_fournisseur_ex_xeqt_to_b4956), ticker, { ticker = it.uppercase() })
            TextEntry(stringResource(R.string.ui_nom_7ff20), name, { name = it })
            TextEntry(stringResource(R.string.ui_marche_eb9aa), exchange, { exchange = it.uppercase() })
            Choice(stringResource(R.string.ui_devise_eb2e4), currency, listOf("CAD", "USD")) { currency = listOf("CAD", "USD")[it] }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(etf, { etf = it })
                Text(stringResource(R.string.ui_etf_50631))
            }
            Button(
                onClick = {
                    if (ticker.isNotBlank() && exchange.isNotBlank()) {
                        onChoose(
                            Security.of(
                                ticker.trim(),
                                name.ifBlank { ticker },
                                exchange.trim(),
                                currency,
                                if (etf) "ETF" else "STOCK",
                            )
                        )
                        manual = false
                    }
                },
                enabled = ticker.isNotBlank() && exchange.isNotBlank(),
            ) {
                Text(stringResource(R.string.ui_utiliser_ce_titre_e25b1))
            }
        }
    }
}

@Composable
fun TransactionDialog(
    s: Services,
    w: Wallet,
    initial: Security?,
    portfolio: String?,
    edit: Transaction?,
    onClose: () -> Unit,
    onSaved: (Transaction, Security?) -> Unit,
    initialType: TxType = TxType.BUY,
) {
    val checkAmounts = stringResource(R.string.check_amounts)
    var security by remember { mutableStateOf(initial ?: w.security(edit?.securityId)) }
    var chosen by remember {
        mutableStateOf(edit?.portfolioId ?: portfolio ?: w.portfolios.firstOrNull()?.id)
    }
    var type by remember { mutableStateOf(edit?.type ?: initialType) }
    var quantity by remember { mutableStateOf(edit?.quantity?.toPlainString() ?: "") }
    var price by remember { mutableStateOf(edit?.price?.toPlainString() ?: "") }
    var fees by remember { mutableStateOf(edit?.fees?.toPlainString() ?: "0") }
    var day by remember { mutableStateOf(edit?.date ?: LocalDate.now().toString()) }
    var currency by remember { mutableStateOf(edit?.currency ?: security?.currency ?: "CAD") }
    var fx by remember { mutableStateOf(edit?.fxRate?.toPlainString() ?: "") }
    var fxDate by remember { mutableStateOf(edit?.fxDate ?: day) }
    var note by remember { mutableStateOf(edit?.note ?: "") }
    var calendar by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasShares = type in listOf(TxType.BUY, TxType.SELL)
    val needSecurity = hasShares || type == TxType.DIVIDEND
    val names =
        mapOf(
            TxType.BUY to transactionLabel(TxType.BUY),
            TxType.SELL to transactionLabel(TxType.SELL),
            TxType.DIVIDEND to transactionLabel(TxType.DIVIDEND),
            TxType.FEE to transactionLabel(TxType.FEE),
            TxType.DEPOSIT to transactionLabel(TxType.DEPOSIT),
            TxType.WITHDRAWAL to transactionLabel(TxType.WITHDRAWAL),
        )
    FullDialog(
        stringResource(if (edit == null) R.string.transaction_add else R.string.transaction_edit),
        onClose,
    ) {
        if (edit == null) {
            Choice(stringResource(R.string.ui_type_3deb7), names[type]!!, names.values.toList()) {
                type = names.keys.toList()[it]
            }
        } else {
            Caption(stringResource(R.string.transaction_edit_in_place))
        }
        Choice(
            stringResource(R.string.ui_portefeuille_c2303),
            w.portfolios.find { it.id == chosen }?.name ?: stringResource(R.string.ui_choisir_b030d),
            w.portfolios.map { it.name },
        ) {
            chosen = w.portfolios[it].id
        }
        if (w.portfolios.isEmpty()) Caption(stringResource(R.string.ui_cree_dabord_un_portefeuille_dans_profil_de99b))
        if (needSecurity)
            OutlinedButton(onClick = { search = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    security?.let { "${it.ticker} · ${it.exchange} · ${it.currency}" }
                        ?: stringResource(R.string.ui_rechercher_un_titre_d9a8e)
                )
            }
        if (hasShares) TextEntry(stringResource(R.string.ui_quantite_09a38), quantity, { quantity = it }, true)
        if (type == TxType.SELL && chosen != null && security != null) {
            val available = runCatching {
                Engine.availableAt(w.transactions.filter { it.id != edit?.id }, chosen!!, security!!.id, day)
            }.getOrNull()
            Metric(stringResource(R.string.shares_available), number(available, 6))
            if (available != null && available > ZERO)
                TextButton(onClick = { quantity = available.toPlainString() }) {
                    Text(stringResource(R.string.sell_all_shares))
                }
        }
        TextEntry(stringResource(if (hasShares) R.string.price_per_share else R.string.ui_montant_4adcd), price, { price = it }, true)
        if (!needSecurity)
            Choice(stringResource(R.string.ui_devise_eb2e4), currency, listOf("CAD", "USD")) { currency = listOf("CAD", "USD")[it] }
        else Caption(stringResource(R.string.currency_value, currency))
        OutlinedButton(onClick = { calendar = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.date_value, date(day)))
        }
        Caption(stringResource(R.string.ui_vous_pouvez_selectionner_une_transaction_pass_3017a))
        TextEntry(stringResource(R.string.fees_currency, currency), fees, { fees = it }, true)
        if (currency == "USD") {
            TextEntry(stringResource(R.string.ui_taux_historique_1_usd_en_cad_158fe), fx, { fx = it }, true)
            TextEntry(stringResource(R.string.ui_date_du_taux_fx_aaaa_mm_jj_0bbb7), fxDate, { fxDate = it })
            Caption(stringResource(R.string.ui_utilise_le_taux_reellement_applique_par_ton_c_631e4))
        }
        TextEntry(stringResource(R.string.ui_notes_facultatif_5884b), note, { note = it })
        val cost =
            runCatching {
                    val gross = if (hasShares) quantity.dec() * price.dec() else price.dec()
                    if (type in listOf(TxType.SELL, TxType.DIVIDEND)) gross - fees.dec()
                    else gross + fees.dec()
                }
                .getOrNull()
        Metric(stringResource(R.string.ui_montant_4adcd), money(cost, currency))
        if (type == TxType.SELL && chosen != null && security != null) {
            val preview = runCatching {
                Engine.salePreview(w.transactions, Transaction(
                    id = edit?.id ?: "preview", portfolioId = chosen!!, securityId = security!!.id,
                    type = TxType.SELL, quantity = quantity.dec(), price = price.dec(),
                    currency = currency, fxRate = if (currency == "CAD") ONE else fx.dec(),
                    fees = fees.dec(), date = day,
                ))
            }
            preview.getOrNull()?.let {
                Metric(stringResource(R.string.gross_proceeds), money(it.gross, currency))
                Metric(stringResource(R.string.net_proceeds), money(it.netCad))
                Metric(stringResource(R.string.cost_basis), money(it.costCad))
                Metric(stringResource(R.string.realized_gain), signed(it.realizedCad), tint(it.realizedCad))
                Metric(stringResource(R.string.realized_percent), percent(it.realizedPercent), tint(it.realizedCad))
                Metric(stringResource(R.string.shares_remaining), number(it.remaining, 6))
            }
        }
        error?.let { Text(it, color = Red) }
        Button(
            enabled = !saving && chosen != null && (!needSecurity || security != null),
            onClick = {
                scope.launch {
                    saving = true
                    try {
                        val t =
                            Transaction(
                                id = edit?.id ?: uuid(),
                                portfolioId = chosen!!,
                                securityId = if (needSecurity) security?.id else null,
                                type = type,
                                quantity = if (hasShares) quantity.dec() else ZERO,
                                price = price.dec(),
                                currency = currency,
                                fxRate = if (currency == "CAD") ONE else fx.dec(),
                                fees = fees.dec(),
                                date = day,
                                fxDate = if (currency == "CAD") day else fxDate,
                                createdAt = edit?.createdAt ?: System.currentTimeMillis(),
                                note = note,
                            )
                        s.repo.transaction(t, if (needSecurity) security else null)
                        onSaved(t, security)
                        onClose()
                    } catch (e: Exception) {
                        error = e.message ?: checkAmounts
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(
                if (saving) R.string.saving
                else if (edit != null) R.string.ui_enregistrer_f7c8b
                else if (type == TxType.SELL) R.string.sell_shares
                else R.string.add
            ))
        }
        Caption(
            stringResource(R.string.ui_les_ventes_conservent_les_achats_historiques__97c24)
        )
    }
    if (search)
        SearchDialog(s, w, { search = false }) {
            security = it
            currency = it.currency
            search = false
        }
    if (calendar) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = 1970..LocalDate.now().year,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) =
                            Instant.ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate() <= LocalDate.now()
                    },
            )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            val old = day
                            day =
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .toString()
                            if (fxDate == old) fxDate = day
                        }
                        calendar = false
                    }
                ) {
                    Text(stringResource(R.string.ui_choisir_b030d))
                }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text(stringResource(R.string.ui_annuler_49ba3)) } },
        ) {
            DatePicker(state)
        }
    }
}

@Composable
fun PortfolioDialog(existing: Portfolio?, onClose: () -> Unit, onSave: (Portfolio) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "CELI") }
    FullDialog(
        stringResource(if (existing == null) R.string.new_portfolio else R.string.edit_portfolio),
        onClose,
    ) {
        TextEntry(stringResource(R.string.ui_nom_7ff20), name, { name = it })
        val types = listOf("CELI", "REER", "CELIAPP", "MARGE", "CRYPTO", "PERSONNEL", "AUTRE")
        Choice(stringResource(R.string.ui_type_3deb7), type, types) { type = types[it] }
        Caption(
            stringResource(R.string.ui_devise_de_presentation_cad_les_transactions_c_136b2)
        )
        Button(
            enabled = name.isNotBlank(),
            onClick = {
                onSave(Portfolio(existing?.id ?: uuid(), name.trim(), type))
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ui_enregistrer_f7c8b))
        }
    }
}

@Composable
fun AlertDialogForm(s: Services, security: Security, onClose: () -> Unit, onSaved: () -> Unit) {
    val invalidThreshold = stringResource(R.string.alert_invalid_threshold)
    var kind by remember { mutableStateOf("ABOVE") }
    var threshold by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val types =
        linkedMapOf(
            "ABOVE" to stringResource(R.string.alert_price_above),
            "BELOW" to stringResource(R.string.alert_price_below),
            "DAY_UP" to stringResource(R.string.alert_day_up),
            "DAY_DOWN" to stringResource(R.string.alert_day_down),
            "HIGH52" to stringResource(R.string.alert_high_52),
            "LOW52" to stringResource(R.string.alert_low_52),
            "VOLUME" to stringResource(R.string.alert_volume),
        )
    FullDialog(stringResource(R.string.alert_title, security.ticker), onClose) {
        Choice(stringResource(R.string.ui_condition_2f497), types[kind]!!, types.values.toList()) { kind = types.keys.toList()[it] }
        if (kind !in listOf("HIGH52", "LOW52"))
            TextEntry(
                if (kind in listOf("ABOVE", "BELOW")) stringResource(R.string.alert_threshold, security.currency)
                else stringResource(R.string.alert_positive_threshold),
                threshold,
                { threshold = it },
                true,
            )
        Caption(
            stringResource(R.string.ui_verification_periodique_au_mieux_toutes_les_1_3f075)
        )
        error?.let { Text(it, color = Red) }
        Button(
            onClick = {
                scope.launch {
                    try {
                        val v = if (kind in listOf("HIGH52", "LOW52")) ZERO else threshold.dec()
                        require(v >= ZERO)
                        val a = PriceAlert(securityId = security.id, kind = kind, threshold = v)
                        s.repo.put("security", security.id, security)
                        s.repo.put("alert", a.id, a)
                        onSaved()
                        onClose()
                    } catch (e: Exception) {
                        error = invalidThreshold
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.ui_creer_lalerte_32356))
        }
    }
}

@Composable
fun NotificationDialog(
    s: Services,
    security: Security,
    onClose: () -> Unit,
    onPermission: () -> Unit,
) {
    var earnings by remember { mutableStateOf(true) }
    var news by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.follow_security, security.ticker)) },
        text = {
            Column {
                Text(stringResource(R.string.ui_voulez_vous_recevoir_les_notifications_liees__549ab))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(earnings, { earnings = it })
                    Text(stringResource(R.string.ui_dates_et_resultats_earnings_2d231))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(news, { news = it })
                    Text(stringResource(R.string.ui_actualites_du_titre_bbb8c))
                }
                Caption(
                    stringResource(R.string.ui_earnings_cle_finnhub_requise_les_annonces_de__d33ba)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val n =
                            NotificationPreference(
                                "notification:${security.id}",
                                security.id,
                                earnings,
                                news,
                            )
                        s.repo.put("notification_preference", n.id, n)
                        onPermission()
                        onClose()
                    }
                }
            ) {
                Text(stringResource(R.string.ui_activer_b96fa))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ui_pas_maintenant_cf96b)) } },
    )
}
