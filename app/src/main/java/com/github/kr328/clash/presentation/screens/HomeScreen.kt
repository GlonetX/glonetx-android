package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.*
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.settings.*
import com.github.kr328.clash.design.model.ProbeServices
import java.util.Locale

private val widgets = listOf("outbound_mode", "traffic_chart", "traffic_total", "memory", "connections", "uptime", "services", "providers", "ip_info", "profile")
@Composable private fun widgetTitle(key:String):String = when(key){"outbound_mode"->text("Routing mode","路由模式");"traffic_chart"->text("Network activity","网络活动");"traffic_total"->text("Transferred","累计流量");"memory"->text("Memory","内存");"connections"->text("Connections","连接");"uptime"->text("Connected for","运行时长");"services"->text("Service availability","服务可用性");"providers"->text("Providers","Providers");"ip_info"->text("Exit address","出口地址");else->text("Subscription","订阅")}
@Composable private fun widgetIcon(key:String):ImageVector = when(key){
    "outbound_mode"->Icons.Default.AccountTree
    "traffic_chart"->Icons.Default.ShowChart
    "traffic_total"->Icons.Default.SwapVert
    "memory"->Icons.Default.Memory
    "connections"->Icons.Default.Link
    "uptime"->Icons.Default.Timer
    "services"->Icons.Default.CloudQueue
    "providers"->Icons.Default.Dns
    "ip_info"->Icons.Default.Public
    else->Icons.Default.Description
}

@Composable private fun RateMetric(icon:ImageVector,label:String,value:String,tint:Color) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(icon,null,Modifier.size(20.dp),tint=tint)
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
    }
}

private fun formatUptime(seconds:Long):String {
    val days=seconds/86_400
    val time=String.format(Locale.ROOT,"%02d:%02d:%02d",(seconds%86_400)/3_600,(seconds%3_600)/60,seconds%60)
    return if(days>0)"${days}d $time" else time
}

@Composable fun HomeScreen(state: HomeState, preferences: UiPreferences, settings: SettingsSnapshot?, onToggle:()->Unit, onMode:(TunnelState.Mode)->Unit, onNavigate:(String)->Unit, onRefreshIp:()->Unit, onPreferences:((UiPreferences)->UiPreferences)->Unit, probeModel:FeatureViewModel=viewModel(key="dashboard.probes")) {
    var edit by rememberSaveable{mutableStateOf(false)};var window by rememberSaveable{mutableIntStateOf(60)}
    val probes by probeModel.probes.collectAsStateWithLifecycle();val probing by probeModel.probeRunning.collectAsStateWithLifecycle()
    val order=(preferences.dashboardWidgetOrder+widgets).distinct().filter{it in widgets&&it !in preferences.dashboardCardHidden}
    LazyVerticalGrid(columns=GridCells.Adaptive(170.dp),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) { Surface(color=if(state.connected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if(state.connected)text("Connected","已连接")else text("Ready when you are","随时准备连接"),style=MaterialTheme.typography.headlineLarge);Text(state.active?.name?:text("Choose a subscription to begin","选择订阅以开始连接"),color=MaterialTheme.colorScheme.onSurfaceVariant) };FilledIconButton(onClick=onToggle,enabled=!state.loading,modifier=Modifier.size(64.dp)){Icon(Icons.Default.PowerSettingsNew,if(state.connected)text("Disconnect","断开")else text("Connect","连接"),Modifier.size(32.dp))} }
                if(settings?.trafficPolicy==TrafficPolicy.Paused) StatusBanner(Icons.Default.PauseCircleOutline,text("Traffic paused by your network policy","网络策略已暂停流量"),tint=MaterialTheme.colorScheme.error)
                if(settings?.trafficPolicy==TrafficPolicy.Direct) StatusBanner(Icons.Default.Route,text("Direct connection policy is active","直连策略生效中"),tint=MaterialTheme.colorScheme.error)
                if(settings?.pendingRestartFields?.isNotEmpty()==true) StatusBanner(Icons.Default.RestartAlt,text("Settings saved. Reconnect to apply.","设置已保存，重新连接后生效。"))
                Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    RateMetric(Icons.Default.Download,text("Download","下载"),"${bytes(state.download)}/s",MaterialTheme.colorScheme.primary)
                    RateMetric(Icons.Default.Upload,text("Upload","上传"),"${bytes(state.upload)}/s",MaterialTheme.colorScheme.tertiary)
                }
            }
        } }
        if(state.error!=null)item(span={GridItemSpan(maxLineSpan)}){Text(state.error,color=MaterialTheme.colorScheme.error)}
        item(span={GridItemSpan(maxLineSpan)}) { Row(verticalAlignment=Alignment.CenterVertically){Text(text("Overview","概览"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={edit=true}){Icon(Icons.Default.Tune,null);Spacer(Modifier.width(4.dp));Text(text("Customize","自定义"))}} }
        items(order,key={it},span={key->GridItemSpan(if(preferences.dashboardWidgetSizes[key]=="Small"||key in listOf("memory","connections","uptime","traffic_total"))1 else maxLineSpan)}) { key ->
            MetricCard(title=widgetTitle(key),icon=widgetIcon(key)) {
                when(key) {
                    "outbound_mode" -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(TunnelState.Mode.Rule,TunnelState.Mode.Global,TunnelState.Mode.Direct).forEach{mode->FilterChip(state.mode==mode,{onMode(mode)},label={Text(when(mode){TunnelState.Mode.Rule->text("Rule","规则");TunnelState.Mode.Global->text("Global","全局");else->text("Direct","直连")})})}}
                    "traffic_chart" -> {
                        val interval=if(preferences.lightweightMode)5 else 1
                        val effectiveWindow=if(preferences.lightweightMode)window.coerceAtMost(300)else window
                        TrafficGraph(state.history.takeLast(effectiveWindow/interval))
                        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            MetricDetail(Icons.Default.Download,text("Download","下载"),"${bytes(state.download)}/s",tint=MaterialTheme.colorScheme.primary)
                            MetricDetail(Icons.Default.Upload,text("Upload","上传"),"${bytes(state.upload)}/s",tint=MaterialTheme.colorScheme.tertiary)
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            (if(preferences.lightweightMode)listOf(60,300)else listOf(60,300,900)).forEach{n->TextButton(onClick={window=n}){Text(text("${n/60} min","${n/60} 分钟"))}}
                        }
                    }
                    "traffic_total" -> {
                        Text(bytes(state.totalUpload+state.totalDownload),style=MaterialTheme.typography.headlineMedium)
                        MetricDetail(Icons.Default.Upload,text("Uploaded","累计上传"),bytes(state.totalUpload),tint=MaterialTheme.colorScheme.tertiary)
                        MetricDetail(Icons.Default.Download,text("Downloaded","累计下载"),bytes(state.totalDownload),tint=MaterialTheme.colorScheme.primary)
                    }
                    "memory" -> {
                        Text(if(state.connected)bytes(state.memory)else"—",style=MaterialTheme.typography.headlineMedium)
                        Text(text("Clash memory","Clash 内存占用"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "connections" -> {
                        Text(if(state.connected)state.connections.toString()else"—",style=MaterialTheme.typography.headlineMedium)
                        Text(text("active connections","条活动连接"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={onNavigate("connections")}){Icon(Icons.Default.ChevronRight,null);Text(text("Inspect","查看"))}
                    }
                    "uptime" -> {
                        val secs=if(state.startedAt>0)(android.os.SystemClock.elapsedRealtime()-state.startedAt)/1000 else 0
                        Text(if(state.connected)formatUptime(secs)else"—",style=MaterialTheme.typography.headlineMedium)
                        Text(text("this session","本次连接"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "providers" -> {
                        Text(if(state.connected)state.providerCount.toString()else"—",style=MaterialTheme.typography.headlineMedium)
                        Text(text("configured providers","个已配置 Provider"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={onNavigate("providers")}){Icon(Icons.Default.ChevronRight,null);Text(text("Manage","管理"))}
                    }
                    "profile" -> {
                        Text(state.active?.name?:text("No subscription selected","尚未选择订阅"),style=MaterialTheme.typography.titleLarge)
                        state.active?.let{MetricDetail(Icons.Default.CheckCircle,text("Status","状态"),text("Active","当前使用"),tint=MaterialTheme.colorScheme.tertiary)}
                        Button(onClick={onNavigate("profiles")}){Icon(Icons.Default.Subscriptions,null);Text(text("Subscriptions","订阅管理"))}
                    }
                    "ip_info" -> {
                        Text(state.ip?.ip?:"—",style=MaterialTheme.typography.titleLarge)
                        state.ip?.let{Text(listOfNotNull(it.country,it.city,it.isp).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick=onRefreshIp){Icon(Icons.Default.Refresh,null);Text(text("Refresh","刷新"))}
                            TextButton(onClick={onNavigate("quality")}){Icon(Icons.Default.VerifiedUser,null);Text(text("IP quality","IP 质量"))}
                        }
                    }
                    "services" -> {
                        probes.forEach{(name,result)->
                            val(statusIcon,statusColor)=when{
                                result==null->Icons.Default.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
                                result.code in 200..299->Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
                                else->Icons.Default.Warning to MaterialTheme.colorScheme.error
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Icon(statusIcon,null,Modifier.size(16.dp),tint=statusColor)
                                Text(name,Modifier.weight(1f))
                                Text(if(result==null)text("Unavailable","不可用")else"HTTP ${result.code} · ${result.latency} ms",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled=!probing,onClick={val selected=(preferences.probeServiceOrder+ProbeServices.names).distinct().filter{preferences.probeServiceOrder.isEmpty()||it in preferences.probeServiceEnabled};probeModel.probe(ProbeServices.ALL.filter{it.name in selected}.map{it.name to it.url})}){Icon(Icons.Default.NetworkCheck,null);Text(if(probing)text("Checking…","检测中…")else text("Check now","立即检测"))}
                            TextButton(onClick={onNavigate("latency")}){Icon(Icons.Default.ChevronRight,null);Text(text("Details","详情"))}
                        }
                    }
                }
            }
        }
        item(span={GridItemSpan(maxLineSpan)}){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={onNavigate("logs")}){Text(text("Logs","日志"))};TextButton(onClick={onNavigate("help")}){Text(text("Help","帮助"))};TextButton(onClick={onNavigate("about")}){Text(text("About","关于"))}}}
    }
    if(edit) DashboardEditor(preferences,{edit=false},onPreferences)
}

@Composable fun TrafficGraph(points:List<TrafficPoint>) {
    val down=MaterialTheme.colorScheme.primary;val up=MaterialTheme.colorScheme.tertiary
    val latest=points.lastOrNull()
    val summary=if(latest==null)text("No traffic data","暂无流量数据")else text("Traffic chart. Download ${bytes(latest.download)} per second, upload ${bytes(latest.upload)} per second.","流量图。下载 ${bytes(latest.download)}/秒，上传 ${bytes(latest.upload)}/秒。")
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics{contentDescription=summary}) {
        val maximum=points.maxOfOrNull{maxOf(it.upload,it.download)}?.coerceAtLeast(1)?:1
        fun line(selector:(TrafficPoint)->Long,color:androidx.compose.ui.graphics.Color) { val path=Path();points.forEachIndexed{i,p->val x=i.toFloat()/(points.size-1).coerceAtLeast(1)*size.width;val y=size.height-selector(p).toFloat()/maximum*size.height;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=Stroke(2.dp.toPx())) }
        line({it.download},down);line({it.upload},up)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DashboardEditor(prefs:UiPreferences,onDismiss:()->Unit,onSave:((UiPreferences)->UiPreferences)->Unit) {
    var order by remember{mutableStateOf((prefs.dashboardWidgetOrder+widgets).distinct().filter{it in widgets})};var hidden by remember{mutableStateOf(prefs.dashboardCardHidden)};var sizes by remember{mutableStateOf(prefs.dashboardWidgetSizes)}
    val step=with(LocalDensity.current){72.dp.toPx()}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.padding(horizontal=20.dp)){Text(text("Customize overview","自定义概览"),style=MaterialTheme.typography.headlineSmall);Text(text("Hold the handle to reorder cards","长按拖动手柄调整顺序"));LazyColumn(Modifier.weight(1f,false).heightIn(max=480.dp)){itemsIndexed(order,key={_,k->k}){index,key->Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically){var travel by remember{mutableFloatStateOf(0f)};Icon(Icons.Default.DragHandle,text("Reorder","调整顺序"),Modifier.padding(12.dp).pointerInput(order){detectDragGesturesAfterLongPress(onDragStart={travel=0f},onDragEnd={travel=0f},onDragCancel={travel=0f}){change,amount->change.consume();travel+=amount.y;if(kotlin.math.abs(travel)>step){val current=order.indexOf(key);val next=(current+if(travel>0)1 else -1).coerceIn(0,order.lastIndex);if(current!=next)order=order.toMutableList().apply{add(next,removeAt(current))};travel=0f}}});Text(widgetTitle(key),Modifier.weight(1f));TextButton(onClick={sizes=sizes+(key to if(sizes[key]=="Small")"Large" else "Small")}){Text(if(sizes[key]=="Small")text("Small","半宽")else text("Wide","全宽"))};Checkbox(key !in hidden,{checked->hidden=if(checked)hidden-key else hidden+key})}}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={order=widgets;hidden=emptySet();sizes=emptyMap()}){Text(text("Reset","重置"))};Button(onClick={val o=order;val h=hidden;val s=sizes;onSave{it.copy(dashboardWidgetOrder=o,dashboardCardHidden=h,dashboardWidgetSizes=s)};onDismiss()}){Text(text("Save","保存"))}};Spacer(Modifier.height(24.dp))}}
}
