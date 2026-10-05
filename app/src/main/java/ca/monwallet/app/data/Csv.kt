package ca.monwallet.app.data

import ca.monwallet.app.database.Record
import ca.monwallet.app.domain.*
import java.time.LocalDate

object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (quoted && i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else quoted = !quoted
                }
                c == ',' && !quoted -> {
                    row += field.toString()
                    field.clear()
                }
                c == '\n' && !quoted -> {
                    row += field.toString().removeSuffix("\r")
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "Guillemets CSV non fermés." }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString().removeSuffix("\r")
            rows += row.toList()
        }
        return rows
    }

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun export(w: Wallet): String {
        val head =
            listOf(
                "id",
                "date",
                "ticker",
                "quantity",
                "price",
                "currency",
                "fees",
                "portfolio",
                "type",
                "fxRate",
                "exchange",
                "note",
                "fxDate",
            )
        val rows =
            w.transactions
                .sortedBy { it.date }
                .map { t ->
                    listOf(
                        t.id,
                        t.date,
                        w.security(t.securityId)?.symbol ?: "",
                        t.quantity.toPlainString(),
                        t.price.toPlainString(),
                        t.currency,
                        t.fees.toPlainString(),
                        w.portfolios.find { it.id == t.portfolioId }?.name ?: "",
                        t.type.name,
                        t.fxRate.toPlainString(),
                        w.security(t.securityId)?.exchange ?: "",
                        t.note,
                        t.fxDate,
                    )
                }
        return (listOf(head) + rows).joinToString("\n") {
            it.joinToString(",", transform = ::quote)
        }
    }

    data class Preview(val records: List<Record>, val count: Int)

    suspend fun preview(text: String, repo: Repository): Preview {
        val rows = parse(text.removePrefix("\uFEFF"))
        require(rows.size > 1) { "Le CSV est vide." }
        val headers = rows.first().map { it.trim() }
        val w = repo.current()
        val portfolios = w.portfolios.toMutableList()
        val securities = w.securities.toMutableList()
        val records = mutableListOf<Record>()
        val transactions = mutableListOf<Transaction>()
        fun record(kind: String, id: String, value: Any) {
            val now = System.currentTimeMillis()
            records += Record(id, repo.owner.value, kind, repo.gson.toJson(value), now, now)
        }
        rows.drop(1).forEachIndexed { index, row ->
            fun f(name: String) =
                headers
                    .indexOf(name)
                    .takeIf { it >= 0 }
                    ?.let { row.getOrNull(it) }
                    ?.trim()
                    .orEmpty()
            try {
                val date = LocalDate.parse(f("date")).toString()
                require(date <= LocalDate.now().toString()) { "Date future." }
                val type = TxType.valueOf(f("type").ifBlank { "BUY" }.uppercase())
                val currency = f("currency").ifBlank { "CAD" }.uppercase()
                require(currency in listOf("CAD", "USD"))
                val fx =
                    if (currency == "CAD") ONE
                    else
                        f("fxRate").takeIf { it.isNotBlank() }?.dec()
                            ?: error("fxRate historique USD/CAD obligatoire.")
                val name = f("portfolio").ifBlank { "Import" }
                val p =
                    portfolios.find { it.name == name }
                        ?: Portfolio(name = name, type = "AUTRE").also {
                            portfolios += it
                            record("portfolio", it.id, it)
                        }
                val symbol = f("ticker").uppercase()
                val sec =
                    if (symbol.isBlank()) null
                    else
                        securities.find {
                            it.symbol == symbol &&
                                it.currency == currency &&
                                (f("exchange").isBlank() || it.exchange == f("exchange"))
                        }
                            ?: Security.of(
                                    symbol,
                                    symbol,
                                    f("exchange").ifBlank {
                                        error("Marché exchange requis pour $symbol.")
                                    },
                                    currency,
                                )
                                .also {
                                    securities += it
                                    record("security", it.id, it)
                                }
                val t =
                    Transaction(
                        id =
                            f("id").ifBlank {
                                java.util.UUID.nameUUIDFromBytes(
                                        (row.joinToString("|") + index).toByteArray()
                                    )
                                    .toString()
                            },
                        portfolioId = p.id,
                        securityId = sec?.id,
                        type = type,
                        quantity = f("quantity").ifBlank { "0" }.dec(),
                        price = f("price").dec(),
                        currency = currency,
                        fxRate = fx,
                        fees = f("fees").ifBlank { "0" }.dec(),
                        date = date,
                        fxDate = f("fxDate").ifBlank { date },
                        note = f("note"),
                    )
                transactions += t
                record("transaction", t.id, t)
            } catch (e: Exception) {
                error("Ligne ${index+2} : ${e.message?:"donnée invalide"}")
            }
        }
        Engine.validate(
            w.transactions.filter { old -> transactions.none { it.id == old.id } } + transactions
        )
        return Preview(records, transactions.size)
    }

    suspend fun import(preview: Preview, repo: Repository) =
        repo.restore(repo.gson.toJson(Repository.Backup(1, preview.records)))
}
