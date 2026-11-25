package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

@Composable
fun HelpScreen(onOpenUrl: (String) -> Unit, showGuides: Boolean = true) {
    val links = listOf(
        R.string.clash_wiki to R.string.clash_wiki_url,
        R.string.clash_meta_wiki to R.string.clash_meta_wiki_url,
        R.string.clash_meta_core to R.string.clash_meta_core_url,
        R.string.clash_meta_for_android to R.string.meta_github_url,
        R.string.reforged_by_glonetx to R.string.glonetx_url,
    )
    val linkIcons = listOf(Icons.Default.MenuBook,Icons.Default.MenuBook,Icons.Default.Code,Icons.Default.Code,Icons.Default.Language)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Info,null,tint=MaterialTheme.colorScheme.primary);Text(stringResource(R.string.tips_help), style = MaterialTheme.typography.bodyLarge)} }
        if (showGuides) {
            item { SectionTitle(text("Quick start", "快速开始")) }
            item {
                InfoCard(
                    title = text("Connect in three steps", "三步开始使用"),
                    subtitle = text(
                        "Import a subscription, start the VPN, then choose a node on the Proxies page.",
                        "导入订阅，启动 VPN，然后在代理页选择节点。",
                    ),
                    icon = Icons.Default.PlayArrow,
                )
            }
            item {
                InfoCard(
                    title = text("Tailscale", "Tailscale"),
                    subtitle = text(
                        "Open Settings → Tailscale, enable it and save. Direct login shows the authorization URL on the same page. The hostname defaults to glonetx-<device model>, and Tailscale network traffic is routed through this node automatically.",
                        "打开 设置 → Tailscale，启用并保存。直接登录会在同一页面显示授权链接；主机名默认使用 glonetx-设备型号，Tailscale 网段会自动通过该节点。",
                    ),
                    icon = Icons.Default.Hub,
                )
            }
            item {
                InfoCard(
                    title = text("Overrides and kernel features", "配置覆盖与内核特性"),
                    subtitle = text(
                        "These pages override the active subscription. Inherit keeps the subscription value; changes are applied after the configuration reloads.",
                        "这些页面会覆盖当前订阅。选择“继承”表示沿用订阅值，保存后会在配置重载时生效。",
                    ),
                    icon = Icons.Default.Tune,
                )
            }
            item {
                InfoCard(
                    title = text("Logs and troubleshooting", "日志与排障"),
                    subtitle = text(
                        "Use live logs to inspect startup and authorization. Search and level filters are in the top bar; switch to Debug temporarily when diagnosing connection issues.",
                        "实时日志可查看启动与授权过程。搜索和级别筛选位于顶部标题栏；排查连接问题时建议临时切换到 Debug。",
                    ),
                    icon = Icons.Default.Article,
                )
            }
            item {
                InfoCard(
                    title = text("Per-app access control", "分应用访问控制"),
                    subtitle = text(
                        "Choose All, Include selected, or Exclude selected. System apps, sorting, reverse order, and bulk actions are in the More menu.",
                        "可选择全部、仅选中或排除选中。系统应用、排序、倒序和批量操作都收在右上角“更多”菜单中。",
                    ),
                    icon = Icons.Default.Apps,
                )
            }
        }
        item { SectionTitle(text("Documentation", "文档与链接")) }
        links.forEachIndexed { index,(title, url) ->
            item(key = title) {
                val target = stringResource(url)
                Card(onClick = { onOpenUrl(target) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(target) }, leadingContent = { Icon(linkIcons[index], contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                }
            }
        }
    }
}

@Composable
fun AboutScreen(version: String, onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GlonetX", style = MaterialTheme.typography.headlineLarge)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Icon(Icons.Default.Info,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary);Text("${text("Version","版本")} $version", style = MaterialTheme.typography.labelLarge)}
        }
        HelpScreen(onOpenUrl, showGuides = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.github.kr328.clash.R.string.support_back))
            }
        })
    }) { insets -> Box(Modifier.fillMaxSize().padding(insets)) { content() } }
}

@Composable
fun CrashScreen(logs: String?, onShare: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.ReportProblem,null,tint=MaterialTheme.colorScheme.error);Text(stringResource(com.github.kr328.clash.R.string.support_crash_description))}
        Button(onClick = onShare, enabled = !logs.isNullOrEmpty()) {
            Icon(Icons.Default.Share,null)
            Text(stringResource(com.github.kr328.clash.R.string.support_share_logs))
        }
        if (logs == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(Modifier.weight(1f)) {
            item { SelectionContainer { Text(logs, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
fun BrokenInstallationScreen(onOpenUrl: (String) -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Warning,null,tint=MaterialTheme.colorScheme.error);Text(stringResource(R.string.application_broken_tips), style = MaterialTheme.typography.bodyLarge)}
        Button(onClick = { onOpenUrl("https://github.com/GlonetX/glonetx-android/releases") }) {
            Icon(Icons.Default.OpenInNew,null)
            Text(stringResource(R.string.github_releases))
        }
    }
}
