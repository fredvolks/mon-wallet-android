package ca.monwallet.app

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import ca.monwallet.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.wrap(newBase))
    }

    private var locked by mutableStateOf(false)
    private var deepSecurity by mutableStateOf<String?>(null)
    private var widgetPortfolio by mutableStateOf<Pair<String?, Int>?>(null)
    private var widgetSection by mutableStateOf<Pair<String, Int>?>(null)
    private var widgetOpenCount = 0
    private val services
        get() = (application as MonWallet).services

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locked = services.secure.get("biometric") == "true"
        handle(intent)
        setContent {
            WalletTheme {
                if (locked) LockScreen { authenticate { locked = false } }
                else WalletApp(deepSecurity, widgetPortfolio, widgetSection) { action -> authenticate(action) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        deepSecurity = intent.getStringExtra("security")
        intent.getStringExtra("section")?.let { section ->
            widgetOpenCount++
            widgetSection = section to widgetOpenCount
        }
        if (intent.hasExtra("portfolio")) {
            widgetOpenCount++
            widgetPortfolio = intent.getStringExtra("portfolio") to widgetOpenCount
        }
        intent.data
            ?.takeIf { it.scheme == "monwallet" && it.host == "auth" }
            ?.let { uri ->
                lifecycleScope.launch {
                    try {
                        services.auth.callback(uri)
                        services.secure.preference("onboarded", "true")
                        runCatching { services.sync.sync() }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                                this@MainActivity,
                                e.message ?: "Connexion impossible.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
            }
    }

    override fun onStop() {
        super.onStop()
        if (services.secure.get("biometric") == "true") locked = true
    }

    override fun onResume() {
        super.onResume()
        if (services.secure.get("biometric") == "true")
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun authenticate(success: () -> Unit) {
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (
            androidx.biometric.BiometricManager.from(this).canAuthenticate(authenticators) !=
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        ) {
            android.widget.Toast.makeText(
                    this,
                    "Configure une empreinte ou un verrouillage sécurisé dans les réglages Android.",
                    android.widget.Toast.LENGTH_LONG,
                )
                .show()
            return
        }
        val prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        success()
                    }
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Mon Wallet")
                .setSubtitle("Confirme ton identité sur cet appareil")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}
