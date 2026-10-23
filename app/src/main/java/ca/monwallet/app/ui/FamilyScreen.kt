package ca.monwallet.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.Services
import ca.monwallet.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FamilyMemberDialog(s: Services, onClose: () -> Unit, onAdded: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onClose, title = { Text("Ajouter un membre") },
        text = { Column { TextEntry("Prénom", name, { name = it }); error?.let { Text(it, color = Red) } } },
        dismissButton = { TextButton(onClick = onClose) { Text("Annuler") } },
        confirmButton = { TextButton(enabled = !busy && name.trim().isNotEmpty(), onClick = {
            scope.launch {
                busy = true
                try {
                    val current = s.repo.current()
                    require(current.familyMembers().none { it.name.equals(name.trim(), true) }) { "Ce membre existe déjà." }
                    val id = uuid()
                    s.repo.setting("family_member_" + id, name.trim().take(60))
                    onAdded(id)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) { error = e.message }
                finally { busy = false }
            }
        }) { Text("Ajouter") } })
}

@Composable
internal fun FamilyDialog(s: Services, portfolioId: String?, onClose: () -> Unit) {
    val wallet by s.repo.state.collectAsState()
    val members = wallet.familyMembers()
    var selected by remember { mutableStateOf<String?>(null) }
    var all by remember { mutableStateOf(false) }
    var addMember by remember { mutableStateOf(false) }
    var transactionType by remember { mutableStateOf<TxType?>(null) }
    var edit by remember { mutableStateOf<Transaction?>(null) }
    var initialSecurity by remember { mutableStateOf<Security?>(null) }
    var showAllTransactions by remember { mutableStateOf(false) }
    val memberWallet = if (all) wallet else wallet.forMember(selected)
    val entries = memberWallet.transactions.filter { portfolioId == null || it.portfolioId == portfolioId }
    val pending = runCatching { FamilyLedger.resolve(entries).pending }.getOrDefault(emptyList())
    val result = remember(entries, wallet.quotes, wallet.settings) {
        runCatching { memberWallet.copy(transactions = entries).result() }.getOrNull()
    }
    FullDialog("Famille · investissements", onClose) {
        Caption(wallet.portfolios.find { it.id == portfolioId }?.name ?: "Tous les comptes")
        Chips(listOf("Compte complet") + members.map { it.name },
            if (all) 0 else members.indexOfFirst { it.id == selected } + 1) { index ->
            all = index == 0
            if (index > 0) selected = members[index - 1].id
        }
        TextButton(onClick = { addMember = true }) { Text("+ Ajouter un membre") }
        CardBlock {
            Caption(if (all) "COMPTE COMPLET · CAD" else wallet.memberName(selected).uppercase() + " · CAD")
            Text(money(result?.value), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("${signed(result?.pnl)} · ${percent(result?.percent)}", color = tint(result?.pnl), fontSize = 20.sp)
            Metric("Capital net investi", money(result?.invested))
            Metric("Dépôts enregistrés", money(entries.filter { it.type == TxType.DEPOSIT }.fold(ZERO) { n, t -> n + t.cad }))
            Metric("Retraits enregistrés", money(entries.filter { it.type == TxType.WITHDRAWAL }.fold(ZERO) { n, t -> n + t.cad }))
            Metric("Liquidités", if (pending.isEmpty()) money(result?.cash) else "À compléter")
            Metric("Profit réalisé" + if (pending.isEmpty()) "" else " · partiel", money(result?.realized))
            Metric("Dividendes reçus", money(result?.dividends))
            Caption("Sans dépôt enregistré, un achat est traité comme un apport. Les membres se partagent le compte; leurs parts ne sont jamais ajoutées une deuxième fois.")
        }
        if (pending.isNotEmpty()) CardBlock {
            Text("Historique à compléter", color = Blue, fontWeight = FontWeight.Bold)
            Caption("Une vente dépasse les achats enregistrés. Valeur totale, rendement et liquidités restent indisponibles. Les ventes concernées ne sont pas comptabilisées avant correction des achats.")
        }
        if (!all) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { initialSecurity = null; transactionType = TxType.DEPOSIT }) { Text("Dépôt") }
            OutlinedButton(onClick = { initialSecurity = null; transactionType = TxType.BUY }) { Text("Acheter") }
            OutlinedButton(onClick = { initialSecurity = null; transactionType = TxType.SELL }) { Text("Vendre") }
        }
        Text("Titres détenus", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        result?.holdings?.filter { it.quantity > ZERO }?.forEach { holding ->
            val security = wallet.security(holding.securityId)
            val incomplete = pending.any { it.securityId == holding.securityId }
            CardBlock {
                Text(security?.ticker ?: holding.securityId, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Caption(security?.name.orEmpty())
                Metric("Quantité", if (incomplete) "À compléter" else number(holding.quantity, 6))
                Metric("Prix moyen", if (incomplete) "À compléter" else money(holding.average, security?.currency ?: "CAD"))
                Metric("Coût des parts · CAD", if (incomplete) "À compléter" else money(holding.costCad))
                Metric("Valeur · CAD", if (incomplete) "À compléter" else money(holding.value))
                Text(if (incomplete) "Profit : à compléter" else "${signed(holding.pnl)} · ${percent(holding.percent)}",
                    color = if (incomplete) Blue else tint(holding.pnl))
                if (!all && security != null) TextButton(onClick = { initialSecurity = security; transactionType = TxType.SELL }) { Text("Vendre ce titre") }
            }
        }
        Text("Historique · touche pour modifier", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (entries.isEmpty()) Caption("Aucune transaction pour ce membre.")
        val sorted = FamilyLedger.ordered(entries).asReversed()
        sorted.take(if (showAllTransactions) sorted.size else 20).forEach { t ->
            OutlinedButton(onClick = { initialSecurity = null; edit = t }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(wallet.memberName(t.memberId) + " · " + transactionLabel(t.type) + " · " + (wallet.security(t.securityId)?.ticker ?: "Liquidités"))
                    Text(date(t.date) + " · " + money(t.cad))
                    if (t.type in listOf(TxType.BUY, TxType.SELL)) Text(number(t.quantity, 6) + " action(s)")
                    if (pending.any { it.id == t.id }) Text("Profit : à compléter", color = Blue)
                }
            }
        }
        if (sorted.size > 20 && !showAllTransactions) TextButton(onClick = { showAllTransactions = true }) { Text("Tout l’historique") }
        Caption("Suivi dans Mon Wallet. Les transactions saisies ici ne passent pas d’ordre chez ton courtier.")
    }
    if (addMember) FamilyMemberDialog(s, { addMember = false }) { selected = it; all = false; addMember = false }
    if (transactionType != null || edit != null) TransactionDialog(s, wallet, initialSecurity,
        portfolioId, edit, { transactionType = null; edit = null }, { _, _ -> },
        initialType = transactionType ?: TxType.BUY, initialMemberId = selected)
}
