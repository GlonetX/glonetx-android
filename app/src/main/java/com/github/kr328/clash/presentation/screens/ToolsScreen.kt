package com.github.kr328.clash.presentation.screens

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.github.kr328.clash.R
import com.github.kr328.clash.core.model.ProbeResult
import com.github.kr328.clash.design.model.ProbeServices
import com.github.kr328.clash.presentation.FeatureViewModel
import com.github.kr328.clash.service.settings.UiPreferences

@Composable
fun ToolsScreen(
    prefs: UiPreferences,
    onPrefs: ((UiPreferences) -> UiPreferences) -> Unit,
    model: FeatureViewModel = viewModel(),
) {
    val exit by model.ip.collectAsStateWithLifecycle()
    val direct by model.directIp.collectAsStateWithLifecycle()
    val probes by model.probes.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val probeRunning by model.probeRunning.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        text("Outbound", "出口"),
        text("Services", "服务"),
        text("Map", "地图"),
        text("Network", "网络"),
    )
    val orderedServices = ProbeServices.ALL.sortedBy { service ->
        prefs.probeServiceOrder.indexOf(service.name).let { if (it < 0) Int.MAX_VALUE else it }
    }
    val visibleServices = orderedServices.filter { prefs.probeServiceCategory == "All" || it.category == prefs.probeServiceCategory }

    LaunchedEffect(Unit) { model.loadOutbound() }

    Column(Modifier.fillMaxWidth()) {
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) })
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(text("Outbound IPs", "出口 IP"), Modifier.weight(1f))
                            IconButton(onClick = { model.loadOutbound() }, enabled = !busy) {
                                Icon(Icons.Default.Refresh, text("Refresh", "刷新"))
                            }
                        }
                    }
                    if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (error != null) item { ErrorState(error!!) { model.loadOutbound() } }
                    item { QualityIpCard(text("Proxy exit", "代理出口"), exit, Icons.Default.Public) }
                    item { QualityIpCard(text("Direct network", "直连网络"), direct, Icons.Default.Dns) }
                    item { OutboundComparisonCard(exit, direct) }
                }
                1 -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionTitle(text("Service probes", "服务检测"), Modifier.weight(1f))
                            TextButton(onClick = { model.probes.value = emptyMap(); model.error.value = null }) { Text(text("Reset", "重置")) }
                        }
                    }
                    item { ServiceProbeSummary(exit, probes) }
                    item { ServiceCategoryFilters(prefs, onPrefs) }
                    item { CustomLatencyTest(model, busy) }
                    item {
                        Button(
                            enabled = !probeRunning,
                            onClick = { model.probe(ProbeServices.ALL.map { it.name to it.url }) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Speed, null)
                            Text(if (probeRunning) text("Testing…", "检测中…") else text("Test all services", "检测全部服务"))
                        }
                    }
                    if (probeRunning) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (error != null) item { ErrorState(error!!) { model.error.value = null } }
                    itemsIndexed(visibleServices, key = { _, service -> service.name }) { _, service ->
                        ServiceProbeCard(
                            service = service,
                            result = probes[service.name],
                            onTest = { model.probe(listOf(service.name to service.url)) },
                            testing = probeRunning,
                        )
                    }
                }
                2 -> {
                    item { SectionTitle(text("Server map", "服务器地图")) }
                    item { ServiceMapCard(probes) }
                }
                else -> {
                    item { SectionTitle(text("Network diagnostics", "网络诊断")) }
                    item { NetworkDiagnosticsCard() }
                }
            }
        }
    }
}

@Composable
private fun OutboundComparisonCard(
    exit: com.github.kr328.clash.core.model.ExternalIP?,
    direct: com.github.kr328.clash.core.model.ExternalIP?,
) {
    val sameIp = exit?.ip != null && exit.ip == direct?.ip
    val sameCountry = exit?.countryCode != null && exit.countryCode == direct?.countryCode
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text("Comparison", "对比"), style = MaterialTheme.typography.titleMedium)
            QualityDetailRow(text("Same IP", "相同 IP"), yesNo(sameIp))
            QualityDetailRow(text("Same country", "相同国家"), yesNo(sameCountry))
            QualityDetailRow(text("Proxy protocol", "出口协议"), exit?.ip?.let { if (it.contains(':')) "IPv6" else "IPv4" } ?: "—")
            QualityDetailRow(text("Direct protocol", "直连协议"), direct?.ip?.let { if (it.contains(':')) "IPv6" else "IPv4" } ?: "—")
        }
    }
}

@Composable
private fun ServiceProbeSummary(
    exit: com.github.kr328.clash.core.model.ExternalIP?,
    probes: Map<String, ProbeResult?>,
) {
    val tested = probes.values.filterNotNull()
    val reachable = probes.entries.count { (name, result) ->
        result != null && result.code in (ProbeServices.ALL.firstOrNull { it.name == name }?.okCodes ?: emptySet())
    }
    val average = tested.filter { it.latency >= 0 }.map { it.latency }.average().takeIf { it.isFinite() }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text("Current exit", "当前出口"), style = MaterialTheme.typography.titleMedium)
            QualityDetailRow(text("Country", "国家"), exit?.country ?: "—")
            QualityDetailRow(text("City", "城市"), exit?.city ?: "—")
            QualityDetailRow(text("Reachable", "可达"), "$reachable/${tested.size}")
            QualityDetailRow(text("Average latency", "平均延迟"), average?.let { "${it.toInt()} ms" } ?: "—")
        }
    }
}

@Composable
private fun ServiceCategoryFilters(prefs: UiPreferences, onPrefs: ((UiPreferences) -> UiPreferences) -> Unit) {
    val categories = listOf("All", "AI", "Streaming", "Developer", "Domestic", "General")
    val selected = prefs.probeServiceCategory.ifBlank { "All" }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onPrefs { it.copy(probeServiceCategory = category) } },
                label = { Text(category) },
            )
        }
    }
}

@Composable
private fun CustomLatencyTest(model: FeatureViewModel, busy: Boolean) {
    var url by rememberSaveable { mutableStateOf("https://www.gstatic.com/generate_204") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            url,
            { url = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text("Test URL", "测试地址")) },
            leadingIcon = { Icon(Icons.Default.Link, null) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = { model.action { result = model.repository.latency(url).toString() } }) {
                Text(text("Test", "测试"))
            }
            result?.let { value ->
                val latency = value.toLongOrNull()
                Text(
                    if (latency != null && latency >= 0) "$latency ms" else text("Failed", "失败"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (latency != null && latency >= 0) toolLatencyColor(latency.toInt()) else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ServiceProbeCard(
    service: ProbeServices.Service,
    result: ProbeResult?,
    onTest: () -> Unit,
    testing: Boolean,
) {
    val status = when {
        result == null -> ServiceStatus.Untested
        result.code in service.okCodes -> ServiceStatus.Reachable
        result.code in service.restrictedCodes -> ServiceStatus.Restricted
        else -> ServiceStatus.Failed
    }
    val latency = result?.latency ?: -1
    val code = result?.code ?: -1
    val statusColor = when (status) {
        ServiceStatus.Reachable -> MaterialTheme.colorScheme.primary
        ServiceStatus.Restricted -> Color(0xFFF9A825)
        ServiceStatus.Failed -> MaterialTheme.colorScheme.error
        ServiceStatus.Untested -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ServiceLogo(service.name, Modifier.size(21.dp))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor,
                    modifier = Modifier.align(Alignment.BottomEnd).size(8.dp),
                ) {}
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(service.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "${service.category} · ${statusLabel(status)}",
                        result?.country,
                        result?.city,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result?.serverIp != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (latency >= 0) "$latency ms" else "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (latency >= 0) toolLatencyColor(latency.toInt()) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result != null) Text("HTTP $code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTest, enabled = !testing, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Speed, text("Test", "测速"), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ServiceLogo(name: String, modifier: Modifier = Modifier) {
    val asset = serviceLogoAsset(name)
    if (asset == null) {
        Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/service_icons/$asset.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(serviceLogoColor(name)),
        error = { Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier) },
    )
}

private fun serviceLogoAsset(name: String): String? = when (name.lowercase()) {
    "google" -> "google"
    "cloudflare" -> "cloudflare"
    "github" -> "github"
    "gitlab" -> "gitlab"
    "youtube" -> "youtube"
    "netflix" -> "netflix"
    "disney+" -> "disneyplus"
    "chatgpt" -> "chatgpt"
    "claude" -> "claude"
    "gemini" -> "gemini"
    "perplexity" -> "perplexity"
    "sora" -> "sora"
    "baidu" -> "baidu"
    "bilibili" -> "bilibili"
    "tencent" -> "tencent"
    "alibaba cloud" -> "alibabacloud"
    "wechat" -> "wechat"
    "douyin" -> "douyin"
    "jd" -> "jd"
    "weibo" -> "weibo"
    else -> null
}

@Composable
private fun serviceLogoColor(name: String): Color = when (name.lowercase()) {
    "google" -> Color(0xFF4285F4)
    "cloudflare" -> Color(0xFFF38020)
    "github" -> Color(0xFF181717)
    "gitlab" -> Color(0xFFFC6D26)
    "youtube" -> Color(0xFFFF0000)
    "netflix" -> Color(0xFFE50914)
    "disney+" -> Color(0xFF01147C)
    "chatgpt", "sora" -> Color(0xFF412991)
    "claude" -> Color(0xFFD97757)
    "gemini" -> Color(0xFF8E75B2)
    "perplexity" -> Color(0xFF20808D)
    "baidu" -> Color(0xFF2932E1)
    "bilibili" -> Color(0xFF00A1D6)
    "tencent" -> Color(0xFFEB1923)
    "alibaba cloud" -> Color(0xFFFF6A00)
    "wechat" -> Color(0xFF07C160)
    "douyin" -> Color(0xFF000000)
    "jd" -> Color(0xFFE1251B)
    "weibo" -> Color(0xFFE6162D)
    else -> MaterialTheme.colorScheme.onSurface
}

private enum class ServiceStatus { Reachable, Restricted, Failed, Untested }

@Composable
private fun statusLabel(status: ServiceStatus): String = when (status) {
    ServiceStatus.Reachable -> text("Reachable", "可达")
    ServiceStatus.Restricted -> text("Restricted", "受限")
    ServiceStatus.Failed -> text("Failed", "失败")
    ServiceStatus.Untested -> text("Not tested", "未测试")
}

@Composable
private fun ServiceMapCard(probes: Map<String, ProbeResult?>) {
    val located = ProbeServices.ALL.mapNotNull { service ->
        probes[service.name]?.takeIf { it.latitude != null && it.longitude != null }?.let { service to it }
    }
    if (located.isEmpty()) {
        EmptyState(text("Run a service test to plot server locations", "运行服务检测后在地图上显示服务器位置"))
        return
    }
    val map = ImageBitmap.imageResource(R.drawable.world_map)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFF9A825),
        Color(0xFF8E24AA),
        Color(0xFF00897B),
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text("Resolved server locations", "解析服务器位置"), style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                val crop = WorldMapGeometry.worldCrop(map.width.toFloat(), map.height.toFloat())
                val scale = minOf(size.width / crop.width, size.height / crop.height)
                val offsetX = (size.width - crop.width * scale) / 2f - crop.left * scale
                val offsetY = (size.height - crop.height * scale) / 2f - crop.top * scale
                clipRect {
                    withTransform({
                        translate(left = offsetX, top = offsetY)
                        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
                    }) {
                        drawImage(image = map, alpha = 0.34f)
                    }
                }
                located.forEachIndexed { index, (_, result) ->
                    val point = WorldMapGeometry.projectOrNull(
                        result.latitude,
                        result.longitude,
                        map.width.toFloat(),
                        map.height.toFloat(),
                    ) ?: return@forEachIndexed
                    val marker = Offset(point.x * scale + offsetX, point.y * scale + offsetY)
                    drawCircle(colors[index % colors.size].copy(alpha = 0.2f), radius = 11.dp.toPx(), center = marker)
                    drawCircle(colors[index % colors.size], radius = 5.dp.toPx(), center = marker)
                }
            }
            located.forEachIndexed { index, (service, result) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = colors[index % colors.size], modifier = Modifier.size(8.dp)) {}
                    QualityDetailRow(
                        service.name,
                        listOfNotNull(result.country, result.city).joinToString(" · "),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkDiagnosticsCard() {
    val context = LocalContext.current
    var info by remember { mutableStateOf(readNetworkDiagnostics(context)) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text("Network diagnostics", "网络诊断"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { info = readNetworkDiagnostics(context) }) {
                    Icon(Icons.Default.Refresh, text("Refresh", "刷新"))
                }
            }
            QualityDetailRow(text("Active network", "当前网络"), info.activeNetwork)
            QualityDetailRow(text("Underlying network", "底层网络"), info.underlyingNetwork)
            QualityDetailRow(text("Internet", "互联网"), yesNo(info.hasInternet))
            QualityDetailRow(text("Validated", "已验证"), yesNo(info.validated))
            QualityDetailRow(text("VPN active", "VPN 已连接"), yesNo(info.vpnActive))
            QualityDetailRow(text("DNS servers", "DNS 服务器"), info.dnsServers.ifEmpty { listOf("—") }.joinToString("\n"))
            QualityDetailRow(text("Private DNS", "私有 DNS"), info.privateDns)
            QualityDetailRow(text("Link addresses", "链路地址"), info.addresses.ifEmpty { listOf("—") }.joinToString("\n"))
        }
    }
}

private data class NetworkDiagnostics(
    val activeNetwork: String,
    val underlyingNetwork: String,
    val hasInternet: Boolean,
    val validated: Boolean,
    val vpnActive: Boolean,
    val dnsServers: List<String>,
    val privateDns: String,
    val addresses: List<String>,
)

@SuppressLint("MissingPermission")
private fun readNetworkDiagnostics(context: Context): NetworkDiagnostics {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val active = manager.activeNetwork
    val activeCaps = active?.let(manager::getNetworkCapabilities)
    val underlying = manager.allNetworks.firstOrNull { network ->
        val caps = manager.getNetworkCapabilities(network)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN).not()
    }
    val underlyingCaps = underlying?.let(manager::getNetworkCapabilities)
    val link = active?.let(manager::getLinkProperties)
    val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        when {
            link?.isPrivateDnsActive == true -> "Strict"
            !link?.privateDnsServerName.isNullOrBlank() -> "Provider"
            else -> "Off"
        }
    } else "Off"
    return NetworkDiagnostics(
        activeNetwork = networkType(activeCaps),
        underlyingNetwork = networkType(underlyingCaps),
        hasInternet = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        validated = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        vpnActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
        dnsServers = link?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
        privateDns = privateDns,
        addresses = link?.linkAddresses?.mapNotNull { it.address.hostAddress }.orEmpty(),
    )
}

private fun networkType(capabilities: NetworkCapabilities?): String = when {
    capabilities == null -> "—"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB"
    else -> "Other"
}

@Composable
private fun toolLatencyColor(delay: Int): Color {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    return when {
        delay < 0 -> colors.onSurfaceVariant
        delay <= 200 -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        delay <= 500 -> if (dark) Color(0xFFFFD54F) else Color(0xFFF9A825)
        else -> colors.error
    }
}

@Composable
private fun yesNo(value: Boolean): String = if (value) text("Yes", "是") else text("No", "否")
