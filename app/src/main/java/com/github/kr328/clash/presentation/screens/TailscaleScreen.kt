package com.github.kr328.clash.presentation.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.data.ClientRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class TailscaleViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository(application)

    val draft = MutableStateFlow<ConfigurationOverride.Tailscale?>(null)
    val loaded = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    val authUrl = MutableStateFlow<String?>(null)

    private var original: ConfigurationOverride.Tailscale? = null
    private var logChannel: Channel<LogMessage>? = null
    val dirty: Boolean get() = draft.value != original

    init {
        load()
        viewModelScope.launch {
            logChannel = runCatching { Clash.subscribeLogcat() }.getOrNull()
            logChannel?.let { channel ->
                for (message in channel) {
                    extractTailscaleAuthUrl(message.message)?.let { authUrl.value = it }
                }
            }
        }
    }

    fun load() = viewModelScope.launch {
        busy.value = true
        try {
            val current = repository.override().tailscale
            draft.value = current
            original = current
            loaded.value = true
            error.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = e.message
        } finally {
            busy.value = false
        }
    }

    fun update(transform: (ConfigurationOverride.Tailscale) -> ConfigurationOverride.Tailscale) {
        draft.value = (draft.value ?: ConfigurationOverride.Tailscale()).let(transform)
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    override fun onCleared() {
        logChannel?.cancel()
        super.onCleared()
    }

    fun save() = viewModelScope.launch {
        busy.value = true
        try {
            val current = repository.override()
            repository.saveOverride(current.copy(tailscale = draft.value))
            original = draft.value
            saved.value = true
            error.value = null
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
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    var loginMode by remember { mutableStateOf(if (draft?.authKey.isNullOrBlank()) "direct" else "key") }
    LaunchedEffect(draft?.authKey) { loginMode = if (draft?.authKey.isNullOrBlank()) "direct" else "key" }
    LaunchedEffect(saved) { if (saved) onBack() }

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
                    "Tailscale is added as a Mihomo outbound proxy. Direct login prints a login URL in the log on first use.",
                    "Tailscale 会作为 Mihomo 的出站节点写入配置覆盖。直接登录模式会在首次使用时把登录地址输出到日志中。",
                ),
                icon = Icons.Default.Hub,
            )
        }
        if (error != null) item { ErrorState(error!!) { model.load() } }
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
                        model.update { current -> current.copy(name = it.ifBlank { "tailscale" }) }
                    }
                    SettingText(text("Hostname", "主机名"), value.hostname.orEmpty(), icon = Icons.Default.Dns) {
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
                            text("Save and reload, then open Logs to authorize this node.", "保存并重新加载配置后，打开日志完成节点授权。"),
                            icon = Icons.Default.HelpOutline,
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
                        "Save and reload the configuration. In direct login mode, open Logs and use the printed URL to authorize this node. Then select it in a proxy group or route a rule to it.",
                        "保存并重新加载配置。直接登录模式下，打开日志页，用输出的链接完成节点授权；随后在代理组中选择该节点，或在规则中将流量指向它。",
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
            ) { Text(text("Save", "保存")) }
        }
    }
}
