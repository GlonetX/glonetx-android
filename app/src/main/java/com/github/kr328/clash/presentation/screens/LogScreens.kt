package com.github.kr328.clash.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.LogsViewModel
import com.github.kr328.clash.core.model.LogMessage
import java.text.DateFormat

@Composable fun LogsScreen(onOpen:(String)->Unit,model:LogsViewModel=viewModel()){
    val files by model.files.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var confirm by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){model.refresh()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{InfoCard(text("Saved logs","已保存日志"),"${files.size} ${text("files","个文件")}",icon=Icons.Default.Description)}
        item{Row{Button(onClick={onOpen("live")}){Icon(Icons.Default.PlayArrow,null);Text(text("Live logs","实时日志"))};Spacer(Modifier.width(12.dp));TextButton(enabled=files.isNotEmpty(),onClick={confirm=true}){Icon(Icons.Default.Delete,null);Text(text("Delete all","全部删除"))}}}
        if(error!=null)item{ErrorState(error!!){model.refresh()}}
        items(files,key={it.fileName}){file->InfoCard(DateFormat.getDateTimeInstance().format(file.date),file.fileName,onClick={onOpen(file.fileName)},icon=Icons.Default.Description)}
        if(files.isEmpty())item{EmptyState(text("No saved logs","没有保存的日志"))}
    }
    if(confirm)ConfirmDialog(text("Delete saved logs?","删除已保存日志？"),text("This cannot be undone.","此操作无法撤销。"),{confirm=false}){confirm=false;model.action{model.repository.deleteAll();model.files.value=model.repository.files()}}
}

@Composable fun LogViewerScreen(name:String,onBack:()->Unit,model:LogsViewModel=viewModel()){
    val messages by model.messages.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var query by rememberSaveable{mutableStateOf("")};var paused by rememberSaveable{mutableStateOf(false)};var level by rememberSaveable{mutableStateOf("Debug")};var confirm by remember{mutableStateOf(false)}
    val list=rememberLazyListState();var follow by rememberSaveable{mutableStateOf(true)}
    val filtered=messages.filter{it.message.contains(query,true)&&(level=="Debug"||it.level.ordinal>=(runCatching{LogMessage.Level.valueOf(level)}.getOrDefault(LogMessage.Level.Debug)).ordinal)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)model.export(uri,filtered)}
    LaunchedEffect(name,paused){if(name=="live"){if(!paused)model.stream()else model.pause()}else model.read(name)}
    DisposableEffect(Unit){onDispose{model.pause()}}
    LaunchedEffect(filtered.size,follow){if(follow&&filtered.isNotEmpty())list.animateScrollToItem(filtered.lastIndex+3)}
    LazyColumn(state=list,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(text("Search messages","搜索日志"))},singleLine=true,leadingIcon={Icon(Icons.Default.Search,null)})}
        item{InfoCard(text("Messages","日志"),"${text("Showing","显示")} ${filtered.size} / ${messages.size}",icon=Icons.Default.Article)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(name=="live")FilterChip(paused,{paused=!paused},label={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Icon(if(paused)Icons.Default.PlayArrow else Icons.Default.Pause,null,Modifier.size(14.dp));Text(text("Pause","暂停"))}});FilterChip(follow,{follow=!follow},label={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(14.dp));Text(text("Follow","跟随"))}});TextButton(onClick={export.launch(if(name=="live")"glonetx-live.log"else name)}){Icon(Icons.Default.SaveAlt,null);Text(text("Export","导出"))};TextButton(onClick={confirm=true}){Icon(if(name=="live")Icons.Default.Stop else Icons.Default.Delete,null);Text(if(name=="live")text("Stop","停止")else text("Delete","删除"))}}}
        item{
            val levels=listOf(LogMessage.Level.Debug,LogMessage.Level.Info,LogMessage.Level.Warning,LogMessage.Level.Error)
            SecondaryTabRow(selectedTabIndex=levels.indexOfFirst{it.name==level}.coerceAtLeast(0),containerColor=Color.Transparent,contentColor=MaterialTheme.colorScheme.primary) {
                levels.forEach{l->val icon=when(l){LogMessage.Level.Debug->Icons.Default.BugReport;LogMessage.Level.Info->Icons.Default.Info;LogMessage.Level.Warning->Icons.Default.Warning;else->Icons.Default.ErrorOutline};Tab(selected=level==l.name,onClick={level=l.name;if(name=="live")model.action{model.repository.level(l)}},icon={Icon(icon,null,Modifier.size(16.dp))},text={Text(l.name)})}
            }
        }
        if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        itemsIndexed(filtered,key={index,m->"${m.time.time}/$index"}){_,m->
            val levelColor=when(m.level){LogMessage.Level.Error->MaterialTheme.colorScheme.error;LogMessage.Level.Warning->MaterialTheme.colorScheme.tertiary;LogMessage.Level.Info->MaterialTheme.colorScheme.primary;else->MaterialTheme.colorScheme.onSurfaceVariant}
            val levelIcon=when(m.level){LogMessage.Level.Debug->Icons.Default.BugReport;LogMessage.Level.Info->Icons.Default.Info;LogMessage.Level.Warning->Icons.Default.Warning;else->Icons.Default.ErrorOutline}
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){Icon(levelIcon,null,Modifier.size(14.dp),tint=levelColor);Text("${DateFormat.getTimeInstance().format(m.time)} · ${m.level}",style=MaterialTheme.typography.labelSmall,color=levelColor)};androidx.compose.foundation.text.selection.SelectionContainer{Text(m.message,style=MaterialTheme.typography.bodySmall)}}}
        }
    }
    if(confirm)ConfirmDialog(if(name=="live")text("Stop recording?","停止记录？")else text("Delete log?","删除日志？"),name,{confirm=false}){confirm=false;if(name=="live"){model.stop();onBack()}else model.action{model.repository.delete(name);onBack()}}
}
