package ca.monwallet.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.monwallet.app.domain.Security
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.news.NewsArticle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(wallet: Wallet, vm: WalletViewModel, onSecurity: (Security) -> Unit) {
    val feed by vm.services.news.state.collectAsState()
    var tab by remember { mutableStateOf("Pour moi") }
    var showWeak by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }
    val trackedIds = remember(wallet.transactions, wallet.items) {
        (wallet.transactions.mapNotNull { it.securityId } +
            wallet.items.map { it.securityId }).toSet()
    }
    val trackedSymbols = remember(trackedIds, wallet.securities) {
        trackedIds.mapNotNull(wallet::security).map { it.symbol.uppercase() }.toSet()
    }
    LaunchedEffect(trackedSymbols) {
        vm.services.news.refresh(wallet)
    }
    val visible = remember(feed.articles, tab, showWeak, trackedSymbols) {
        feed.articles.filter { article ->
            (showWeak || article.analysis?.importance != "LOW") && when (tab) {
                "Pour moi" -> article.tickers.any { it.uppercase() in trackedSymbols }
                "Canada" -> article.market == "CANADA"
                "USA" -> article.market == "USA"
                "Marchés" -> article.market == "MACRO"
                else -> true
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)) {
            listOf("Pour moi", "Canada", "USA", "Marchés", "Toutes").forEach { name ->
                FilterChip(selected = tab == name, onClick = { tab = name },
                    label = { Text(name, fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (feed.loading) "Actualisation…" else if (feed.error != null) feed.error!!
                else "${visible.size} nouvelles · source et date indiquées",
                color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showWeak = !showWeak }) {
                Text(if (showWeak) "Faibles : oui" else "Faibles : non", fontSize = 10.sp)
            }
            TextButton(onClick = { vm.run { vm.services.news.refresh(wallet, force = true) } }) {
                Text("↻", fontSize = 18.sp)
            }
        }
        if (feed.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (visible.isEmpty() && !feed.loading) {
            Text(if (tab == "Pour moi" && trackedSymbols.isEmpty())
                "Ajoute des titres à ton portefeuille ou à une Watchlist pour personnaliser le fil."
                else feed.error ?: "Aucune nouvelle disponible pour cette sélection.",
                Modifier.padding(20.dp), color = Muted)
        }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 80.dp)) {
            items(visible, key = { it.id + it.url }) { article ->
                Column(Modifier.fillMaxWidth().clickable { selected = article }
                    .padding(vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(article.tickers.take(3).joinToString(" · "), color = Green,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(article.analysis?.importance?.let { "IA · $it" }
                            ?: "Non analysée", color = Muted, fontSize = 10.sp)
                    }
                    Text(article.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    article.analysis?.summaryFr?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = Muted, fontSize = 11.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Text("${article.source} · ${newsDate(article.publishedAt)}", color = Muted,
                        fontSize = 10.sp)
                }
                HorizontalDivider(color = Border)
            }
        }
    }
    selected?.let { article ->
        val uriHandler = LocalUriHandler.current
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(article.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${article.source} · ${newsDate(article.publishedAt)}", color = Muted,
                    fontSize = 11.sp)
                Text("Fournisseur : ${article.provider}", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(12.dp))
                val a = article.analysis
                if (a != null) {
                    Text("Résumé IA", color = Green, fontSize = 12.sp)
                    Text(a.summaryFr)
                    if (a.whyItMatters.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Pourquoi c'est important", color = Green, fontSize = 12.sp)
                        Text(a.whyItMatters)
                    }
                    if (a.shortTermImpact.isNotBlank()) Text("Court terme : ${a.shortTermImpact}")
                    if (a.longTermImpact.isNotBlank()) Text("Long terme : ${a.longTermImpact}")
                    Text("${a.importance} · ${a.sentiment} · confiance ${
                        (a.confidence * 100).toInt()} %", color = Muted, fontSize = 11.sp)
                } else Text("Analyse IA indisponible pour cette nouvelle. Le titre et la source sont conservés.",
                    color = Muted)
                Row {
                    article.tickers.take(3).forEach { ticker ->
                        wallet.securities.firstOrNull { it.symbol.equals(ticker, true) }?.let { sec ->
                            TextButton(onClick = { selected = null; onSecurity(sec) }) {
                                Text(sec.ticker)
                            }
                        }
                    }
                }
                TextButton(onClick = { uriHandler.openUri(article.url) }) {
                    Text("Source originale ↗")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun newsDate(timestamp: Long): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM · HH:mm", java.util.Locale.CANADA_FRENCH)
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}.getOrDefault("Date inconnue")
