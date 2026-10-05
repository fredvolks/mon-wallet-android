package ca.monwallet.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import ca.monwallet.app.R
import ca.monwallet.app.domain.AllocationResult
import kotlin.math.atan2

private val donutColors = listOf(Green, Blue, Color(0xFF61CCAA), Color(0xFFA48AFF), Color(0xFFFFC857), Red)

@Composable
fun AllocationDonut(allocation: AllocationResult) {
    val slices = allocation.slices
    val total = allocation.total ?: return
    var selected by remember(slices) { mutableIntStateOf(-1) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(slices) {
                    detectTapGestures { point ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val delta = point - center
                        val angle = ((Math.toDegrees(atan2(delta.y.toDouble(), delta.x.toDouble())) + 90 + 360) % 360).toFloat()
                        var cumulative = 0f
                        selected = slices.indexOfFirst { slice ->
                            cumulative += slice.weight.toFloat() * 3.6f
                            angle < cumulative
                        }
                    }
                }
            ) {
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = slice.weight.toFloat() * 3.6f
                    drawArc(donutColors[index % donutColors.size], start, sweep, false, style = Stroke(24.dp.toPx()))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (selected in slices.indices) slices[selected].label else stringResource(R.string.allocation_total), color = Muted, fontSize = 12.sp)
                Text(money(if (selected in slices.indices) slices[selected].value else total), fontWeight = FontWeight.Bold)
                if (selected in slices.indices) Text(percent(slices[selected].weight), color = Green)
            }
        }
        slices.forEachIndexed { index, slice ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("● ${slice.label}", color = donutColors[index % donutColors.size])
                Text("${money(slice.value)} · ${percent(slice.weight)}")
            }
        }
    }
}
