package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.clickable
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

@Composable fun AccessControlScreen(snapshot:SettingsSnapshot,onRuntime:((RuntimeSettings)->RuntimeSettings)->Unit,onUi:((UiPreferences)->UiPreferences)->Unit,query:String,onQueryChange:(String)->Unit,model:AccessControlViewModel=viewModel()){
    val apps by model.apps.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();val prefs=snapshot.ui;val selected=snapshot.desired.accessControlPackages
    val clipboard=LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var filtered=apps.filter{(prefs.accessControlSystemApp||!it.system)&&(it.label.contains(query,true)||it.packageName.contains(query,true))}
    filtered=when(prefs.accessControlSort){"PackageName"->filtered.sortedBy{it.packageName};"InstallTime"->filtered.sortedByDescending{it.installed};"UpdateTime"->filtered.sortedByDescending{it.updated};else->filtered.sortedBy{it.label.lowercase()}}
    if(prefs.accessControlReverse)filtered=filtered.reversed()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.Shield,null,tint=MaterialTheme.colorScheme.primary);Text(text("Select which apps use the VPN. Reconnect after changing this list.","选择经过 VPN 的应用。修改列表后需重新连接。"))}}
        item{InfoCard(text("Selected apps","已选应用"),"${selected.size} / ${apps.size}",icon=Icons.Default.Apps)}
        item {
            val modes=listOf("AcceptAll", "AcceptSelected", "DenySelected")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                SecondaryTabRow(
                    selectedTabIndex=modes.indexOf(snapshot.desired.accessControlMode).coerceAtLeast(0),
                    containerColor=Color.Transparent,
                    contentColor=MaterialTheme.colorScheme.primary,
                    modifier=Modifier.weight(1f),
                ) {
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
                Box {
                    IconButton(onClick={ menu=true }) {
                        Icon(Icons.Default.MoreVert, text("More", "更多"))
                    }
                    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                        DropdownMenuItem(
                            text={Text(text("System apps","系统应用"))},
                            trailingIcon={Checkbox(prefs.accessControlSystemApp,null)},
                            onClick={onUi{it.copy(accessControlSystemApp=!it.accessControlSystemApp)}},
                        )
                        DropdownMenuItem(
                            text={Text(text("Sort","排序"))},
                            trailingIcon={Text(when(prefs.accessControlSort){"PackageName"->text("Package","包名");"InstallTime"->text("Installed","安装时间");"UpdateTime"->text("Updated","更新时间");else->text("Name","名称")})},
                            onClick={onUi{it.copy(accessControlSort=when(it.accessControlSort){"Label"->"PackageName";"PackageName"->"InstallTime";"InstallTime"->"UpdateTime";else->"Label"})}},
                        )
                        DropdownMenuItem(
                            text={Text(text("Reverse","倒序"))},
                            trailingIcon={Checkbox(prefs.accessControlReverse,null)},
                            onClick={onUi{it.copy(accessControlReverse=!it.accessControlReverse)}},
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text={Text(text("Select all","全选"))},
                            leadingIcon={Icon(Icons.Default.SelectAll,null)},
                            onClick={onRuntime{it.copy(accessControlPackages=selected+filtered.map{a->a.packageName})};menu=false},
                        )
                        DropdownMenuItem(
                            text={Text(text("Clear","清空"))},
                            leadingIcon={Icon(Icons.Default.Deselect,null)},
                            onClick={onRuntime{it.copy(accessControlPackages=emptySet())};menu=false},
                        )
                        DropdownMenuItem(
                            text={Text(text("Invert","反选"))},
                            leadingIcon={Icon(Icons.Default.Flip,null)},
                            onClick={val names=filtered.map{it.packageName}.toSet();onRuntime{it.copy(accessControlPackages=(selected-names)+(names-selected))};menu=false},
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text={Text(text("Copy","复制"))},
                            leadingIcon={Icon(Icons.Default.ContentCopy,null)},
                            onClick={clipboard.setText(AnnotatedString(selected.joinToString("\n")));menu=false},
                        )
                        DropdownMenuItem(
                            text={Text(text("Paste","粘贴"))},
                            leadingIcon={Icon(Icons.Default.ContentPaste,null)},
                            onClick={val names=clipboard.getText()?.text.orEmpty().lines().map{it.trim()}.toSet().intersect(apps.map{it.packageName}.toSet());onRuntime{it.copy(accessControlPackages=names)};menu=false},
                        )
                    }
                }
            }
        }
        if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        itemsIndexed(filtered,key={_,app->app.packageName}){index,app->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable{onRuntime{it.copy(accessControlPackages=if(app.packageName in selected)selected-app.packageName else selected+app.packageName)}}.heightIn(min=56.dp).padding(horizontal=12.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Apps,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label,style=MaterialTheme.typography.bodyLarge)
                        Text(app.packageName,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(app.packageName in selected,{checked->onRuntime{it.copy(accessControlPackages=if(checked)selected+app.packageName else selected-app.packageName)}})
                }
                if(index<filtered.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f))
            }
        }
    }
}
