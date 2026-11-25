package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.AccessControlViewModel
import com.github.kr328.clash.service.settings.*

@Composable fun AccessControlScreen(snapshot:SettingsSnapshot,onRuntime:((RuntimeSettings)->RuntimeSettings)->Unit,onUi:((UiPreferences)->UiPreferences)->Unit,model:AccessControlViewModel=viewModel()){
    val apps by model.apps.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();val prefs=snapshot.ui;val selected=snapshot.desired.accessControlPackages
    var query by rememberSaveable{mutableStateOf("")};val clipboard=LocalClipboardManager.current
    var filtered=apps.filter{(prefs.accessControlSystemApp||!it.system)&&(it.label.contains(query,true)||it.packageName.contains(query,true))}
    filtered=when(prefs.accessControlSort){"PackageName"->filtered.sortedBy{it.packageName};"InstallTime"->filtered.sortedByDescending{it.installed};"UpdateTime"->filtered.sortedByDescending{it.updated};else->filtered.sortedBy{it.label.lowercase()}}
    if(prefs.accessControlReverse)filtered=filtered.reversed()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Shield,null,tint=MaterialTheme.colorScheme.primary);Text(text("Select which apps use the VPN. Reconnect after changing this list.","选择经过 VPN 的应用。修改列表后需重新连接。"))}}
        item{InfoCard(text("Selected apps","已选应用"),"${selected.size} / ${apps.size}",icon=Icons.Default.Apps)}
        item {
            val modes=listOf("AcceptAll", "AcceptSelected", "DenySelected")
            SecondaryTabRow(selectedTabIndex=modes.indexOf(snapshot.desired.accessControlMode).coerceAtLeast(0),containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.primary) {
                modes.forEach { mode ->
                    val icon=when(mode){"AcceptAll"->Icons.Default.SelectAll;"AcceptSelected"->Icons.Default.Checklist;else->Icons.Default.Block}
                    Tab(
                        selected = snapshot.desired.accessControlMode == mode,
                        onClick = { onRuntime { it.copy(accessControlMode = mode) } },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        text = { Text(when (mode) {
                            "AcceptAll" -> text("All", "全部")
                            "AcceptSelected" -> text("Include", "仅选中")
                            else -> text("Exclude", "排除选中")
                        }) },
                    )
                }
            }
        }
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(text("Search apps","搜索应用"))},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})}
        item{Row{FilterChip(prefs.accessControlSystemApp,{onUi{it.copy(accessControlSystemApp=!it.accessControlSystemApp)}},label={Text(text("System apps","系统应用"))});TextButton(onClick={onUi{it.copy(accessControlSort=when(it.accessControlSort){"Label"->"PackageName";"PackageName"->"InstallTime";"InstallTime"->"UpdateTime";else->"Label"})}}){Text(when(prefs.accessControlSort){"PackageName"->text("Package","包名");"InstallTime"->text("Installed","安装时间");"UpdateTime"->text("Updated","更新时间");else->text("Name","名称")});Icon(Icons.Default.Sort,null)};TextButton(onClick={onUi{it.copy(accessControlReverse=!it.accessControlReverse)}}){Icon(if(prefs.accessControlReverse)Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,null);Text(text("Reverse","倒序"))}}}
        item{Row{TextButton(onClick={onRuntime{it.copy(accessControlPackages=selected+filtered.map{a->a.packageName})}}){Icon(Icons.Default.SelectAll,null);Text(text("All","全选"))};TextButton(onClick={onRuntime{it.copy(accessControlPackages=emptySet())}}){Icon(Icons.Default.Deselect,null);Text(text("None","清空"))};TextButton(onClick={val names=filtered.map{it.packageName}.toSet();onRuntime{it.copy(accessControlPackages=(selected-names)+(names-selected))}}){Icon(Icons.Default.Flip,null);Text(text("Invert","反选"))};TextButton(onClick={clipboard.setText(AnnotatedString(selected.joinToString("\n")))}){Icon(Icons.Default.ContentCopy,null);Text(text("Copy","复制"))};TextButton(onClick={val names=clipboard.getText()?.text.orEmpty().lines().map{it.trim()}.toSet().intersect(apps.map{it.packageName}.toSet());onRuntime{it.copy(accessControlPackages=names)}}){Icon(Icons.Default.ContentPaste,null);Text(text("Paste","粘贴"))}}}
        if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        items(filtered,key={it.packageName}){app->InfoCard(app.label,app.packageName,icon=Icons.Default.Apps,onClick={onRuntime{it.copy(accessControlPackages=if(app.packageName in selected)selected-app.packageName else selected+app.packageName)}},trailing={Checkbox(app.packageName in selected,{checked->onRuntime{it.copy(accessControlPackages=if(checked)selected+app.packageName else selected-app.packageName)}})})}
    }
}
