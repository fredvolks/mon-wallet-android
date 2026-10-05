package ca.monwallet.app.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import ca.monwallet.app.BuildConfig
import ca.monwallet.app.R
import ca.monwallet.app.Services
import ca.monwallet.app.data.*
import ca.monwallet.app.database.Record
import ca.monwallet.app.domain.*
import ca.monwallet.app.notifications.Notifications
import ca.monwallet.app.updates.Updater
import ca.monwallet.app.widgets.*
import kotlinx.coroutines.*
import org.json.JSONObject

@Composable
fun ProfileScreen(w: Wallet, vm: WalletViewModel, biometric: (() -> Unit) -> Unit) {
    val s = vm.services
    val context = LocalContext.current
    val user by s.auth.user.collectAsState()
    val email by s.auth.email.collectAsState()
    val sync by s.sync.status.collectAsState()
    var page by remember { mutableStateOf<String?>(null) }
    var portfolioForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Portfolio?>(null) }
    var deletePortfolio by remember { mutableStateOf<String?>(null) }
    var signout by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var csvPreview by remember { mutableStateOf<Csv.Preview?>(null) }
    var importType by remember { mutableStateOf("json") }
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null)
                vm.run("Export enregistré") {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(exportContent.toByteArray())
                        } ?: error("Impossible d’écrire ce fichier.")
                    }
                }
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.run {
                    val raw =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val buffer = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(chunk)
                                    if (count < 0) break
                                    require(buffer.size() + count <= 10 * 1024 * 1024) {
                                        "Fichier trop volumineux (max. 10 Mo)."
                                    }
                                    buffer.write(chunk, 0, count)
                                }
                                buffer.toString("UTF-8")
                            } ?: error("Fichier illisible.")
                        }
                    if (importType == "csv") csvPreview = Csv.preview(raw, s.repo)
                    else {
                        val backup = s.repo.gson.fromJson(raw, Repository.Backup::class.java)
                        require(backup.formatVersion == 1)
                        imported = raw to backup.records.size
                    }
                }
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item {
            CardBlock {
                Icon(Icons.Outlined.PersonOutline, null, Modifier.size(38.dp), tint = Blue)
                Text(
                    if (user == null) stringResource(R.string.profile_guest) else stringResource(R.string.profile_my_account),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
                Caption(
                    email?.let { it.take(3) + "*****@" + it.substringAfter('@') }
                        ?: stringResource(R.string.profile_local_data)
                )
                Text(
                    if (user == null) stringResource(R.string.profile_local_transactions, w.transactions.size) else sync,
                    color = if (sync.startsWith("✓")) Green else Muted,
                    fontSize = 12.sp,
                )
                if (user == null)
                    TextButton(onClick = { page = "auth" }) {
                        Text(stringResource(R.string.profile_signin))
                    }
            }
        }
        items(
            listOf(
                "Compte" to R.string.profile_account,
                "Portefeuilles" to R.string.profile_portfolios,
                "Notifications" to R.string.profile_notifications,
                "Widgets" to R.string.profile_widgets,
                "Sécurité" to R.string.profile_security,
                "Synchronisation" to R.string.profile_sync,
                "Appareils" to R.string.profile_devices,
                "Source des données" to R.string.profile_data_source,
                "Export et sauvegarde" to R.string.profile_export,
                "Paramètres" to R.string.profile_settings,
                "Mises à jour" to R.string.profile_updates,
                "À propos" to R.string.profile_about,
            )
        ) { (key, labelId) ->
            Row(
                Modifier.fillMaxWidth().clickable { page = key }.padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(labelId), Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
            }
            HorizontalDivider(color = Border)
        }
        if (user != null) item { TextButton(onClick = { signout = true }) { Text(stringResource(R.string.profile_logout)) } }
        item {
            Caption(
                stringResource(R.string.profile_version_native, BuildConfig.VERSION_NAME),
                Modifier.padding(top = 16.dp),
            )
        }
    }
    when (page) {
        "auth" ->
            FullDialog(stringResource(R.string.sign_in), { page = null }) {
                Box(Modifier.height(740.dp)) {
                    Authentication(
                        s,
                        vm,
                        {
                            vm.run { s.secure.preference("onboarded", "true") }
                            page = null
                        },
                        { page = "Synchronisation" },
                    )
                }
            }
        "Portefeuilles" ->
            FullDialog(stringResource(R.string.ui_portefeuilles_98a99), { page = null }) {
                w.portfolios.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable {
                                editing = p
                                portfolioForm = true
                            }
                        ) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Caption("${p.type} · ${p.currency}")
                        }
                        IconButton(
                            onClick = {
                                editing = p
                                portfolioForm = true
                            }
                        ) {
                            Icon(Icons.Outlined.Edit, "Modifier")
                        }
                        IconButton(onClick = { deletePortfolio = p.id }) {
                            Icon(Icons.Outlined.DeleteOutline, "Supprimer")
                        }
                    }
                }
                Button(
                    onClick = {
                        editing = null
                        portfolioForm = true
                    }
                ) {
                    Text(stringResource(R.string.ui_nouveau_portefeuille_24abc))
                }
            }
        "Source des données" -> SourceSettings(s, vm) { page = null }
        "Synchronisation" -> SyncSettings(w, vm) { page = null }
        "Appareils" -> DeviceSettings(vm) { page = null }
        "Sécurité" ->
            FullDialog(stringResource(R.string.ui_securite_33e96), { page = null }) {
                var locked by remember { mutableStateOf(s.secure.get("biometric") == "true") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ui_verrouiller_avec_biometrie_5657b), Modifier.weight(1f))
                    Switch(
                        locked,
                        { desired ->
                            biometric {
                                locked = desired
                                s.secure.put("biometric", desired.toString())
                                WalletWidget.updateAll(context)
                            }
                        },
                    )
                }
                Caption(
                    stringResource(R.string.ui_empreinte_visage_compatible_ou_verrouillage_s_366bc)
                )
                Caption(
                    stringResource(R.string.ui_les_tokens_et_cles_api_sont_chiffres_par_andr_51e9c)
                )
            }
        "Notifications" ->
            FullDialog(stringResource(R.string.ui_notifications_753a2), { page = null }) {
                Notifications.categories.forEach { (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Switch(
                            w.settings["notify_$id"] != "false",
                            { enabled ->
                                vm.run { s.repo.setting("notify_$id", enabled.toString()) }
                                if (enabled) askPermission()
                            },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        askPermission()
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(
                                    android.provider.Settings.EXTRA_APP_PACKAGE,
                                    context.packageName,
                                )
                        )
                    }
                ) {
                    Text(stringResource(R.string.ui_reglages_android_cb6c7))
                }
                Caption(
                    stringResource(R.string.ui_actifs_alertes_prix_earnings_finnhub_et_actua_68ec5)
                )
                Section(stringResource(R.string.ui_titres_suivis_59829))
                w.notifications.forEach { pref ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.security(pref.securityId)?.ticker.orEmpty())
                            Caption(stringResource(R.string.profile_earnings_news, pref.earnings.toString(), pref.news.toString()))
                        }
                        IconButton(onClick = { vm.run { s.repo.remove(pref.id) } }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.profile_disable))
                        }
                    }
                }
                Section(stringResource(R.string.ui_historique_34f3a))
                w.events
                    .sortedByDescending { it.timestamp }
                    .take(100)
                    .forEach { e ->
                        Text(e.title)
                        Caption("${time(e.timestamp)} · ${e.body}")
                        HorizontalDivider(color = Border)
                    }
            }
        "Widgets" ->
            FullDialog(stringResource(R.string.ui_widgets_ecran_daccueil_c79f0), { page = null }) {
                Caption(
                    stringResource(R.string.ui_maintiens_un_espace_vide_de_lecran_daccueil_s_93dbb)
                )
                WalletWidget.ids(context).forEachIndexed { index, id ->
                    val config = WidgetSettings.load(context, id)
                    Section(stringResource(R.string.profile_widget_number, index + 1))
                    Text(w.portfolios.find { it.id == config.portfolio }?.name
                        ?: stringResource(R.string.all_portfolios))
                    Caption(config.style + " · " + if (config.custom)
                        config.titleIds.mapNotNull { w.security(it)?.ticker }.joinToString(" · ")
                        else stringResource(R.string.widget_automatic))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(context, WidgetConfiguration::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    }) { Text(stringResource(R.string.widget_edit)) }
                }
                listOf(
                        "2 × 2" to Widget2x2::class.java,
                        "2 × 3" to Widget2x3::class.java,
                        "4 × 2" to Widget4x2::class.java,
                        "4 × 3" to Widget4x3::class.java,
                    )
                    .forEach { (name, clazz) ->
                        OutlinedButton(
                            onClick = {
                                val manager = AppWidgetManager.getInstance(context)
                                if (manager.isRequestPinAppWidgetSupported)
                                    manager.requestPinAppWidget(
                                        ComponentName(context, clazz),
                                        null,
                                        null,
                                    )
                                else
                                    vm.run {
                                        error(
                                            context.getString(R.string.profile_widget_instruction)
                                        )
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.profile_add_widget, name))
                        }
                    }
                Caption(
                    stringResource(R.string.ui_les_widgets_montrent_le_p_l_du_jour_ils_naffi_2b8aa)
                )
            }
        "Export et sauvegarde" ->
            FullDialog(stringResource(R.string.ui_export_et_sauvegarde_a20c0), { page = null }) {
                Caption(
                    stringResource(R.string.ui_les_exports_contiennent_des_donnees_financier_8a653)
                )
                Button(
                    onClick = {
                        vm.run {
                            exportContent = s.repo.exportJson()
                            create.launch("MonWallet-backup-${java.time.LocalDate.now()}.json")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ui_exporter_json_complet_b4913))
                }
                OutlinedButton(
                    onClick = {
                        exportContent = Csv.export(w)
                        create.launch("MonWallet-transactions.csv")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ui_exporter_les_transactions_csv_fbcbc))
                }
                OutlinedButton(
                    onClick = {
                        importType = "json"
                        open.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ui_restaurer_json_0c67b))
                }
                OutlinedButton(
                    onClick = {
                        importType = "csv"
                        open.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ui_importer_csv_5de8f))
                }
                Caption(
                    stringResource(R.string.ui_csv_date_ticker_quantity_price_currency_fees__3e049)
                )
            }
        "Paramètres" ->
            FullDialog(stringResource(R.string.profile_settings), { page = null }) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.settings_language))
                    LanguageChoice()
                }
                HorizontalDivider(color = Border)
                Metric(stringResource(R.string.settings_currency), "CAD")
                Caption(stringResource(R.string.settings_fx_caption))
                Metric(stringResource(R.string.settings_background_refresh), stringResource(R.string.settings_refresh_interval))
                Caption(stringResource(R.string.settings_refresh_caption))
                Text("Valorisation hors séance", fontWeight = FontWeight.SemiBold)
                val extendedValuation = w.settings["portfolio_extended"] == "LAST"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!extendedValuation, onClick = {
                        vm.run { s.repo.setting("portfolio_extended", "REGULAR") }
                    }, label = { Text("Cours régulier") })
                    FilterChip(extendedValuation, onClick = {
                        vm.run { s.repo.setting("portfolio_extended", "LAST") }
                    }, label = { Text("Dernier disponible") })
                }
                Caption("Les estimations hors séance utilisent seulement les cours PRE/AFTER effectivement fournis.")
                Button(onClick = { vm.refresh() }) { Text(stringResource(R.string.settings_refresh_now)) }
                var hidden by remember { mutableStateOf(s.secure.get("hide_widgets") != "false") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_hide_widgets), Modifier.weight(1f))
                    Switch(
                        hidden,
                        {
                            hidden = it
                            s.secure.put("hide_widgets", it.toString())
                            WalletWidget.updateAll(context)
                        },
                    )
                }
            }
        "Mises à jour" -> UpdateSettings(vm) { page = null }
        "Compte" ->
            FullDialog(stringResource(R.string.ui_compte_c4574), { page = null }) {
                if (user == null) {
                    Caption(stringResource(R.string.ui_tu_utilises_le_mode_invite_55a4d))
                    Button(onClick = { page = "auth" }) { Text(stringResource(R.string.ui_creer_un_compte_dd9f9)) }
                } else {
                    Caption(email.orEmpty())
                    Button(onClick = { page = "Export et sauvegarde" }) {
                        Text(stringResource(R.string.ui_exporter_mes_donnees_7ed18))
                    }
                    var confirm by remember { mutableStateOf("") }
                    Text(stringResource(R.string.ui_supprimer_mon_compte_e894a), color = Red, fontWeight = FontWeight.Bold)
                    Caption(
                        stringResource(R.string.ui_suppression_definitive_du_compte_et_des_donne_7cf13)
                    )
                    TextEntry(stringResource(R.string.ui_saisir_supprimer_1bbf1), confirm, { confirm = it })
                    Button(
                        enabled = confirm == "SUPPRIMER",
                        onClick = {
                            vm.run("Compte supprimé") {
                                s.auth.deleteAccount()
                                page = null
                            }
                        },
                    ) {
                        Text(stringResource(R.string.ui_supprimer_definitivement_66e03))
                    }
                }
            }
        "À propos" ->
            FullDialog(stringResource(R.string.ui_mon_wallet_be307), { page = null }) {
                Text(stringResource(R.string.profile_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Caption(
                    stringResource(R.string.ui_suivi_prive_des_investissements_sans_connexio_714ab)
                )
                Caption(
                    stringResource(R.string.ui_les_fractionnements_et_fusions_ne_sont_pas_au_c6cf9)
                )
            }
    }
    if (portfolioForm)
        PortfolioDialog(editing, { portfolioForm = false }) { p ->
            vm.run("Portefeuille enregistré") { s.repo.put("portfolio", p.id, p) }
        }
    deletePortfolio?.let { id ->
        Confirm(
            stringResource(R.string.ui_supprimer_le_portefeuille_c1351),
            stringResource(R.string.profile_portfolio_delete_only_empty),
            { deletePortfolio = null },
        ) {
            vm.run { s.repo.remove(id) }
        }
    }
    if (signout)
        Confirm(
            stringResource(R.string.ui_se_deconnecter_4ecfd),
            stringResource(R.string.profile_signout_local),
            { signout = false },
        ) {
            vm.run { s.auth.signOut(context) }
        }
    imported?.let { (raw, count) ->
        Confirm(
            stringResource(R.string.ui_restaurer_la_sauvegarde_d29ea),
            stringResource(R.string.profile_restore_count, count),
            { imported = null },
        ) {
            vm.run("Sauvegarde restaurée") { s.repo.restore(raw) }
        }
    }
    csvPreview?.let { preview ->
        Confirm(
            stringResource(R.string.profile_import_count, preview.count),
            stringResource(R.string.profile_import_validated),
            { csvPreview = null },
        ) {
            vm.run("Import effectué") { Csv.import(preview, s.repo) }
        }
    }
}

@Composable
fun SourceSettings(s: Services, vm: WalletViewModel, onClose: () -> Unit) {
    FullDialog(stringResource(R.string.ui_source_des_donnees_e07e6), onClose) {
        Caption(stringResource(R.string.market_source_status))
        Button(onClick = { vm.refresh(); onClose() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_refresh))
        }
    }
}

@Composable
fun SyncSettings(w: Wallet, vm: WalletViewModel, onClose: () -> Unit) {
    val s = vm.services
    val status by s.sync.status.collectAsState()
    var conflicts by remember { mutableStateOf<List<Record>>(emptyList()) }
    LaunchedEffect(w.pending) { conflicts = s.sync.conflicts() }
    FullDialog(stringResource(R.string.ui_synchronisation_3f2dc), onClose) {
        Text(status, color = Blue)
        Caption(stringResource(R.string.profile_pending_count, w.pending))
        if (s.auth.user.value == null) {
            Caption(stringResource(R.string.cloud_guest_status))
            if (!s.secure.configured) Caption(stringResource(R.string.cloud_unavailable))
        } else Button(onClick = { vm.run { s.sync.sync() } }) { Text(stringResource(R.string.ui_synchroniser_maintenant_11f81)) }
        if (conflicts.isNotEmpty()) {
            Section(stringResource(R.string.ui_conflits_a_resoudre_393b8))
            conflicts.forEach { r ->
                CardBlock {
                    Text("${r.kind} · ${r.id.take(8)}")
                    Caption(
                        stringResource(R.string.ui_cet_objet_a_ete_modifie_sur_plusieurs_apparei_90089)
                    )
                    Caption("Local : ${r.payload.take(280)}")
                    Caption(
                        "Cloud : ${runCatching { JSONObject(r.conflict!!).getJSONObject("payload").toString().take(280) }.getOrDefault("—")}"
                    )
                    Row {
                        TextButton(
                            onClick = {
                                vm.run {
                                    s.sync.resolve(r.id, true)
                                    conflicts = s.sync.conflicts()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ui_garder_local_7648c))
                        }
                        TextButton(
                            onClick = {
                                vm.run {
                                    s.sync.resolve(r.id, false)
                                    conflicts = s.sync.conflicts()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.ui_garder_cloud_48dfd))
                        }
                    }
                }
            }
        }
        Caption(
            stringResource(R.string.ui_uuid_stables_versions_serveur_suppressions_co_d3df9)
        )
    }
}

@Composable
fun DeviceSettings(vm: WalletViewModel, onClose: () -> Unit) {
    val s = vm.services
    var devices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var revoke by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            devices = s.sync.devices()
        } catch (e: Exception) {
            error = e.message
        }
    }
    FullDialog(stringResource(R.string.ui_appareils_connectes_4ecaf), onClose) {
        error?.let { Caption(it) }
        devices.forEach { d ->
            CardBlock {
                Text(d.optString("name"))
                Caption(stringResource(R.string.profile_last_sync, time(d.optLong("last_seen"))))
                Caption(stringResource(if (d.optBoolean("revoked")) R.string.profile_session_revoked else R.string.profile_session_active))
                if (!d.optBoolean("revoked"))
                    TextButton(onClick = { revoke = d.getString("session_id") }) {
                        Text(stringResource(R.string.ui_deconnecter_cet_appareil_74330))
                    }
            }
        }
        if (devices.isEmpty() && error == null) Caption(stringResource(R.string.ui_chargement_01cba))
    }
    revoke?.let { id ->
        Confirm(
            stringResource(R.string.ui_deconnecter_cet_appareil_eeb9f),
            stringResource(R.string.profile_revoke_caption),
            { revoke = null },
        ) {
            vm.run {
                s.sync.revoke(id)
                devices = s.sync.devices()
            }
        }
    }
}

@Composable
fun UpdateSettings(vm: WalletViewModel, onClose: () -> Unit) {
    val s = vm.services
    val context = LocalContext.current
    var url by remember { mutableStateOf(s.secure.updateUrl) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    var status by remember { mutableStateOf(context.getString(R.string.profile_installed_version, BuildConfig.VERSION_NAME)) }
    var busy by remember { mutableStateOf(false) }
    FullDialog(stringResource(R.string.ui_mises_a_jour_8e32a), onClose) {
        Text(status)
        TextEntry(stringResource(R.string.ui_adresse_https_de_version_json_4a892), url, { url = it })
        Caption(
            stringResource(R.string.ui_cette_adresse_est_fournie_par_le_depot_de_dis_aba3c)
        )
        Button(
            enabled = !busy,
            onClick = {
                vm.run {
                    s.secure.put("update_url", url.trim())
                    busy = true
                    try {
                        release = s.updater.check()
                        status =
                            if (release == null) context.getString(R.string.profile_latest_version)
                            else context.getString(R.string.profile_new_version)
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            Text(stringResource(R.string.ui_verifier_les_mises_a_jour_7a411))
        }
        release?.let { r ->
            Section(stringResource(R.string.profile_release_name, r.name))
            Text(r.notes)
            if (r.mandatory || BuildConfig.VERSION_CODE < r.min)
                Text(stringResource(R.string.ui_mise_a_jour_requise_par_lediteur_dc126), color = Red)
            Button(
                enabled = !busy,
                onClick = {
                    vm.run {
                        busy = true
                        try {
                            val file = apk ?: s.updater.download(r).also { apk = it }
                            s.updater.install(file)
                            status =
                                context.getString(R.string.profile_install_again)
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (apk == null) R.string.profile_download_update else R.string.profile_install))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Caption(
            stringResource(R.string.ui_controles_sha_256_identifiant_version_et_cert_97a32)
        )
    }
}
