package ca.monwallet.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.monwallet.app.MonWallet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    val services = (app as MonWallet).services
    val wallet = services.repo.state
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun run(success: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                success?.let { messages.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.emit(e.message ?: "Une erreur est survenue.")
                android.widget.Toast.makeText(getApplication(), e.message ?: "Une erreur est survenue.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refresh() = run { services.refresh(history = true) }
}
