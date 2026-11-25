@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.github.kr328.clash.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.OverrideViewModel
import com.github.kr328.clash.core.model.ConfigurationOverride
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

@Composable
fun OverrideScreen(meta: Boolean, onBack: () -> Unit, model: OverrideViewModel = viewModel()) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    var discard by remember { mutableStateOf(false) }
    var showJsonEditor by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var rawError by remember { mutableStateOf<String?>(null) }
    BackHandler { if (model.dirty) discard = true else onBack() }
    LaunchedEffect(saved) { if (saved) onBack() }

    val value = draft
    if (value == null) {
        if (error != null) ErrorState(error!!) { model.load() } else BusyState()
        return
    }

    val descriptor = ConfigurationOverride.serializer().descriptor
    val defaultJson = model.codec.encodeToJsonElement(ConfigurationOverride.serializer(), ConfigurationOverride()).jsonObject
    val overrideCount = countOverrideLeaves(value, defaultJson)
    val groups = listOf(
        text("General", "通用") to listOf("global-ua", "mode", "log-level", "ipv6", "allow-lan", "bind-address"),
        text("Ports", "端口") to listOf("port", "socks-port", "redir-port", "tproxy-port", "mixed-port"),
        text("DNS", "DNS") to listOf("dns"),
        text("Kernel features", "内核特性") to listOf("unified-delay", "geodata-mode", "tcp-concurrent", "find-process-mode", "sniffer", "geox-url"),
        text("Advanced", "高级") to listOf(
            "authentication", "external-controller", "external-controller-tls", "external-controller-cors",
            "secret", "hosts", "profile", "clash-for-android", "disable-keep-alive", "keep-alive-interval", "keep-alive-idle",
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            InfoCard(
                title = text("Configuration overrides", "配置覆盖"),
                subtitle = text(
                    "Inherit keeps the subscription value. Changes are stored locally and applied when the configuration reloads.",
                    "继承表示沿用订阅中的值。修改会保存在本地，并在配置重新加载后生效。",
                ),
                icon = Icons.Default.Tune,
            )
        }
        item { InfoCard(text("Active overrides", "生效覆盖"), "$overrideCount ${text("fields", "项")}", icon = Icons.Default.Tune) }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error != null) item { ErrorState(error!!) { model.load() } }

        groups.forEach { (title, keys) ->
            item {
                SettingSection(title) {
                    keys.forEach { key ->
                        val index = (0 until descriptor.elementsCount).firstOrNull { descriptor.getElementName(it) == key }
                        if (index != null) {
                            ConfigurationField(
                                path = listOf(key),
                                descriptor = descriptor.getElementDescriptor(index),
                                value = value[key] ?: JsonNull,
                                onChange = model::set,
                            )
                        }
                    }
                }
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
                    raw = model.codec.encodeToString(JsonObject.serializer(), value)
                    rawError = null
                    showJsonEditor = true
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Icon(Icons.Default.Code, contentDescription = null); Text(text("Advanced JSON editor", "高级 JSON 编辑器")) }
        }
    }

    if (discard) {
        ConfirmDialog(
            title = text("Discard changes?", "放弃修改？"),
            message = text("The saved overrides will stay unchanged.", "已保存的覆盖配置不会改变。"),
            onDismiss = { discard = false },
        ) { discard = false; onBack() }
    }

    if (showJsonEditor) {
        AlertDialog(
            onDismissRequest = { showJsonEditor = false },
            title = { Text(text("Configuration overrides", "配置覆盖")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp),
                        isError = rawError != null,
                    )
                    rawError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val parsed = model.codec.decodeFromString(ConfigurationOverride.serializer(), raw)
                        model.draft.value = model.codec.encodeToJsonElement(ConfigurationOverride.serializer(), parsed).jsonObject
                        showJsonEditor = false
                        rawError = null
                    } catch (e: Exception) {
                        rawError = e.message
                    }
                }) { Text(text("Apply to draft", "应用到草稿")) }
            },
            dismissButton = { TextButton(onClick = { showJsonEditor = false }) { Text(text("Cancel", "取消")) } },
        )
    }
}

private fun countOverrideLeaves(current: JsonElement?, default: JsonElement?): Int {
    if (current == null || current == JsonNull) return 0
    if (current is JsonObject && default is JsonObject) {
        return current.keys.sumOf { key -> countOverrideLeaves(current[key], default[key]) }
    }
    return if (current != default) 1 else 0
}

@Composable
private fun ConfigurationField(
    path: List<String>,
    descriptor: SerialDescriptor,
    value: JsonElement,
    onChange: (List<String>, JsonElement) -> Unit,
) {
    val label = overrideLabel(path.last())
    var expanded by rememberSaveable(path.joinToString(".")) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val kind = descriptor.kind

    if (kind == StructureKind.CLASS || kind == StructureKind.OBJECT) {
        val size = (value as? JsonObject)?.size ?: 0
        SettingAction(label, "$size ${text("fields", "项")}", icon = Icons.Default.Folder, trailingSummary = true) {
            expanded = !expanded
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp)) {
                for (i in 0 until descriptor.elementsCount) {
                    val key = descriptor.getElementName(i)
                    ConfigurationField(
                        path = path + key,
                        descriptor = descriptor.getElementDescriptor(i),
                        value = (value as? JsonObject)?.get(key) ?: JsonNull,
                        onChange = onChange,
                    )
                }
            }
        }
        return
    }

    val options: List<Pair<String, JsonElement>>? = when (kind) {
        PrimitiveKind.BOOLEAN -> listOf(
            text("Inherit", "继承") to JsonNull,
            text("Enabled", "开启") to JsonPrimitive(true),
            text("Disabled", "关闭") to JsonPrimitive(false),
        )
        SerialKind.ENUM -> listOf(text("Inherit", "继承") to JsonNull) +
            (0 until descriptor.elementsCount).map { descriptor.getElementName(it).let { name -> name to JsonPrimitive(name) } }
        else -> null
    }
    val isSecret = label in setOf("secret", "authentication") || path.last() in setOf("secret", "authentication")
    val summary = when {
        value == JsonNull -> text("Inherit", "继承")
        isSecret -> "••••••••"
        value is JsonArray -> "${value.size} ${text("items", "项")}"
        value is JsonObject -> "${value.size} ${text("fields", "项")}"
        else -> (value as? JsonPrimitive)?.content ?: value.toString()
    }
    val fieldIcon = when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> Icons.Default.Folder
        StructureKind.LIST -> Icons.Default.List
        StructureKind.MAP -> Icons.Default.Map
        PrimitiveKind.BOOLEAN -> Icons.Default.ToggleOn
        PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> Icons.Default.Numbers
        PrimitiveKind.STRING -> Icons.Default.TextFields
        SerialKind.ENUM -> Icons.Default.Category
        else -> Icons.Default.Settings
    }

    Box {
        SettingAction(label, summary, icon = fieldIcon, trailingSummary = true) {
            if (options != null) menu = true
            else {
                input = if (value == JsonNull) "" else if (kind == PrimitiveKind.STRING) value.jsonPrimitive.content else value.toString()
                error = null
                edit = true
            }
        }
        if (options != null) {
            DropdownMenu(menu, { menu = false }) {
                options.forEach { (title, option) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = { onChange(path, option); menu = false })
                }
            }
        }
    }

    if (edit) {
        AlertDialog(
            onDismissRequest = { edit = false },
            title = { Text(label) },
            text = {
                Column {
                    Text(
                        if (kind == StructureKind.LIST || kind == StructureKind.MAP) {
                            text("Enter JSON; leave empty to inherit.", "输入 JSON，留空表示继承。")
                        } else {
                            text("Leave empty to inherit.", "留空表示继承。")
                        },
                    )
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        isError = error != null,
                        visualTransformation = if (isSecret) {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val parsed = if (input.isEmpty()) JsonNull
                        else if (kind == PrimitiveKind.STRING) JsonPrimitive(input)
                        else Json.parseToJsonElement(input)
                        if (kind == StructureKind.LIST && parsed != JsonNull) require(parsed is JsonArray) { "Expected a JSON array" }
                        if (kind == StructureKind.MAP && parsed != JsonNull) require(parsed is JsonObject) { "Expected a JSON object" }
                        if (kind in setOf(PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE) && parsed != JsonNull) {
                            require((parsed as? JsonPrimitive)?.doubleOrNull != null) { "Expected a number" }
                        }
                        onChange(path, parsed)
                        edit = false
                    } catch (e: Exception) {
                        error = e.message
                    }
                }) { Text(text("Apply", "应用")) }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text(text("Cancel", "取消")) } },
        )
    }
}

@Composable
private fun overrideLabel(key: String): String = when (key) {
    "global-ua" -> text("Global User-Agent", "全局 User-Agent")
    "disable-keep-alive" -> text("Disable keep-alive", "禁用 Keep-Alive")
    "keep-alive-interval" -> text("Keep-alive interval", "Keep-Alive 间隔")
    "keep-alive-idle" -> text("Keep-alive idle", "Keep-Alive 空闲时间")
    "profile" -> text("Profile", "配置")
    "port" -> text("HTTP port", "HTTP 端口")
    "socks-port" -> text("SOCKS port", "SOCKS 端口")
    "redir-port" -> text("Redirect port", "Redirect 端口")
    "tproxy-port" -> text("TProxy port", "TProxy 端口")
    "mixed-port" -> text("Mixed port", "混合端口")
    "authentication" -> text("Authentication", "认证")
    "allow-lan" -> text("Allow LAN", "允许局域网连接")
    "bind-address" -> text("Bind address", "监听地址")
    "mode" -> text("Routing mode", "路由模式")
    "log-level" -> text("Log level", "日志级别")
    "ipv6" -> text("IPv6", "IPv6")
    "external-controller" -> text("External controller", "外部控制器")
    "external-controller-tls" -> text("External controller TLS", "外部控制器 TLS")
    "external-controller-cors" -> text("External controller CORS", "外部控制器 CORS")
    "secret" -> text("Secret", "密钥")
    "hosts" -> text("Hosts", "Hosts 映射")
    "unified-delay" -> text("Unified delay", "统一延迟")
    "geodata-mode" -> text("Geodata mode", "Geodata 模式")
    "tcp-concurrent" -> text("TCP concurrent", "TCP 并发")
    "find-process-mode" -> text("Find process mode", "查找进程模式")
    "dns" -> text("DNS", "DNS")
    "clash-for-android" -> text("Android options", "Android 选项")
    "sniffer" -> text("Sniffer", "域名嗅探")
    "geox-url" -> text("GeoX URLs", "地理数据地址")
    "enable" -> text("Enable", "启用")
    "ports" -> text("Ports", "端口")
    "override-destination" -> text("Override destination", "覆盖目标地址")
    "force-dns-mapping" -> text("Force DNS mapping", "强制 DNS 映射")
    "parse-pure-ip" -> text("Parse pure IP", "解析纯 IP")
    "force-domain" -> text("Force domains", "强制解析域名")
    "skip-domain" -> text("Skip domains", "跳过域名")
    "geoip" -> "GeoIP"
    "mmdb" -> "MMDB"
    "geosite" -> "GeoSite"
    else -> key
}
