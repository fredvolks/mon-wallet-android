package ca.monwallet.app.widgets

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ca.monwallet.app.*
import ca.monwallet.app.data.Catalog
import ca.monwallet.app.domain.heldSecurityIds
import kotlinx.coroutines.*

/** User-visible five-second widget session. No wake lock and no polling while locked. */
class WidgetLiveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val services get() = (application as MonWallet).services

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            services.secure.put("widget_live", "false")
            stopSelf()
            return START_NOT_STICKY
        }
        services.secure.put("widget_live", "true")
        val channel = "widget_live"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Suivi du widget", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 5051,
            Intent(this, WidgetLiveService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 5052, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Mon Wallet · suivi actif")
            .setContentText("Vérification aux 5 s · pause écran éteint ou verrouillé")
            .setOngoing(true).setSilent(true).setContentIntent(open)
            .addAction(0, "Arrêter", stop).build()
        try { startForeground(5050, notification) }
        catch (error: Exception) {
            android.util.Log.w("MonWalletLive", "Live widget session unavailable", error)
            stopSelf()
            return START_NOT_STICKY
        }
        services.widgetLiveActive.value = true
        if (loop?.isActive != true) loop = scope.launch {
            while (isActive) {
                val ids = WalletWidget.ids(this@WidgetLiveService)
                if (ids.isEmpty()) { stopSelf(); break }
                val started = SystemClock.elapsedRealtime()
                var failed = false
                if (services.initialized.value && screenAllowsRefresh(
                        getSystemService(PowerManager::class.java).isInteractive,
                        getSystemService(KeyguardManager::class.java).isKeyguardLocked)) {
                    try {
                        val wallet = services.repo.current()
                        val tracked = wallet.heldSecurityIds()
                        val securities = (wallet.securities.filter { it.id in tracked } +
                            services.foregroundSecurities.value + Catalog.markets.filter {
                                it.symbol in setOf("CAD=X", "^GSPC", "^IXIC", "^DJI", "ES=F", "NQ=F", "YM=F")
                            }).distinctBy { it.id }.take(20)
                        services.refreshForeground(securities)
                        failed = services.marketStatus.value.startsWith("Cotation indisponible")
                        WalletWidget.render(this@WidgetLiveService, ids.toIntArray())
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        failed = true
                        android.util.Log.w("MonWalletLive", "Widget update failed; cached data retained", e)
                    }
                }
                val period = if (failed) 60_000L else 5_000L
                delay((period - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(250L))
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() {
        services.widgetLiveActive.value = false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val STOP = "ca.monwallet.STOP_WIDGET_LIVE"
        internal fun screenAllowsRefresh(interactive: Boolean, locked: Boolean) = interactive && !locked
        fun start(context: Context, explicit: Boolean = false) {
            val s = (context.applicationContext as MonWallet).services
            if (explicit) s.secure.put("widget_live", "true")
            if (s.widgetLiveActive.value || s.secure.get("widget_live") == "false" ||
                WalletWidget.ids(context).isEmpty()) return
            try { ContextCompat.startForegroundService(context, Intent(context, WidgetLiveService::class.java)) }
            catch (error: Exception) { android.util.Log.w("MonWalletLive", "Open app to resume live session", error) }
        }
        fun stop(context: Context) {
            val s = (context.applicationContext as MonWallet).services
            s.secure.put("widget_live", "false")
            context.stopService(Intent(context, WidgetLiveService::class.java))
        }
        fun refreshIntent(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getForegroundService(context, widgetId + 50500,
                Intent(context, WidgetLiveService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
