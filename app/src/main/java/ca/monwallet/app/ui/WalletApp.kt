@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ca.monwallet.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.*
import ca.monwallet.app.R
import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LockScreen(unlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Night) {
        Column(
            Modifier.fillMaxSize().padding(30.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(58.dp), tint = Green)
            Text(stringResource(R.string.locked_message), Modifier.padding(vertical = 20.dp))
            Button(onClick = unlock) { Text(stringResource(R.string.action_unlock)) }
        }
    }
}

@Composable
fun WalletApp(deepSecurity: String?, widgetPortfolio: Pair<String?, Int>?,
    widgetSection: Pair<String, Int>?,
    biometric: (() -> Unit) -> Unit) {
    val vm: WalletViewModel = viewModel()
    val s = vm.services
    val w by vm.wallet.collectAsState()
    val initialized by s.initialized.collectAsState()
    val prefs by s.secure.preferences.collectAsState(initial = emptyMap())
    val user by s.auth.user.collectAsState()
    val busy by s.busy.collectAsState()
    val marketStatus by s.marketStatus.collectAsState()
    val nav = rememberNavController()
    val backstack by nav.currentBackStackEntryAsState()
    val route = backstack?.destination?.route ?: "portfolio"
    val lifecycleOwner = LocalLifecycleOwner.current
    val snack = remember { SnackbarHostState() }
    var portfolio by remember { mutableStateOf<String?>(null) }
    var txOpen by remember { mutableStateOf(false) }
    var txSecurity by remember { mutableStateOf<Security?>(null) }
    var txType by remember { mutableStateOf(TxType.BUY) }
    var portfolioDialog by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Transaction?>(null) }
    var search by remember { mutableStateOf(false) }
    var searchList by remember { mutableStateOf<String?>(null) }
    var alert by remember { mutableStateOf<Security?>(null) }
    var notify by remember { mutableStateOf<Security?>(null) }
    var watch by remember { mutableStateOf<Security?>(null) }
    var selectedSecurity by remember { mutableStateOf<Security?>(null) }
    var cloud by remember { mutableStateOf(false) }
    var handledDeepLink by remember { mutableStateOf<String?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun detail(sec: Security) {
        selectedSecurity = sec
        nav.navigate("detail/${sec.id}") { launchSingleTop = true }
    }
    fun buy(sec: Security?, type: TxType = TxType.BUY) {
        edit = null
        txSecurity = sec
        txType = type
        txOpen = true
    }
    fun editTransaction(t: Transaction) {
        edit = t
        txSecurity = w.security(t.securityId)
        txOpen = true
    }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    LaunchedEffect(s, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            s.foregroundSecurities.collectLatest { visible ->
                while (isActive && visible.isNotEmpty()) {
                    val attempted = s.refreshForeground(visible)
                    val quotes = s.repo.state.value.quotes
                    val active = visible.any { security ->
                        quotes[security.id]?.let { q -> q.marketOpen == true ||
                            q.marketSession == "PRE_MARKET" || q.marketSession == "AFTER_HOURS" } == true
                    }
                    // An initial full refresh may still be running; retry promptly.
                    // The foreground loop is cancelled when the app is paused.
                    delay(if (!attempted) 5_000L else if (active) 30_000L else 180_000L)
                }
            }
        }
    }
    LaunchedEffect(route, w.transactions, portfolio, selectedSecurity) {
        when (route) {
            "portfolio" -> {
                // Prioritize positions still held; old fully sold transactions must not
                // consume the foreground quote budget before visible holdings.
                val ids = runCatching { w.result(portfolio).holdings
                    .filter { it.quantity > ZERO }.map { it.securityId }.toSet() }
                    .getOrDefault(emptySet())
                s.foregroundSecurities.value = w.securities.filter { it.id in ids }.take(20)
            }
            "detail/{id}" -> s.foregroundSecurities.value = listOfNotNull(
                w.security(backstack?.arguments?.getString("id")) ?: selectedSecurity)
            "markets" -> s.foregroundSecurities.value = Catalog.markets.take(12)
            "discover", "watchlist" -> Unit // Each screen chooses its own visible symbols.
            else -> s.foregroundSecurities.value = emptyList()
        }
    }
    LaunchedEffect(initialized, prefs["onboarded"], user) {
        if (initialized && (prefs["onboarded"] == "true" || user != null)) vm.refresh()
    }
    LaunchedEffect(deepSecurity, w.securities) {
        if (deepSecurity != null && handledDeepLink != deepSecurity)
            w.security(deepSecurity)?.let {
                detail(it)
                handledDeepLink = deepSecurity
            }
    }
    LaunchedEffect(widgetPortfolio, w.portfolios) {
        widgetPortfolio?.let { (id, _) ->
            if (id == null || w.portfolios.any { it.id == id }) {
                portfolio = id
                nav.navigate("portfolio") { launchSingleTop = true; popUpTo("portfolio") }
            }
        }
    }
    LaunchedEffect(widgetSection) {
        if (widgetSection?.first == "markets")
            nav.navigate("markets") { launchSingleTop = true; popUpTo("portfolio") }
        if (widgetSection?.first == "reports")
            nav.navigate("reports") { launchSingleTop = true; popUpTo("portfolio") }
    }
    val tabs =
        listOf(
            "portfolio" to R.string.nav_portfolio,
            "markets" to R.string.nav_markets,
            "watchlist" to R.string.nav_watchlist,
            "discover" to R.string.nav_discover,
            "news" to R.string.nav_news,
            "profile" to R.string.nav_profile,
        )
    val icons =
        listOf(
            Icons.Outlined.AccountBalanceWallet,
            Icons.Outlined.ShowChart,
            Icons.Outlined.StarOutline,
            Icons.Outlined.Explore,
            Icons.Outlined.Article,
            Icons.Outlined.PersonOutline,
        )
    val onboarded = prefs["onboarded"] == "true" || user != null
    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (onboarded)
                TopAppBar(
                    title = {
                        Text(
                            when (route) {
                                "portfolio" -> stringResource(R.string.app_name)
                                "history" -> stringResource(R.string.nav_history)
                                "reports" -> stringResource(R.string.nav_reports)
                                "detail/{id}" -> stringResource(R.string.nav_security_detail)
                                else -> tabs.find { it.first == route }?.second?.let { stringResource(it) }
                                    ?: stringResource(R.string.app_name)
                            }
                        )
                    },
                    navigationIcon = {
                        if (route !in tabs.map { it.first })
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.action_cancel))
                            }
                    },
                    actions = {
                        if (route in tabs.map { it.first }) {
                            IconButton(onClick = { vm.refresh() }, enabled = !busy) {
                                Icon(Icons.Outlined.Refresh, stringResource(R.string.action_refresh), tint = Blue)
                            }
                            IconButton(
                                onClick = {
                                    searchList = null
                                    search = true
                                }
                            ) {
                                Icon(Icons.Outlined.Search, stringResource(R.string.action_search), tint = Blue)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
                )
        },
        bottomBar = {
            if (onboarded)
                NavigationBar(containerColor = Night, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, (name, labelId) ->
                        val label = stringResource(labelId)
                        NavigationBarItem(
                            selected = route == name,
                            onClick = {
                                nav.navigate(name) {
                                    popUpTo("portfolio") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icons[i], label, Modifier.size(22.dp)) },
                            label = { Text(label, fontSize = 9.sp) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = Green,
                                    selectedTextColor = Green,
                                    indicatorColor = Green.copy(alpha = .08f),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                        )
                    }
                }
        },
        floatingActionButton = {
            if (onboarded && route == "portfolio")
                FloatingActionButton(
                    onClick = { if (w.portfolios.isEmpty()) portfolioDialog = true else buy(null) },
                    containerColor = Green,
                    contentColor = Night,
                ) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.action_add_transaction))
                }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!initialized) CircularProgressIndicator(Modifier.align(Alignment.Center))
            else if (!onboarded)
                Authentication(
                    s,
                    vm,
                    { vm.run { s.secure.preference("onboarded", "true") } },
                    { cloud = true },
                )
            else
                Column {
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (route in listOf("portfolio", "markets"))
                        Caption(marketStatus, Modifier.padding(horizontal = 18.dp, vertical = 3.dp))
                    NavHost(
                        navController = nav,
                        startDestination = "portfolio",
                        modifier = Modifier.weight(1f),
                    ) {
                        composable("portfolio") {
                            PortfolioScreen(
                                w,
                                vm,
                                portfolio,
                                { portfolio = it },
                                { detail(it) },
                                { buy(null) },
                                { nav.navigate("history") },
                                { nav.navigate("reports") },
                                { portfolioDialog = true },
                            )
                        }
                        composable("history") {
                            HistoryScreen(w, { editTransaction(it) }) { id ->
                                vm.run("Transaction supprimée") { s.repo.remove(id) }
                            }
                        }
                        composable("reports") { ReportsScreen(w) }
                        composable("markets") { MarketsScreen(w, vm) { detail(it) } }
                        composable("watchlist") {
                            WatchlistScreen(w, vm, { detail(it) }, { buy(it) }, { alert = it }) {
                                list ->
                                searchList = list
                                search = true
                            }
                        }
                        composable("discover") { DiscoverScreen(vm) { detail(it) } }
                        composable("news") { NewsScreen(w, vm) { detail(it) } }
                        composable("profile") { ProfileScreen(w, vm, biometric) }
                        composable("detail/{id}") { entry ->
                            val id = entry.arguments?.getString("id")
                            val sec = w.security(id) ?: selectedSecurity?.takeIf { it.id == id }
                            if (sec != null)
                                DetailScreen(
                                    sec,
                                    w,
                                    vm,
                                    { buy(it) },
                                    { buy(it, TxType.SELL) },
                                    { alert = it },
                                    { watch = it },
                                    { editTransaction(it) },
                                    { notify = it },
                                )
                            else
                                Empty(
                                    stringResource(R.string.ui_titre_introuvable_0f407),
                                    "Reviens à la recherche pour sélectionner un titre.",
                                )
                        }
                    }
                }
        }
    }
    if (search)
        SearchDialog(s, w, { search = false }) { sec ->
            search = false
            val list = searchList
            if (list != null)
                vm.run("Titre ajouté à la watchlist") {
                    s.repo.watch(sec, list)
                    s.refresh(listOf(sec), true)
                }
            else {
                vm.run { s.repo.put("security", sec.id, sec) }
                detail(sec)
            }
        }
    if (txOpen)
        TransactionDialog(s, w, txSecurity, portfolio, edit, { txOpen = false }, { t, sec ->
            vm.run {
                val result = s.repo.current().result(t.portfolioId)
                val h = result.holdings.find { it.securityId == sec?.id }
                vm.messages.emit(
                    "${if (t.type == TxType.BUY) "Achat ajouté" else "Transaction enregistrée"} · ${date(t.date)}" +
                        (if (h?.average != null) " · Prix moyen ${money(h.average, t.currency)}"
                        else "")
                )
                if (sec != null) s.refresh(listOf(sec), true)
            }
            if (
                t.type == TxType.BUY &&
                    sec != null &&
                    w.notifications.none { it.securityId == sec.id && (it.earnings || it.news) }
            )
                notify = sec
        }, txType)
    if (portfolioDialog) PortfolioDialog(null, { portfolioDialog = false }) {
        vm.run { s.repo.put("portfolio", it.id, it) }
    }
    alert?.let { sec ->
        AlertDialogForm(s, sec, { alert = null }) {
            askPermission()
            vm.run("Alerte créée") { s.refresh(listOf(sec)) }
        }
    }
    notify?.let { sec -> NotificationDialog(s, sec, { notify = null }, { askPermission() }) }
    watch?.let { sec ->
        ChooseWatchlist(w, { watch = null }) { list ->
            vm.run("Titre ajouté") { s.repo.watch(sec, list) }
            watch = null
        }
    }
    if (cloud) SyncSettings(w, vm) { cloud = false }
}
