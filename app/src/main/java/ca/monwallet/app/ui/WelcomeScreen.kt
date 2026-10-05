package ca.monwallet.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.monwallet.app.LocaleController
import ca.monwallet.app.R
import ca.monwallet.app.Services
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent

@Composable
fun LanguageChoice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var expanded by remember { mutableStateOf(false) }
    val choice = LocaleController.language(context)
    Box(modifier) {
        Row(
            Modifier.clip(RoundedCornerShape(25.dp))
                .border(1.dp, Border, RoundedCornerShape(25.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Language, null, Modifier.size(18.dp), tint = Blue)
            Text(
                stringResource(if (choice == "fr") R.string.language_french else R.string.language_english),
                fontSize = 12.sp,
            )
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(16.dp), tint = Muted)
        }
        DropdownMenu(expanded, { expanded = false }) {
            listOf("fr" to R.string.language_french, "en" to R.string.language_english)
                .forEach { (code, label) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            expanded = false
                            activity?.let { LocaleController.select(it, code) }
                        },
                    )
                }
        }
    }
}

@Composable
fun Authentication(s: Services, vm: WalletViewModel, onGuest: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var migrate by remember { mutableStateOf(true) }
    var emailOpen by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf(false) }
    var retryGoogleMigration by remember { mutableStateOf<Boolean?>(null) }
    var googleMigrationPrompt by remember { mutableStateOf(false) }
    val wallet by vm.wallet.collectAsState()
    fun googleSignIn(migrateGuest: Boolean) {
        retryGoogleMigration = migrateGuest
        vm.run {
            try {
                s.auth.google(context, migrateGuest)
                onGuest()
                s.sync.sync()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { signInError = true }
        }
    }

    if (googleMigrationPrompt) AlertDialog(
        onDismissRequest = { googleMigrationPrompt = false },
        title = { Text(stringResource(R.string.google_migration_title)) },
        text = { Text(stringResource(R.string.google_migration_body)) },
        confirmButton = {
            TextButton(onClick = { googleMigrationPrompt = false; googleSignIn(true) }) {
                Text(stringResource(R.string.google_migration_sync))
            }
        },
        dismissButton = {
            TextButton(onClick = { googleMigrationPrompt = false; googleSignIn(false) }) {
                Text(stringResource(R.string.google_migration_skip))
            }
        },
    )

    if (signInError)
        AlertDialog(
            onDismissRequest = { signInError = false },
            title = { Text(stringResource(R.string.cloud_configuration)) },
            text = { Text(stringResource(R.string.cloud_unavailable)) },
            confirmButton = {
                TextButton(onClick = {
                    signInError = false
                    retryGoogleMigration?.let { googleSignIn(it) }
                }) {
                    Text(stringResource(R.string.action_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { signInError = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF031018), Night, Color(0xFF061825)))
        ),
    ) {
        val contentWidth = maxWidth.coerceAtMost(490.dp)
        Box(Modifier.width(contentWidth).fillMaxHeight().align(Alignment.TopCenter)) {
            PhonePreview(
                Modifier.align(Alignment.TopEnd).offset(x = 56.dp, y = 78.dp)
                    .rotate(8f),
            )
            Box(
                Modifier.align(Alignment.TopStart).fillMaxWidth().height(350.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Night.copy(alpha = .96f), Night.copy(alpha = .78f), Color.Transparent)
                        )
                    )
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Image(
                        painterResource(R.drawable.ic_brand_wallet),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(58.dp),
                    )
                    LanguageChoice()
                }
                Spacer(Modifier.height(56.dp))
                Text(
                    stringResource(R.string.portfolio_tracker),
                    color = Muted,
                    letterSpacing = 3.sp,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))
                val first = stringResource(R.string.brand_first)
                val second = stringResource(R.string.brand_second)
                Text(
                    buildAnnotatedString {
                        append(first)
                        append(" ")
                        withStyle(SpanStyle(color = Green)) { append(second) }
                    },
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.welcome_tagline),
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.already_account), color = Muted, fontSize = 13.sp)
                    Text(
                        stringResource(R.string.sign_in),
                        Modifier.clickable {
                            if (s.secure.configured) emailOpen = true else signInError = true
                        }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                        color = Green,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = Green)
                }
                Spacer(Modifier.height(14.dp))
                listOf(
                    Triple(R.string.continue_google, "google", Icons.Outlined.AccountCircle),
                    Triple(R.string.continue_apple, "apple", Icons.Outlined.PhoneIphone),
                    Triple(R.string.continue_microsoft, "azure", Icons.Outlined.Window),
                ).forEach { (label, provider, icon) ->
                    OutlinedButton(
                        onClick = {
                            if (provider == "google") {
                                if (wallet.portfolios.isNotEmpty() || wallet.transactions.isNotEmpty() || wallet.watchlists.isNotEmpty() || wallet.alerts.isNotEmpty())
                                    googleMigrationPrompt = true
                                else googleSignIn(false)
                            } else if (!s.secure.configured) { retryGoogleMigration = null; signInError = true }
                            else vm.run {
                                CustomTabsIntent.Builder().build().launchUrl(context, s.auth.oauth(provider, migrate))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(48.dp),
                        shape = RoundedCornerShape(23.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Panel.copy(alpha = .92f)),
                    ) {
                        Icon(icon, null, Modifier.size(21.dp), tint = Color.White)
                        Spacer(Modifier.width(15.dp))
                        Text(stringResource(label), Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Muted)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = Border)
                    Text(stringResource(R.string.or), Modifier.padding(horizontal = 12.dp), color = Muted)
                    HorizontalDivider(Modifier.weight(1f), color = Border)
                }
                OutlinedButton(
                    onClick = { if (s.secure.configured) emailOpen = !emailOpen else signInError = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(23.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Panel.copy(alpha = .92f)),
                ) {
                    Icon(Icons.Outlined.Email, null, Modifier.size(21.dp), tint = Color.White)
                    Spacer(Modifier.width(15.dp))
                    Text(stringResource(R.string.continue_email), Modifier.weight(1f), color = Color.White, fontSize = 14.sp)
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Muted)
                }
                if (emailOpen && s.secure.configured) {
                    Spacer(Modifier.height(10.dp))
                    TextEntry(stringResource(R.string.email_label), address, { address = it })
                    if (sent) TextEntry(stringResource(R.string.email_code_label), code, { code = it }, true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(migrate, { migrate = it })
                        Text(stringResource(R.string.email_migrate), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            vm.run {
                                if (sent) {
                                    s.auth.verify(address.trim(), code.trim(), migrate)
                                    onGuest()
                                    runCatching { s.sync.sync() }
                                } else {
                                    s.auth.sendCode(address.trim())
                                    sent = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(if (sent) R.string.sign_in else R.string.email_send_code))
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onGuest,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Green),
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(20.dp), tint = Green)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.guest_mode), Modifier.weight(1f), color = Green, fontSize = 13.sp)
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = Green)
                }
                Text(
                    stringResource(R.string.guest_privacy),
                    Modifier.padding(top = 13.dp, bottom = 24.dp),
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                val benefits = listOf(
                    R.string.benefit_portfolios to Icons.Outlined.AccountBalanceWallet,
                    R.string.benefit_quotes to Icons.Outlined.TrendingUp,
                    R.string.benefit_alerts to Icons.Outlined.NotificationsNone,
                    R.string.benefit_markets to Icons.Outlined.Public,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    benefits.forEach { (label, icon) ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(icon, null, Modifier.size(24.dp), tint = Muted)
                            Text(
                                stringResource(label),
                                Modifier.padding(top = 8.dp),
                                color = Muted,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun PhonePreview(modifier: Modifier = Modifier) {
    Column(
        modifier.width(220.dp).height(316.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF061B29), Color(0xFF031019))))
            .border(2.dp, Color(0xFF254155), RoundedCornerShape(27.dp))
            .padding(17.dp),
    ) {
        Text(stringResource(R.string.welcome_preview), color = Green, fontSize = 10.sp)
        Text(stringResource(R.string.preview_portfolio), color = Color.White, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.preview_value), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.preview_performance), color = Green, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(57.dp)) {
            val y = listOf(.76f,.65f,.69f,.52f,.59f,.35f,.43f,.38f,.58f,.51f,.28f,.37f,.22f,.27f,.10f,.18f,.09f)
            val line = Path().apply {
                moveTo(0f, size.height * y.first())
                y.drop(1).forEachIndexed { i, p ->
                    lineTo(size.width * (i + 1) / (y.size - 1), size.height * p)
                }
            }
            drawPath(line, Green, style = Stroke(width = 2.3.dp.toPx()))
            drawLine(Border, Offset(0f, size.height * .88f), Offset(size.width, size.height * .88f))
        }
        Text(stringResource(R.string.preview_periods), color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(12.dp))
        listOf(
            Triple("XEQT", R.string.preview_xeqt_price, R.string.preview_xeqt_change),
            Triple("TSM", R.string.preview_tsm_price, R.string.preview_tsm_change),
            Triple("MCD", R.string.preview_mcd_price, R.string.preview_mcd_change),
        ).forEach { (ticker, price, change) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ticker, color = Color.White, fontSize = 12.sp)
                    Text(stringResource(price), color = Muted, fontSize = 10.sp)
                    Text(stringResource(change), color = Green, fontSize = 10.sp)
                }
                HorizontalDivider(color = Border.copy(alpha = .5f))
            }
    }
}
