package ca.monwallet.app.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ca.monwallet.app.R
import ca.monwallet.app.domain.Point
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewChartView(points: List<Point>, kind: String) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val view = remember(context) {
        WebView(context).apply {
            setBackgroundColor(Color.rgb(8, 17, 30))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.blockNetworkLoads = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { loaded = true }
                override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
            }
            loadUrl("file:///android_asset/lwc/chart.html")
        }
    }
    DisposableEffect(view) { onDispose { view.stopLoading(); view.destroy() } }
    val rows = JSONArray()
    points.sortedBy { it.timestamp }.distinctBy { it.timestamp }.forEach { point ->
        rows.put(JSONObject().put("time", point.timestamp / 1000)
            .put("close", point.close.toDouble())
            .put("open", point.open?.toDouble())
            .put("high", point.high?.toDouble())
            .put("low", point.low?.toDouble())
            .put("volume", point.volume?.toDouble()))
    }
    val payload = JSONObject().put("kind", kind).put("points", rows)
        .put("positive", points.lastOrNull()?.close?.let { last -> points.firstOrNull()?.close?.let { last >= it } } ?: true)
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(252.dp), update = {
        if (loaded && points.isNotEmpty()) it.evaluateJavascript("renderWalletChart(${payload})", null)
    })
    TextButton(onClick = { view.evaluateJavascript("resetWalletChart()", null) }) {
        Text(stringResource(R.string.chart_reset))
    }
}
