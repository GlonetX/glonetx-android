package com.github.kr328.clash.presentation.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.data.ClientRepository
import com.github.kr328.clash.data.LogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class TailscaleStatus { Unknown, NeedsLogin, Starting, Running, LoggedOut }

internal fun generateTailscaleHostname(proxyName: String, model: String): String {
    fun sanitize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(24)

    val suffix = sanitize(model).ifBlank { sanitize(proxyName) }.ifBlank { "node" }
    return "glonetx-$suffix".take(63)
}

class TailscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)

    val draft = MutableStateFlow<ConfigurationOverride.Tailscale?>(null)
    val loaded = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    val authUrl = MutableStateFlow<String?>(null)
    val starting = MutableStateFlow(false)
    val loginFailed = MutableStateFlow(false)
    val status = MutableStateFlow(TailscaleStatus.Unknown)
    val account = MutableStateFlow<String?>(null)

    private var original: ConfigurationOverride.Tailscale? = null
    private var logJob: Job? = null
    private var loginJob: Job? = null
    private val logReady = CompletableDeferred<Unit>()
    val dirty: Boolean get() = draft.value != original

    init {
        logJob = viewModelScope.launch {
            LogRepository(application).stream().collect { messages ->
                logReady.complete(Unit)
                messages.forEach { message ->
                    extractTailscaleAuthUrl(message.message)?.let { authUrl.value = it }
                    parseTailscaleStatus(message.message)?.let { next ->
                        status.value = next
                        when (next) {
                            TailscaleStatus.Running -> {
                                starting.value = false
                                loginFailed.value = false
                            }
                            TailscaleStatus.LoggedOut -> authUrl.value = null
                            else -> Unit
                        }
                    }
                    extractTailscaleAccount(message.message)?.let { account.value = it }
                }
            }
        }
        load()
    }

    fun load() = viewModelScope.launch {
        busy.value = true
        try {
            val current = repository.override().tailscale
            original = current
            draft.value = current?.takeIf { it.enabled && it.hostname.isNullOrBlank() }?.let { tailscale ->
                tailscale.copy(hostname = generateTailscaleHostname(tailscale.name, Build.MODEL))
            } ?: current
            loaded.value = true
            error.value = null
            if (current?.enabled == true && current.authKey.isNullOrBlank()) {
                requestLoginUrl()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            busy.value = false
        }
    }

    fun update(transform: (ConfigurationOverride.Tailscale) -> ConfigurationOverride.Tailscale) {
        val previous = draft.value
        val next = (previous ?: ConfigurationOverride.Tailscale()).let(transform)
        draft.value = next
        if (previous?.name != next.name || previous?.authKey != next.authKey || previous?.enabled != next.enabled) {
            authUrl.value = null
            loginFailed.value = false
            status.value = TailscaleStatus.Unknown
            account.value = null
            loginJob?.cancel()
            starting.value = false
        }
    }

    fun setEnabled(enabled: Boolean) = update { current ->
        current.copy(
            enabled = enabled,
            hostname = if (enabled && current.hostname.isNullOrBlank()) {
                generateTailscaleHostname(current.name, Build.MODEL)
            } else {
                current.hostname
            },
        )
    }

    fun requestLoginUrl() {
        if (starting.value || authUrl.value != null || status.value == TailscaleStatus.Running) return
        val name = draft.value?.name?.takeIf { it.isNotBlank() } ?: "tailscale"
        starting.value = true
        loginFailed.value = false
        loginJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(2_000) { logReady.await() }
                delay(500)
                repeat(20) { attempt ->
                    if (authUrl.value != null || status.value == TailscaleStatus.Running) return@launch
                    try {
                        repository.ip(name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // The proxy may not be loaded yet, or it may still be waiting for authorization.
                    }
                    if (authUrl.value != null || status.value == TailscaleStatus.Running) return@launch
                    delay(if (attempt < 3) 500 else 1_500)
                }
                loginFailed.value = authUrl.value == null && status.value != TailscaleStatus.Running
            } finally {
                starting.value = false
            }
        }
    }

    override fun onCleared() {
        loginJob?.cancel()
        logJob?.cancel()
        super.onCleared()
    }

    fun save() = viewModelScope.launch {
        busy.value = true
        try {
            val current = repository.override()
            val next = draft.value?.takeIf { it.enabled && it.hostname.isNullOrBlank() }?.let { tailscale ->
                tailscale.copy(hostname = generateTailscaleHostname(tailscale.name, Build.MODEL))
            } ?: draft.value
            draft.value = next
            repository.saveOverride(current.copy(tailscale = next))
            original = next
            saved.value = true
            error.value = null
            if (draft.value?.enabled == true && draft.value?.authKey.isNullOrBlank()) {
                requestLoginUrl()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            busy.value = false
        }
    }
}

internal fun extractTailscaleAuthUrl(message: String): String? {
    val url = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE).find(message)?.value ?: return null
    val isAuthMessage = message.contains("To start this tsnet server", ignoreCase = true) ||
        message.contains("auth url", ignoreCase = true)
    return url.takeIf {
        isAuthMessage || it.contains("login.tailscale.com", ignoreCase = true) || it.contains("/auth", ignoreCase = true)
    }
}

internal fun parseTailscaleStatus(message: String): TailscaleStatus? {
    if (!message.contains("[Tailscale]", ignoreCase = true)) return null
    return when {
        message.contains("Switching ipn state", ignoreCase = true) && message.contains("-> Running", ignoreCase = true) ->
            TailscaleStatus.Running
        message.contains("Switching ipn state", ignoreCase = true) && message.contains("-> Starting", ignoreCase = true) ->
            TailscaleStatus.Starting
        message.contains("Switching ipn state", ignoreCase = true) && message.contains("-> NeedsLogin", ignoreCase = true) ->
            TailscaleStatus.NeedsLogin
        message.contains("LocalBackend state is NeedsLogin", ignoreCase = true) ->
            TailscaleStatus.NeedsLogin
        message.contains("You are logged out", ignoreCase = true) ->
            TailscaleStatus.LoggedOut
        else -> null
    }
}

internal fun extractTailscaleAccount(message: String): String? {
    if (!message.contains("active login:", ignoreCase = true)) return null
    return message.substringAfter("active login:", "").trim().takeIf { it.isNotEmpty() }
}

@Composable
fun TailscaleScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    model: TailscaleViewModel = viewModel(),
) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val loaded by model.loaded.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val authUrl by model.authUrl.collectAsStateWithLifecycle()
    val starting by model.starting.collectAsStateWithLifecycle()
    val loginFailed by model.loginFailed.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    var loginMode by remember { mutableStateOf(if (draft?.authKey.isNullOrBlank()) "direct" else "key") }
    LaunchedEffect(draft?.authKey) { loginMode = if (draft?.authKey.isNullOrBlank()) "direct" else "key" }

    if (!loaded) {
        if (error != null) ErrorState(error!!) { model.load() } else BusyState()
        return
    }

    val value = draft ?: ConfigurationOverride.Tailscale(enabled = false)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard(
                title = "Tailscale",
                subtitle = text(
                    "Tailscale is added as a Mihomo outbound proxy. With the VPN running, saving an enabled direct-login node starts it automatically and shows the login URL here.",
                    "Tailscale 会作为 Mihomo 的出站节点写入配置覆盖。VPN 运行且启用直接登录并保存后，会自动启动节点，登录地址会显示在本页。",
                ),
                icon = Icons.Default.Hub,
            )
        }
        if (error != null) item { ErrorState(error!!) { model.load() } }
        when (status) {
            TailscaleStatus.Running -> item {
                InfoCard(
                    title = text("Tailscale connected", "Tailscale 已连接"),
                    subtitle = text(
                        "Node ${value.name} is ready. Tailscale traffic (100.64.0.0/10) is routed through it.",
                        "节点 ${value.name} 已就绪，Tailscale 网段（100.64.0.0/10）会自动通过它。",
                    ),
                    icon = Icons.Default.CheckCircle,
                )
            }
            TailscaleStatus.Starting -> item {
                InfoCard(
                    title = text("Starting Tailscale", "正在启动 Tailscale"),
                    subtitle = text(
                        "Connecting to the Tailscale network…",
                        "正在连接 Tailscale 网络…",
                    ),
                    icon = Icons.Default.NetworkCheck,
                )
            }
            TailscaleStatus.NeedsLogin -> item {
                InfoCard(
                    title = text("Authorization required", "等待授权"),
                    subtitle = text(
                        "Use the login URL below to authorize this node.",
                        "请使用下方登录链接完成节点授权。",
                    ),
                    icon = Icons.Default.Lock,
                )
            }
            TailscaleStatus.LoggedOut -> item {
                InfoCard(
                    title = text("Login expired", "登录已失效"),
                    subtitle = text(
                        "Get a new login URL to authorize this node again.",
                        "请重新获取登录链接并完成授权。",
                    ),
                    icon = Icons.Default.Info,
                )
            }
            TailscaleStatus.Unknown -> Unit
        }
        if (starting && authUrl == null && status == TailscaleStatus.Unknown) {
            item {
                InfoCard(
                    title = text("Starting Tailscale", "正在启动 Tailscale"),
                    subtitle = text(
                        "Contacting the control server. The login URL will appear here automatically.",
                        "正在连接控制服务器，登录链接会自动显示在这里。",
                    ),
                    icon = Icons.Default.NetworkCheck,
                )
            }
        }
        if (loginFailed && authUrl == null && status != TailscaleStatus.Running) {
            item {
                InfoCard(
                    title = text("Login URL not received", "未获取到登录链接"),
                    subtitle = text(
                        "Make sure the VPN is running and the configuration has been saved, then try again.",
                        "请确认 VPN 已启动且配置已保存，然后重试。",
                    ),
                    icon = Icons.Default.Info,
                )
            }
        }
        if (status != TailscaleStatus.Running) {
            authUrl?.let { url ->
                item {
                    InfoCard(
                        title = text("Login URL", "登录链接"),
                        subtitle = url,
                        icon = Icons.Default.Lock,
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = text("Copy", "复制"), modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                }) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = text("Open", "打开"), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                    )
                }
            }
        }

        item {
            SettingSection(text("Basic", "基本设置")) {
                SettingSwitch(text("Enable Tailscale", "启用 Tailscale"), value.enabled, icon = Icons.Default.VpnKey) {
                    model.setEnabled(it)
                }
                if (value.enabled) {
                    SettingChoice(
                        text("Login mode", "登录方式"),
                        loginMode,
                        listOf(
                            "direct" to text("Direct login (recommended)", "直接登录（推荐）"),
                            "key" to text("Auth key", "Auth Key"),
                        ),
                        icon = Icons.Default.Lock,
                    ) { loginMode = it }
                    SettingText(text("Proxy name", "节点名称"), value.name, icon = Icons.Default.Hub) {
                        model.update { current ->
                            val name = it.ifBlank { "tailscale" }
                            current.copy(
                                name = name,
                                hostname = current.hostname ?: generateTailscaleHostname(name, Build.MODEL),
                            )
                        }
                    }
                    SettingText(
                        text("Hostname", "主机名"),
                        value.hostname.orEmpty(),
                        icon = Icons.Default.Dns,
                        hint = text(
                            "Leave blank to generate glonetx-<device model>.",
                            "留空自动使用 glonetx-设备型号。",
                        ),
                    ) {
                        model.update { current -> current.copy(hostname = it.ifBlank { null }) }
                    }
                    if (loginMode == "key") {
                        SettingText(
                            text("Auth key", "登录密钥"),
                            value.authKey.orEmpty(),
                            icon = Icons.Default.Lock,
                            secure = true,
                            hint = text("Tailscale or Headscale auth key.", "填写 Tailscale 或 Headscale 的 Auth Key。"),
                        ) {
                            model.update { current -> current.copy(authKey = it.ifBlank { null }) }
                        }
                    } else {
                        SettingAction(
                            text("Get login URL", "获取登录链接"),
                            when {
                                status == TailscaleStatus.Running -> text("Tailscale is connected.", "Tailscale 已连接。")
                                authUrl != null -> text("Login URL is ready above.", "登录链接已显示在上方。")
                                starting -> text("Starting the node and requesting a login URL…", "正在启动节点并获取登录链接…")
                                loginFailed -> text("Tap to retry. Check that the VPN is running.", "点击重试，并确认 VPN 已启动。")
                                else -> text(
                                    "Start this node automatically; no manual proxy selection is required.",
                                    "自动启动该节点，无需先手动选择代理。",
                                )
                            },
                            icon = Icons.Default.HelpOutline,
                        ) { model.requestLoginUrl() }
                        SettingAction(
                            text("View logs", "查看日志"),
                            text("Inspect Tailscale startup and authorization messages.", "查看 Tailscale 启动与授权日志。"),
                            icon = Icons.Default.Info,
                        ) { onOpenLogs() }
                    }
                }
            }
        }

        if (value.enabled) {
            item {
                OutlinedButton(
                    onClick = { advanced = !advanced },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (advanced) text("Hide advanced", "收起高级设置") else text("Advanced", "高级设置")) }
            }

            if (advanced) {
                item {
                    SettingSection(text("Connection", "连接选项")) {
                        SettingText(
                            text("Control URL", "控制服务器"),
                            value.controlUrl.orEmpty(),
                            icon = Icons.Default.NetworkCheck,
                            hint = text("Tailscale control plane or a custom Headscale URL.", "Tailscale 控制面地址，也可填写自建 Headscale 地址。"),
                        ) {
                            model.update { current -> current.copy(controlUrl = it.ifBlank { null }) }
                        }
                        SettingText(
                            text("State directory", "状态目录"),
                            value.stateDir.orEmpty(),
                            icon = Icons.Default.SettingsEthernet,
                            hint = text("Leave empty to use tailscale/<proxy name>.", "留空则使用 tailscale/<节点名称>，避免多个节点共用状态。"),
                        ) {
                            model.update { current -> current.copy(stateDir = it.ifBlank { null }) }
                        }
                        SettingSwitch(text("Ephemeral node", "临时节点"), value.ephemeral, icon = Icons.Default.Timer) {
                            model.update { current -> current.copy(ephemeral = it) }
                        }
                        SettingSwitch(text("Enable UDP", "启用 UDP"), value.udp, icon = Icons.Default.NetworkCheck) {
                            model.update { current -> current.copy(udp = it) }
                        }
                        SettingSwitch(text("Accept subnet routes", "接受子网路由"), value.acceptRoutes, icon = Icons.Default.Route) {
                            model.update { current -> current.copy(acceptRoutes = it) }
                        }
                        SettingText(
                            text("Exit node", "退出节点"),
                            value.exitNode.orEmpty(),
                            icon = Icons.Default.Route,
                            hint = text("Node IP, hostname or auto:any. Leave empty to disable.", "填写节点 IP、主机名或 auto:any；留空表示不指定。"),
                        ) {
                            model.update { current -> current.copy(exitNode = it.ifBlank { null }) }
                        }
                        SettingSwitch(text("Allow LAN access", "允许访问本地局域网"), value.exitNodeAllowLanAccess, icon = Icons.Default.SettingsEthernet) {
                            model.update { current -> current.copy(exitNodeAllowLanAccess = it) }
                        }
                    }
                }

                item {
                    SettingSection(text("Network", "网络选项")) {
                        SettingText(text("Dialer proxy", "底层代理"), value.dialerProxy.orEmpty(), icon = Icons.Default.Route) {
                            model.update { current -> current.copy(dialerProxy = it.ifBlank { null }) }
                        }
                        SettingText(text("Interface name", "网卡名称"), value.interfaceName.orEmpty(), icon = Icons.Default.SettingsEthernet) {
                            model.update { current -> current.copy(interfaceName = it.ifBlank { null }) }
                        }
                        SettingText(
                            text("Routing mark", "Routing Mark"),
                            value.routingMark?.toString().orEmpty(),
                            icon = Icons.Default.SettingsEthernet,
                            hint = text("Linux only. Usually leave empty on Android.", "仅 Linux 有效，Android 通常留空。"),
                        ) {
                            model.update { current -> current.copy(routingMark = it.toIntOrNull()) }
                        }
                        SettingChoice(
                            text("IP version", "IP 版本"),
                            value.ipVersion,
                            listOf(
                                null to text("Default", "默认"),
                                "dual" to text("Dual stack", "双栈"),
                                "ipv4" to "IPv4",
                                "ipv6" to "IPv6",
                                "ipv4-prefer" to text("Prefer IPv4", "优先 IPv4"),
                                "ipv6-prefer" to text("Prefer IPv6", "优先 IPv6"),
                            ),
                            icon = Icons.Default.NetworkCheck,
                        ) {
                            model.update { current -> current.copy(ipVersion = it) }
                        }
                    }
                }
            }

            item {
                InfoCard(
                    title = text("How to use", "如何使用"),
                    subtitle = text(
                        "Tailscale traffic (100.64.0.0/10) is routed through this node automatically after login. To use it for other traffic, select the node in a proxy group.",
                        "登录成功后，Tailscale 网段（100.64.0.0/10）会自动通过该节点。若要用于其他流量，请在代理组中选择该节点。",
                    ),
                    icon = Icons.Default.Info,
                )
            }
        }

        item {
            Button(
                onClick = { model.save() },
                enabled = model.dirty && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saved && !model.dirty) text("Saved", "已保存") else text("Save", "保存")) }
        }
    }
}
