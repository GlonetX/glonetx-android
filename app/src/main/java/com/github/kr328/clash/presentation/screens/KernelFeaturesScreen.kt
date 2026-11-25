package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.OverrideViewModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

private fun JsonObject?.nested(vararg path: String): JsonElement? {
    var current: JsonElement? = this
    for (key in path) current = (current as? JsonObject)?.get(key) ?: return null
    return current
}

private fun JsonElement?.asBoolean(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.asString(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

@Composable
fun KernelFeaturesScreen(onBack: () -> Unit, model: OverrideViewModel = viewModel()) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) onBack() }

    val value = draft
    if (value == null) {
        if (error != null) ErrorState(error!!) { model.load() } else BusyState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard(
                title = text("Kernel features", "内核特性"),
                subtitle = text(
                    "These values override the active subscription. Inherit keeps the subscription value.",
                    "这些设置会覆盖当前订阅。选择“继承”表示沿用订阅中的值，重新加载配置后生效。",
                ),
                icon = Icons.Default.Extension,
                trailing = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = text("Help", "帮助"))
                    }
                },
            )
        }
        if (error != null) item { ErrorState(error!!) { model.load() } }

        item {
            SettingSection(text("Core behavior", "内核行为")) {
                TriStateSetting(
                    title = text("Unified delay", "统一延迟"),
                    summary = text("Use one latency value for URL tests and health checks.", "URL 测速与健康检查使用同一延迟结果。"),
                    value = value["unified-delay"],
                    icon = Icons.Default.Timer,
                    onSelect = { model.set(listOf("unified-delay"), it.toJsonElement()) },
                )
                TriStateSetting(
                    title = text("Geodata mode", "Geodata 模式"),
                    summary = text("Prefer GeoData over legacy rule databases.", "优先使用 GeoData 数据库。"),
                    value = value["geodata-mode"],
                    icon = Icons.Default.Dns,
                    onSelect = { model.set(listOf("geodata-mode"), it.toJsonElement()) },
                )
                TriStateSetting(
                    title = text("TCP concurrent", "TCP 并发"),
                    summary = text("Allow concurrent outbound TCP connections.", "允许并发建立出站 TCP 连接。"),
                    value = value["tcp-concurrent"],
                    icon = Icons.Default.NetworkCheck,
                    onSelect = { model.set(listOf("tcp-concurrent"), it.toJsonElement()) },
                )
                ChoiceSetting(
                    title = text("Find process mode", "查找进程模式"),
                    value = value["find-process-mode"],
                    icon = Icons.Default.Speed,
                    options = listOf(
                        null to text("Inherit", "继承"),
                        "off" to text("Off", "关闭"),
                        "strict" to text("Strict", "严格"),
                        "always" to text("Always", "始终"),
                    ),
                    onSelect = { model.set(listOf("find-process-mode"), it.toJsonElement()) },
                )
            }
        }

        item {
            SettingSection(text("Sniffer", "域名嗅探")) {
                TriStateSetting(
                    title = text("Enable sniffer", "启用域名嗅探"),
                    summary = text("Inspect traffic to recover domain names.", "从流量中还原域名信息。"),
                    value = value.nested("sniffer", "enable"),
                    icon = Icons.Default.NetworkCheck,
                    onSelect = { model.set(listOf("sniffer", "enable"), it.toJsonElement()) },
                )
                TriStateSetting(
                    title = text("Force DNS mapping", "强制 DNS 映射"),
                    value = value.nested("sniffer", "force-dns-mapping"),
                    icon = Icons.Default.Dns,
                    onSelect = { model.set(listOf("sniffer", "force-dns-mapping"), it.toJsonElement()) },
                )
                TriStateSetting(
                    title = text("Parse pure IP", "解析纯 IP"),
                    value = value.nested("sniffer", "parse-pure-ip"),
                    icon = Icons.Default.SettingsEthernet,
                    onSelect = { model.set(listOf("sniffer", "parse-pure-ip"), it.toJsonElement()) },
                )
                TriStateSetting(
                    title = text("Override destination", "覆盖目标地址"),
                    value = value.nested("sniffer", "override-destination"),
                    icon = Icons.Default.NetworkCheck,
                    onSelect = { model.set(listOf("sniffer", "override-destination"), it.toJsonElement()) },
                )
                StringListSetting(
                    title = text("Force domains", "强制解析域名"),
                    summary = text("One domain per line. These domains are always resolved by DNS.", "每行一个域名，这些域名会强制通过 DNS 解析。"),
                    value = value.nested("sniffer", "force-domain"),
                    icon = Icons.Default.Dns,
                    onCommit = { model.set(listOf("sniffer", "force-domain"), it) },
                )
                StringListSetting(
                    title = text("Skip domains", "跳过域名"),
                    summary = text("One domain per line. Matching domains skip sniffing.", "每行一个域名，匹配的域名将跳过嗅探。"),
                    value = value.nested("sniffer", "skip-domain"),
                    icon = Icons.Default.Dns,
                    onCommit = { model.set(listOf("sniffer", "skip-domain"), it) },
                )
            }
        }

        item {
            SettingSection(text("GeoX URLs", "地理数据地址")) {
                NullableTextSetting(
                    title = "GeoIP",
                    value = value.nested("geox-url", "geoip"),
                    icon = Icons.Default.Dns,
                    onCommit = { model.set(listOf("geox-url", "geoip"), it) },
                )
                NullableTextSetting(
                    title = "MMDB",
                    value = value.nested("geox-url", "mmdb"),
                    icon = Icons.Default.Memory,
                    onCommit = { model.set(listOf("geox-url", "mmdb"), it) },
                )
                NullableTextSetting(
                    title = "GeoSite",
                    value = value.nested("geox-url", "geosite"),
                    icon = Icons.Default.Dns,
                    onCommit = { model.set(listOf("geox-url", "geosite"), it) },
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
        item {
            OutlinedButton(
                onClick = {
                    listOf(
                        listOf("unified-delay"),
                        listOf("geodata-mode"),
                        listOf("tcp-concurrent"),
                        listOf("find-process-mode"),
                        listOf("sniffer", "enable"),
                        listOf("sniffer", "force-dns-mapping"),
                        listOf("sniffer", "parse-pure-ip"),
                        listOf("sniffer", "override-destination"),
                        listOf("sniffer", "force-domain"),
                        listOf("sniffer", "skip-domain"),
                        listOf("geox-url", "geoip"),
                        listOf("geox-url", "mmdb"),
                        listOf("geox-url", "geosite"),
                    ).forEach { model.set(it, JsonNull) }
                },
                enabled = model.dirty && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(text("Reset this page", "重置本页覆盖")) }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(text("Kernel features help", "内核特性说明")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HelpItem(text("Inherit and override", "继承与覆盖"), text(
                        "Inherit keeps the value from the active subscription. Enabled or Disabled writes a local override. Save, then reload or reconnect for the change to take effect.",
                        "继承表示使用当前订阅中的值；开启或关闭会写入本地覆盖。保存后需要重新加载配置或重新连接才会生效。",
                    ))
                    HelpItem(text("Unified delay", "统一延迟"), text(
                        "Uses one latency result for URL tests, health checks and manual tests, avoiding different results from different test methods.",
                        "让 URL 测速、健康检查和手动测速使用同一次延迟结果，避免不同测试方式显示不同数值。",
                    ))
                    HelpItem(text("Geodata mode", "Geodata 模式"), text(
                        "Matches rules with GeoIP and GeoSite databases. It can reduce legacy rule database reads, but the matching GeoData files must be available.",
                        "使用 GeoIP 和 GeoSite 数据库匹配规则，可减少传统规则数据库的读取；需要核心支持对应的数据文件。",
                    ))
                    HelpItem(text("TCP concurrent", "TCP 并发"), text(
                        "Allows multiple outbound TCP connections at the same time. Useful for concurrent access, but it can increase the number of active connections.",
                        "允许同时建立多个出站 TCP 连接，适合并发访问较多的场景，但也会增加活跃连接数。",
                    ))
                    HelpItem(text("Find process mode", "查找进程模式"), text(
                        "Off disables process lookup. Strict looks up a process only when required. Always tries to identify the process for every connection. Android permissions may limit the result.",
                        "Off 完全不查找；Strict 仅在明确需要时查找；Always 总是尝试识别连接对应的应用进程。Android 系统权限可能限制识别结果。",
                    ))
                    HelpItem(text("Sniffer", "域名嗅探"), text(
                        "Recovers domain names from TLS SNI, HTTP Host and other traffic metadata so rules can route connections correctly. Forced DNS mapping and pure-IP parsing change how the recovered name is used.",
                        "从 TLS SNI、HTTP Host 等流量元数据中还原域名，帮助规则正确分流。强制 DNS 映射和纯 IP 解析控制还原后的域名如何参与匹配。",
                    ))
                    HelpItem(text("GeoX URLs", "地理数据地址"), text(
                        "Custom download addresses for GeoIP, MMDB and GeoSite data. Leave a field empty to inherit the subscription value.",
                        "自定义 GeoIP、MMDB 和 GeoSite 数据地址。留空表示沿用订阅中的配置。",
                    ))
                }
            },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text(text("Got it", "知道了")) } },
        )
    }
}

@Composable
private fun HelpItem(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TriStateSetting(
    title: String,
    summary: String? = null,
    value: JsonElement?,
    icon: ImageVector? = null,
    onSelect: (Boolean?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = when (value.asBoolean()) {
        true -> text("Enabled", "开启")
        false -> text("Disabled", "关闭")
        null -> text("Inherit", "继承")
    }
    SettingAction(title, current, icon = icon, trailingSummary = true) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    listOf(
                        null to text("Inherit", "继承"),
                        true to text("Enabled", "开启"),
                        false to text("Disabled", "关闭"),
                    ).forEach { (option, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value.asBoolean() == option, onClick = { onSelect(option); open = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(text("Cancel", "取消")) } },
        )
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    value: JsonElement?,
    options: List<Pair<String?, String>>,
    icon: ImageVector? = null,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value.asString().ifBlank { null } }?.second ?: text("Inherit", "继承")
    SettingAction(title, current, icon = icon, trailingSummary = true) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { (option, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value.asString().ifBlank { null } == option, onClick = { onSelect(option); open = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text(text("Cancel", "取消")) } },
        )
    }
}

@Composable
private fun NullableTextSetting(
    title: String,
    value: JsonElement?,
    summary: String? = null,
    icon: ImageVector? = null,
    onCommit: (JsonElement) -> Unit,
) {
    val current = when (val element = value) {
        null, JsonNull -> ""
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> element.toString()
    }
    SettingText(
        title = title,
        value = current,
        hint = summary.orEmpty(),
        icon = icon,
    ) { entered ->
        onCommit(if (entered.isBlank()) JsonNull else JsonPrimitive(entered))
    }
}

@Composable
private fun StringListSetting(
    title: String,
    value: JsonElement?,
    summary: String? = null,
    icon: ImageVector? = null,
    onCommit: (JsonElement) -> Unit,
) {
    val current = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString("\n").orEmpty()
    SettingText(
        title = title,
        value = current,
        hint = summary.orEmpty(),
        icon = icon,
    ) { entered ->
        val items = entered.lines().map(String::trim).filter(String::isNotEmpty)
        onCommit(if (items.isEmpty()) JsonNull else JsonArray(items.map(::JsonPrimitive)))
    }
}

private fun Boolean?.toJsonElement(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
private fun String?.toJsonElement(): JsonElement = this?.let { JsonPrimitive(it) } ?: JsonNull
