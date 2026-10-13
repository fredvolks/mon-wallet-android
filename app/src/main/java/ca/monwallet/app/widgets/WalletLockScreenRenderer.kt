package ca.monwallet.app.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import ca.monwallet.app.MainActivity
import ca.monwallet.app.R
import ca.monwallet.app.domain.Wallet
import ca.monwallet.app.domain.ZERO
import ca.monwallet.app.ui.signed

internal object WalletLockScreenRenderer {
    fun render(
        context: Context,
        widgetId: Int,
        wallet: Wallet,
        portfolioId: String?,
        result: ca.monwallet.app.domain.Result?,
        hidden: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.wallet_widget_lockscreen)
        val portfolioName = if (portfolioId == null) context.getString(R.string.all_portfolios)
            else wallet.portfolios.find { it.id == portfolioId }?.name
                ?: context.getString(R.string.app_name)
        views.setTextViewText(R.id.widget_lock_title,
            if (hidden) context.getString(R.string.widget_locked)
            else "P&L du jour · " + portfolioName + " · CAD")
        views.removeAllViews(R.id.widget_lock_rows)
        val holdings = if (hidden) emptyList() else result?.holdings
            ?.filter { it.quantity > ZERO }
            ?.sortedByDescending { it.value ?: ZERO }
            .orEmpty()
        holdings.forEach { holding ->
            val security = wallet.security(holding.securityId) ?: return@forEach
            val row = RemoteViews(context.packageName, R.layout.wallet_widget_lockscreen_row)
            row.setTextViewText(R.id.widget_lock_ticker, security.ticker)
            row.setTextViewText(R.id.widget_lock_pnl, signed(holding.day))
            val tone = when (holding.day?.signum()) {
                null, 0 -> Color.rgb(205, 220, 229)
                -1 -> Color.rgb(255, 92, 113)
                else -> Color.rgb(83, 244, 141)
            }
            row.setTextColor(R.id.widget_lock_pnl, tone)
            views.addView(R.id.widget_lock_rows, row)
        }
        if (!hidden && holdings.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.wallet_widget_lockscreen_row)
            empty.setTextViewText(R.id.widget_lock_ticker, context.getString(R.string.widget_no_holdings))
            empty.setTextViewText(R.id.widget_lock_pnl, "")
            views.addView(R.id.widget_lock_rows, empty)
        }
        views.setOnClickPendingIntent(R.id.widget_lock_root,
            PendingIntent.getActivity(context, widgetId + 51000,
                Intent(context, MainActivity::class.java).putExtra("portfolio", portfolioId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }
}
