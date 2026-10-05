@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ca.monwallet.app.ui

import ca.monwallet.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ca.monwallet.app.domain.*
import ca.monwallet.app.marketdata.OfficialLogoProvider
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Night = Color(0xFF030E15)
val Panel = Color(0xFF0D1D29)
val Muted = Color(0xFF93A7B7)
val Green = Color(0xFF56F85D)
val Red = Color(0xFFFF596E)
val Blue = Color(0xFF6BBFFF)
val Border = Color(0xFF1B303D)

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Green,
                onPrimary = Night,
                background = Night,
                surface = Panel,
                surfaceVariant = Panel,
                onSurface = Color(0xFFF1F6FA),
                onSurfaceVariant = Muted,
                outline = Border,
                error = Red,
                secondary = Blue,
            ),
        typography = Typography(),
        content = content,
    )
}

fun number(v: BigDecimal?, digits: Int = 2): String =
    if (v == null) "—"
    else
        NumberFormat.getNumberInstance(if (Locale.getDefault().language == "en") Locale.CANADA else Locale.CANADA_FRENCH)
            .apply {
                minimumFractionDigits = if (digits == 2) 2 else 0
                maximumFractionDigits = digits
            }
            .format(v)

fun money(v: BigDecimal?, currency: String = "CAD") =
    if (v == null) "—"
    else if (currency == "CAD" && Locale.getDefault().language == "en") "$${number(v)}"
    else "${number(v)} ${if (currency == "CAD") "$" else currency}"

fun financialValue(label: String, raw: String, currency: String): String {
    val value = raw.toBigDecimalOrNull() ?: return raw
    if (label.endsWith("%")) return "${number(value)} %"
    if (label == "Capitalisation (M)") return "${number(value)} M $currency"
    if (label in setOf("Revenus", "Bénéfice net", "Cash", "Dette totale", "Dette nette",
            "Dette long terme", "Dette court terme", "Operating cash flow", "Free cash flow")) {
        val scale = when {
            value.abs() >= BigDecimal("1000000000000") -> BigDecimal("1000000000000") to "T"
            value.abs() >= BigDecimal("1000000000") -> BigDecimal("1000000000") to "G"
            else -> BigDecimal("1000000") to "M"
        }
        return "${number(value.divide(scale.first, MC))} ${scale.second} $currency"
    }
    if (label in setOf("BPA", "Dividende annuel", "Sommet 52 semaines", "Creux 52 semaines"))
        return money(value, currency)
    return number(value, if (label.startsWith("Volume")) 0 else 2)
}

fun percent(v: BigDecimal?) = if (v == null) "—" else "${if(v>ZERO)"+"else""}${number(v)} %"

fun signed(v: BigDecimal?, currency: String = "CAD") =
    if (v == null) "—" else "${if(v>ZERO)"+"else""}${money(v,currency)}"

fun tint(v: BigDecimal?) = if (v == null) Muted else if (v >= ZERO) Green else Red

@Composable
fun transactionLabel(type: TxType): String = stringResource(
    when (type) {
        TxType.BUY -> R.string.tx_buy
        TxType.SELL -> R.string.tx_sell
        TxType.DIVIDEND -> R.string.tx_dividend
        TxType.DEPOSIT -> R.string.tx_deposit
        TxType.WITHDRAWAL -> R.string.tx_withdrawal
        TxType.FEE -> R.string.tx_fee
    }
)

fun time(ms: Long) =
    Instant.ofEpochMilli(ms)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.getDefault()))

fun date(raw: String) =
    runCatching {
            LocalDate.parse(raw)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        }
        .getOrDefault(raw)

@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Muted, fontSize = 12.sp)
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp) }
    }
}

@Composable
fun Empty(title: String, body: String, button: String? = null, action: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Caption(body)
        if (button != null) Button(onClick = action) { Text(button) }
    }
}

@Composable
fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Metric(
    label: String,
    value: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Caption(label)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
fun Chips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(label, fontSize = 12.sp) },
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        color = Blue,
        modifier =
            Modifier.clip(RoundedCornerShape(5.dp))
                .background(Blue.copy(alpha = .1f))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        maxLines = 1,
    )
}

@Composable
fun Logo(s: Security, size: Dp = 34.dp) {
    val context = LocalContext.current
    var bitmap by remember(s.id, s.symbol, s.exchange, s.currency, s.type) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(s.id, s.symbol, s.exchange, s.currency, s.type) {
        bitmap = OfficialLogoProvider.load(context, s)
    }
    val colors = listOf(Color(0xFF00A8D4), Color(0xFFCF2746), Color(0xFF16845D), Color(0xFF4575DF))
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(if (bitmap != null) Color.White else colors[(s.symbol.hashCode() and Int.MAX_VALUE) % colors.size]),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = s.name,
                modifier = Modifier.fillMaxSize().padding(if (size < 28.dp) 1.dp else 3.dp), contentScale = ContentScale.Fit)
        } ?: Text(s.ticker.take(2), fontSize = if (size < 28.dp) 8.sp else 12.sp,
            fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun Chart(
    values: List<BigDecimal?>,
    capital: List<BigDecimal?> = emptyList(),
    modifier: Modifier = Modifier.height(150.dp),
    color: Color = Green,
    labels: Boolean = false,
) {
    val known = (values + capital).filterNotNull()
    if (known.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Caption(stringResource(R.string.ui_historique_indisponible_fb840))
        }
        return
    }
    val low = known.minOf { it.toDouble() }
    val high = known.maxOf { it.toDouble() }
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    Column {
        Canvas(modifier.fillMaxWidth()) {
            val pad = 8.dp.toPx()
            val h = size.height - pad * 2
            fun y(v: BigDecimal) = size.height - pad - ((v.toDouble() - low) / span * h).toFloat()
            fun x(i: Int, n: Int) = if (n < 2) size.width / 2 else i * size.width / (n - 1)
            repeat(3) {
                val yy = pad + h * it / 2
                drawLine(Border.copy(alpha = .6f), Offset(0f, yy), Offset(size.width, yy), 1f)
            }
            fun path(list: List<BigDecimal?>): Path {
                val p = Path()
                var started = false
                list.forEachIndexed { i, v ->
                    if (v == null) started = false
                    else {
                        if (!started) p.moveTo(x(i, list.size), y(v))
                        else p.lineTo(x(i, list.size), y(v))
                        started = true
                    }
                }
                return p
            }
            if (values.count { it != null } > 1) {
                val p = path(values)
                drawPath(p, color.copy(alpha = .1f), style = Stroke(8.dp.toPx()))
                drawPath(p, color, style = Stroke(2.dp.toPx()))
            } else
                values
                    .indexOfFirst { it != null }
                    .takeIf { it >= 0 }
                    ?.let {
                        drawCircle(color, 3.dp.toPx(), Offset(x(it, values.size), y(values[it]!!)))
                    }
            if (capital.isNotEmpty())
                drawPath(
                    path(capital),
                    Muted,
                    style =
                        Stroke(
                            1.4.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)),
                        ),
                )
        }
        if (labels)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Caption(number(known.minOrNull()))
                Caption(number(known.maxOrNull()))
            }
    }
}

@Composable
fun FullDialog(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.ui_fermer_5ab4e)) }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
fun TextEntry(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    number: Boolean = false,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType =
                    if (number) androidx.compose.ui.text.input.KeyboardType.Decimal
                    else androidx.compose.ui.text.input.KeyboardType.Text
            ),
        visualTransformation =
            if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
fun Choice(label: String, value: String, options: List<String>, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label : $value", Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(open, { open = false }) {
            options.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        open = false
                        onChange(i)
                    },
                )
            }
        }
    }
}

@Composable
fun Confirm(title: String, body: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onClose()
                }
            ) {
                Text(stringResource(R.string.ui_confirmer_80a66), color = Red)
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ui_annuler_49ba3)) } },
    )
}
