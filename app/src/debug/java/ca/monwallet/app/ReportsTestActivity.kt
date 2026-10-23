package ca.monwallet.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Dedicated host for report rendering tests; absent from the signed release APK. */
class ReportsTestActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(LocaleController.wrap(base)) }
    companion object { var content: (@Composable () -> Unit)? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content?.invoke() }
    }
}
