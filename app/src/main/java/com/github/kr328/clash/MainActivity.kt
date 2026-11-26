package com.github.kr328.clash

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.github.kr328.clash.remote.Remote
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.compat.versionCodeCompat
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.presentation.AppViewModel
import com.github.kr328.clash.presentation.screens.*
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.settings.RuntimePhase
import com.github.kr328.clash.service.settings.UiPreferences
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.AppUpdate
import com.github.kr328.clash.design.settings.UpdateChannel
import com.github.kr328.clash.design.compose.GlonetXTheme
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode

class MainActivity : ComponentActivity() {
    internal var pendingRoute by mutableStateOf<String?>(null)
        private set

    companion object { const val EXTRA_NAV_ROUTE = Intents.EXTRA_NAV_ROUTE }

    private fun acceptNavigation(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_NAV_ROUTE) ?: return
        val validProfile = route.startsWith("edit/") && runCatching {
            java.util.UUID.fromString(route.removePrefix("edit/"))
        }.isSuccess
        if (validProfile || route == "log/live") pendingRoute = route
        intent.removeExtra(EXTRA_NAV_ROUTE)
    }

    internal fun consumeNavigation() { pendingRoute = null }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptNavigation(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptNavigation(intent)
        setContent { ApplicationScreen(this) }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) { Remote.service.remote.get().settings().getControlToken() }
            setupShortcuts(token)
        }
    }
    internal fun setupShortcuts(token: String) {
        val aliasState = packageManager.getComponentEnabledSetting(
            ComponentName(this, "com.github.kr328.clash.MainActivityAlias"),
        )
        if (aliasState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
            aliasState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
            return
        }

        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION
        val shortcuts = listOf(
            Triple("toggle_clash", Intents.ACTION_TOGGLE_CLASH, R.drawable.ic_toggle_all),
            Triple("start_clash", Intents.ACTION_START_CLASH, R.drawable.ic_toggle_on),
            Triple("stop_clash", Intents.ACTION_STOP_CLASH, R.drawable.ic_toggle_off),
        ).mapIndexed { rank, (id, action, icon) ->
            ShortcutInfoCompat.Builder(this, id).setShortLabel(id.substringBefore('_').replaceFirstChar { it.uppercase() })
                .setIcon(IconCompat.createWithResource(this, icon))
                .setIntent(Intent(action).setClass(this, ExternalControlActivity::class.java)
                    .putExtra(ExternalControlActivity.EXTRA_CONTROL_TOKEN, token).addFlags(flags))
                .setRank(rank).build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)
    }
}

@Composable private fun TopBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.height(40.dp),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    innerTextField()
                }
            }
        },
    )
}

@Composable private fun ProxyOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    prefs: UiPreferences,
    onPrefs: ((UiPreferences) -> UiPreferences) -> Unit,
) {
    var page by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(expanded) {
        if (!expanded) page = null
    }
    DropdownMenu(expanded, onDismiss) {
        if (page == null) {
            DropdownMenuItem(
                text = { Text(text("View", "视图")) },
                trailingIcon = { Text(if (prefs.proxyViewMode == "Grid") text("Cards", "卡片") else text("List", "列表")) },
                onClick = { page = "view" },
            )
            if (prefs.proxyViewMode == "Grid") {
                DropdownMenuItem(
                    text = { Text(text("Columns", "列数")) },
                    trailingIcon = { Text(prefs.proxyLine.toString()) },
                    onClick = { page = "columns" },
                )
            }
            DropdownMenuItem(
                text = { Text(text("Sort", "排序")) },
                trailingIcon = { Text(prefs.proxySort) },
                onClick = { page = "sort" },
            )
            if (prefs.proxyViewMode == "Grid") {
                DropdownMenuItem(
                    text = { Text(text("Card density", "卡片密度")) },
                    trailingIcon = { Text(prefs.proxyDensity) },
                    onClick = { page = "density" },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(text(if (prefs.proxyIconStyle == "Flag") "Hide flags" else "Show flags", if (prefs.proxyIconStyle == "Flag") "隐藏旗帜" else "显示旗帜")) },
                onClick = { onPrefs { it.copy(proxyIconStyle = if (it.proxyIconStyle == "Flag") "None" else "Flag") }; onDismiss() },
            )
            DropdownMenuItem(
                text = { Text(text("Selectable groups only", "仅显示可选择分组")) },
                trailingIcon = { Checkbox(checked = prefs.proxyExcludeNotSelectable, onCheckedChange = null) },
                onClick = { onPrefs { it.copy(proxyExcludeNotSelectable = !it.proxyExcludeNotSelectable) }; onDismiss() },
            )
        } else {
            DropdownMenuItem(
                text = { Text(text("Back", "返回")) },
                leadingIcon = { Icon(Icons.Default.ChevronLeft, null) },
                onClick = { page = null },
            )
            HorizontalDivider()
            when (page) {
                "view" -> {
                    DropdownMenuItem(
                        text = { Text(text("Cards", "卡片")) },
                        trailingIcon = { RadioButton(selected = prefs.proxyViewMode == "Grid", onClick = null) },
                        onClick = { onPrefs { it.copy(proxyViewMode = "Grid") }; onDismiss() },
                    )
                    DropdownMenuItem(
                        text = { Text(text("List", "列表")) },
                        trailingIcon = { RadioButton(selected = prefs.proxyViewMode == "List", onClick = null) },
                        onClick = { onPrefs { it.copy(proxyViewMode = "List") }; onDismiss() },
                    )
                }
                "columns" -> (1..4).forEach { columns ->
                    DropdownMenuItem(
                        text = { Text(columns.toString()) },
                        trailingIcon = { RadioButton(selected = prefs.proxyLine == columns, onClick = null) },
                        onClick = { onPrefs { it.copy(proxyLine = columns) }; onDismiss() },
                    )
                }
                "sort" -> ProxySort.values().forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.name) },
                        trailingIcon = { RadioButton(selected = prefs.proxySort == sort.name, onClick = null) },
                        onClick = { onPrefs { it.copy(proxySort = sort.name) }; onDismiss() },
                    )
                }
                "density" -> listOf("Tight", "Standard", "Loose").forEach { density ->
                    DropdownMenuItem(
                        text = { Text(density) },
                        trailingIcon = { RadioButton(selected = prefs.proxyDensity == density, onClick = null) },
                        onClick = { onPrefs { it.copy(proxyDensity = density) }; onDismiss() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ApplicationScreen(activity: MainActivity, model: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    LaunchedEffect(activity.pendingRoute) {
        activity.pendingRoute?.let { route ->
            nav.navigate(route)
            activity.consumeNavigation()
        }
    }
    val snapshot by model.settings.collectAsStateWithLifecycle()
    val home by model.home.collectAsStateWithLifecycle()
    val prefs = snapshot?.ui ?: com.github.kr328.clash.service.settings.UiPreferences()
    var homeHeaderVisible by remember { mutableStateOf(true) }
    LaunchedEffect(snapshot?.ui?.hideFromRecents) {
        snapshot?.ui?.let { ui -> runCatching {
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).appTasks.forEach { it.setExcludeFromRecents(ui.hideFromRecents) }
        }.onFailure { model.message(it.message ?: "Unable to change recent tasks") } }
    }
    LaunchedEffect(snapshot?.ui?.hideAppIcon) {
        snapshot?.ui?.let { ui -> runCatching {
            activity.packageManager.setComponentEnabledSetting(
                ComponentName(activity.packageName, "com.github.kr328.clash.MainActivityAlias"),
                if (ui.hideAppIcon) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            if (ui.hideAppIcon) {
                ShortcutManagerCompat.removeAllDynamicShortcuts(activity)
            } else {
                activity.lifecycleScope.launch {
                    val token = withContext(Dispatchers.IO) {
                        Remote.service.remote.get().settings().getControlToken()
                    }
                    activity.setupShortcuts(token)
                }
            }
        }.onFailure { model.message(it.message ?: "Unable to change the launcher icon") } }
    }
    val snackbar = remember { SnackbarHostState() }
    var proxyQuery by rememberSaveable { mutableStateOf("") }
    var profileQuery by rememberSaveable { mutableStateOf("") }
    var accessQuery by rememberSaveable { mutableStateOf("") }
    var logQuery by rememberSaveable { mutableStateOf("") }
    var connectionQuery by rememberSaveable { mutableStateOf("") }
    var proxyMenu by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<AppUpdate.Release?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    fun checkUpdate(notify: Boolean) {
        model.launchAction {
            updateBusy = true
            try {
                val channel = if (prefs.appUpdateChannel == "Prerelease") UpdateChannel.Prerelease else UpdateChannel.Stable
                val result = AppUpdate.checkUpdate(activity, channel)
                when (result) {
                    is AppUpdate.CheckResult.Found -> release = result.release
                    AppUpdate.CheckResult.None -> {
                        release = null
                        if (notify) model.message("No update available")
                    }
                    is AppUpdate.CheckResult.Failed -> {
                        val detail = result.error.message?.takeIf { it.isNotBlank() }
                            ?: result.error.javaClass.simpleName
                        model.message("Unable to check for updates: $detail")
                    }
                }
                if (!notify && result !is AppUpdate.CheckResult.Failed) AppUpdate.markChecked(activity)
            } finally { updateBusy = false }
        }
    }
    LaunchedEffect(snapshot?.ui?.appUpdateAutoCheck) {
        if (snapshot?.ui?.appUpdateAutoCheck == true && AppUpdate.isAutoCheckDue(activity)) {
            checkUpdate(false)
        }
    }
    release?.let { candidate ->
        AlertDialog(onDismissRequest = { release = null }, title = { Text("Update ${candidate.versionName}") },
            text = { Text(candidate.notes.take(800)) }, confirmButton = {
                TextButton(enabled = !updateBusy, onClick = {
                    release = null
                    model.launchAction {
                        val apk = AppUpdate.downloadApk(activity, candidate) { }
                        if (apk == null || !AppUpdate.installApk(activity, apk)) model.message("Unable to install update")
                    }
                }) { Text(text("Download", "下载")) }
            }, dismissButton = { TextButton(onClick = { release = null }) { Text(text("Cancel", "取消")) } })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.setVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.setVisible(false) }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notification.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var pendingLocationPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val callback = pendingLocationPermission
        pendingLocationPermission = null
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            callback?.invoke()
        } else {
            model.message("Location permission is required to identify Wi-Fi networks")
        }
    }
    fun requestLocationPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
            return
        }
        pendingLocationPermission = onGranted
        runCatching {
            location.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }.onFailure {
            pendingLocationPermission = null
            model.message(it.message ?: "Unable to request location permission")
        }
    }
    var pendingVpnConsent by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingVpnConsent?.complete(result.resultCode == Activity.RESULT_OK)
    }
    val scan = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        if (result is QRResult.QRSuccess) {
            val url = result.content.rawValue
            if (!url.isNullOrBlank()) model.launchAction {
                val id = model.repository.create(Profile.Type.Url, "Subscription", url)
                nav.navigate("edit/$id")
            }
        } else if (result is QRResult.QRError) model.message(result.exception.message ?: "Unable to scan QR code")
    }
    @SuppressLint("WrongConstant")
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val takeFlags = result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ?: 0
            val persisted = takeFlags != 0 && runCatching {
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
            }.isSuccess
            if (persisted) model.launchAction {
                val id = model.repository.create(Profile.Type.External, result.data?.getStringExtra(Intents.EXTRA_NAME) ?: "Subscription", uri.toString())
                nav.navigate("edit/$id")
            } else model.message("Unable to persist profile URI permission")
        }
    }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.launchAction {
            val id = model.repository.create(Profile.Type.File, "Local configuration")
            model.repository.replaceFile("$id/config.yaml", uri)
            nav.navigate("edit/$id")
        }
    }
    suspend fun startClashWithConsent(): Boolean {
        val request = try {
            activity.startClashService()
        } catch (e: Exception) {
            model.message(e.message ?: "Unable to start VPN")
            return false
        }
        if (request == null) return true

        val consent = CompletableDeferred<Boolean>()
        pendingVpnConsent = consent
        val launched = runCatching { vpn.launch(request) }
        if (launched.isFailure) {
            pendingVpnConsent = null
            model.message(launched.exceptionOrNull()?.message ?: "Unable to request VPN permission")
            return false
        }

        return try {
            when (val granted = withTimeoutOrNull(30_000) { consent.await() }) {
                null -> {
                    model.message("Timed out waiting for VPN permission")
                    false
                }
                false -> {
                    model.message("VPN permission denied")
                    false
                }
                true -> try {
                    if (activity.startClashService() == null) true
                    else {
                        model.message("VPN permission is still required")
                        false
                    }
                } catch (e: Exception) {
                    model.message(e.message ?: "Unable to start VPN")
                    false
                }
            }
        } finally {
            pendingVpnConsent = null
        }
    }
    fun toggle() {
        val current = model.home.value
        if (current.connected) activity.stopClashService()
        else if (current.active?.imported != true) model.message("Select a valid subscription first")
        else model.launchAction {
            if (startClashWithConsent()) requestNotification()
        }
    }
    var reconnecting by remember { mutableStateOf(false) }
    fun reconnect() {
        if (reconnecting) return
        reconnecting = true
        model.launchAction {
            try {
                val active = withContext(Dispatchers.IO) { model.repository.activeProfile() }
                if (active?.imported != true) {
                    model.message("Select a valid subscription first")
                    return@launchAction
                }
                val beforeStop = model.settings.value
                if (beforeStop == null) {
                    model.message("Settings are not available yet")
                    return@launchAction
                }
                if (beforeStop.phase != RuntimePhase.Stopped && beforeStop.phase != RuntimePhase.Failed) {
                    activity.stopClashService()
                    val stopped = withTimeoutOrNull(15_000) {
                        model.settings.first { it?.phase == RuntimePhase.Stopped || it?.phase == RuntimePhase.Failed }
                    }
                    if (stopped == null) {
                        model.message("Timed out waiting for VPN to stop")
                        return@launchAction
                    }
                }

                val previousGeneration = model.settings.value?.runtimeGeneration ?: beforeStop.runtimeGeneration
                if (!startClashWithConsent()) return@launchAction
                requestNotification()

                val started = withTimeoutOrNull(30_000) {
                    model.settings.first { snapshot ->
                        snapshot != null && snapshot.runtimeGeneration != previousGeneration &&
                            (snapshot.phase == RuntimePhase.Running || snapshot.phase == RuntimePhase.Failed)
                    }
                }
                if (started == null) {
                    model.message("Timed out waiting for VPN to start")
                } else if (started.phase == RuntimePhase.Failed) {
                    model.message("Unable to start VPN: ${started.lastError ?: "service failed"}")
                } else {
                    model.message("Reconnected to apply settings")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                model.message(e.message ?: "Unable to reconnect VPN")
            } finally {
                reconnecting = false
            }
        }
    }
    fun navigate(route: String) {
        if (route == "reconnect") reconnect() else nav.navigate(route)
    }
    fun openLink(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { model.message("No browser available") }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    LaunchedEffect(route) { model.setHomeVisible(route == "home") }
    val root = route in listOf("home", "proxy", "settings")
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val systemConfiguration = LocalConfiguration.current
    val currentContext = LocalContext.current
    val density = LocalDensity.current
    val localizedConfiguration = remember(systemConfiguration, prefs.appLanguage) {
        Configuration(systemConfiguration).apply {
            if (prefs.appLanguage.isNotBlank()) setLocale(java.util.Locale.forLanguageTag(prefs.appLanguage))
        }
    }
    val localizedContext = remember(currentContext, localizedConfiguration) { currentContext.createConfigurationContext(localizedConfiguration) }
    fun selectDestination(target: String) { nav.navigate(target) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
    CompositionLocalProvider(
        LocalActivityResultRegistryOwner provides activity,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
        LocalDensity provides Density(density.density, density.fontScale * prefs.textScale / 100f),
    ) { GlonetXTheme(mode = prefs.darkMode, dynamic = prefs.dynamicColor) {
        val destinations = listOf(Triple("home", Icons.Default.Home, text("Home", "首页")), Triple("proxy", Icons.Default.Public, text("Proxies", "代理")), Triple("settings", Icons.Default.Settings, text("Settings", "设置")))
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
            TopAppBar(title = {
                when (route.substringBefore('/')) {
                    "proxy" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text("Proxies", "代理"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        TopBarSearchField(proxyQuery, { proxyQuery = it }, text("Search nodes", "搜索节点"), Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { proxyMenu = true }) {
                                Icon(Icons.Default.Tune, text("Display options", "显示选项"))
                            }
                            ProxyOptionsMenu(proxyMenu, { proxyMenu = false }, prefs, model::updateUi)
                        }
                    }
                    "profiles" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Profiles", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        TopBarSearchField(profileQuery, { profileQuery = it }, text("Search subscriptions", "搜索订阅"), Modifier.weight(1f))
                        IconButton(onClick = { nav.navigate("new-profile") }) {
                            Icon(Icons.Default.Add, text("Add", "添加"))
                        }
                    }
                    "access" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text("Access control", "访问控制"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        TopBarSearchField(accessQuery, { accessQuery = it }, text("Search apps", "搜索应用"), Modifier.weight(1f))
                    }
                    "log" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text("Logs", "日志"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        TopBarSearchField(logQuery, { logQuery = it }, text("Search messages", "搜索日志"), Modifier.weight(1f))
                    }
                    "connections" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text("Connections", "活动连接"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(12.dp))
                        TopBarSearchField(connectionQuery, { connectionQuery = it }, text("Host, process, rule…", "主机、进程、规则…"), Modifier.weight(1f))
                    }
                    "home" -> Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("GlonetX", style = MaterialTheme.typography.titleMedium)
                        if (homeHeaderVisible) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Icon(Icons.Default.Download, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${bytes(home.download)}/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Icon(Icons.Default.Upload, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        "${bytes(home.upload)}/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Icon(Icons.Default.Download, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "${bytes(home.download)}/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Icon(Icons.Default.Upload, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                                    Text(
                                        "${bytes(home.upload)}/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            val nextMode = when (home.mode) {
                                TunnelState.Mode.Rule -> TunnelState.Mode.Global
                                TunnelState.Mode.Global -> TunnelState.Mode.Direct
                                else -> TunnelState.Mode.Rule
                            }
                            TextButton(
                                onClick = { model.setMode(nextMode) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(when (home.mode) {
                                    TunnelState.Mode.Rule -> text("Rule", "规则")
                                    TunnelState.Mode.Global -> text("Global", "全局")
                                    else -> text("Direct", "直连")
                                }, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { toggle() }) {
                                Icon(
                                    if (home.connected) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (home.connected) text("Pause", "暂停") else text("Start", "开始"),
                                )
                            }
                        }
                    }
                    "settings" -> Text(text("Settings", "设置"))
                    "override" -> Text(text("Configuration overrides", "配置覆盖"))
                    "meta-feature" -> Text(text("Kernel features", "内核特性"))
                    "tailscale" -> Text("Tailscale")
                    "quality" -> Text(text("IP Quality", "IP 质量"))
                    "latency" -> Text(text("Network tools", "网络工具"))
                    else -> Text(route.substringBefore('/').replace('-', ' ').replaceFirstChar { it.uppercase() })
                }
            }, navigationIcon = {
                if(!root) IconButton(onClick = { activity.onBackPressedDispatcher.onBackPressed() }) { Icon(Icons.Default.ArrowBack, text("Back", "返回")) }
            })
        }, bottomBar = { if(root && !wide) NavigationBar {
            destinations.forEach { (target, icon, label) ->
                NavigationBarItem(selected = route == target, onClick = { selectDestination(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
            }
        } }) { padding ->
            Row(Modifier.padding(padding)) {
            if (wide) NavigationRail {
                destinations.forEach { (target, icon, label) ->
                    NavigationRailItem(selected = route == target, onClick = { selectDestination(target) }, icon = { Icon(icon, null) }, label = { Text(label) })
                }
            }
            NavHost(
                nav,
                startDestination = "home",
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                composable("home") {
                    val home by model.home.collectAsStateWithLifecycle()
                    HomeScreen(home, prefs, snapshot, ::toggle, model::setMode, ::navigate, model::refreshIp, model::updateUi, onHeaderVisible = { homeHeaderVisible = it })
                }
                composable("proxy") { ProxyScreen(prefs, proxyQuery, model::updateUi) }
                listOf("settings", "app-settings", "network-settings").forEach { page -> composable(page) {
                    SettingsScreen(page, snapshot, model::updateRuntime, model::updateUi, ::navigate,
                        onCheckUpdate = { checkUpdate(true) },
                        onBatterySettings = { runCatching { activity.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.onFailure { model.message(it.message ?: "Unable to open battery settings") } },
                        onPermission = { onGranted -> requestLocationPermission(onGranted) })
                } }
                composable("profiles") { ProfilesScreen({ nav.navigate("edit/$it") }, { nav.navigate("new-profile") }, prefs, profileQuery, model::updateUi) }
                composable("new-profile") { NewProfileScreen({ nav.navigate("edit/$it") }, { scan.launch(null) }, { file.launch(arrayOf("*/*")) }, { runCatching { external.launch(Intent(Intents.ACTION_PROVIDE_URL)) }.onFailure { model.message(it.message ?: "No profile providers installed") } }) }
                composable("edit/{id}") { e -> ProfileEditorScreen(e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }, { nav.navigate("files/$it") }) }
                composable("files/{id}") { e -> FilesScreen(e.arguments?.getString("id").orEmpty(), { nav.popBackStack() }) }
                composable("providers") { ProvidersScreen() }
                composable("connections") { ConnectionsScreen(prefs, model::updateUi, connectionQuery, { connectionQuery = it }) }
                composable("quality") { QualityScreen() }
                composable("latency") { ToolsScreen(prefs, model::updateUi) }
                composable("backup") { BackupScreen() }
                composable("access") { snapshot?.let { AccessControlScreen(it, model::updateRuntime, model::updateUi, accessQuery, { accessQuery = it }) } }
                composable("override") { OverrideScreen(false, { nav.popBackStack() }) }
                composable("meta-feature") { KernelFeaturesScreen({ nav.popBackStack() }) }
                composable("tailscale") { TailscaleScreen(onBack = { nav.popBackStack() }, onOpenLogs = { nav.navigate("log/live") }) }
                composable("logs") { LogsScreen(onOpen = { name -> nav.navigate("log/${Uri.encode(name)}") }) }
                composable("log/{name}") { e -> LogViewerScreen(e.arguments?.getString("name").orEmpty(), { nav.popBackStack() }, logQuery, { logQuery = it }) }
                composable("help") { HelpScreen(onOpenUrl = { openLink(it) }) }
                composable("about") {
                    val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
                    AboutScreen(info.versionName.orEmpty(), info.versionCodeCompat, Bridge.nativeCoreVersion()) { openLink(it) }
                }
            }
            }
        }
    }
    }
}
