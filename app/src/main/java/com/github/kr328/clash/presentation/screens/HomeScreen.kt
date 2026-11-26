package com.github.kr328.clash.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.presentation.*
import com.github.kr328.clash.service.settings.*
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val widgets = listOf("traffic_total", "connections", "ip_info", "profile")
@Composable private fun widgetTitle(key:String):String = when(key){"traffic_total"->text("Transferred","累计流量");"connections"->text("Connections","连接");"ip_info"->text("Exit address","出口地址");else->text("Subscription","订阅")}
@Composable private fun widgetIcon(key:String):ImageVector = when(key){
    "traffic_total"->Icons.Default.SwapVert
    "connections"->Icons.Default.Link
    "ip_info"->Icons.Default.Public
    else->Icons.Default.Description
}

private fun formatUptime(seconds:Long):String {
    val days=seconds/86_400
    val time=String.format(Locale.ROOT,"%02d:%02d:%02d",(seconds%86_400)/3_600,(seconds%3_600)/60,seconds%60)
    return if(days>0)"${days}d $time" else time
}

@Composable fun HomeScreen(state: HomeState, preferences: UiPreferences, settings: SettingsSnapshot?, onToggle:()->Unit, onMode:(TunnelState.Mode)->Unit, onNavigate:(String)->Unit, onRefreshIp:()->Unit, onPreferences:((UiPreferences)->UiPreferences)->Unit, onHeaderVisible:(Boolean)->Unit) {
    var edit by rememberSaveable{mutableStateOf(false)}
    val listState = rememberLazyGridState()
    LaunchedEffect(listState.firstVisibleItemIndex) {
        onHeaderVisible(listState.firstVisibleItemIndex == 0)
    }
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.connected) {
        while (state.connected) {
            now = android.os.SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val uptime = if (state.connected && state.startedAt > 0) {
        formatUptime(((now - state.startedAt) / 1000).coerceAtLeast(0))
    } else null
    LaunchedEffect(state.connected) {
        if (state.connected) onRefreshIp()
    }
    val order=(preferences.dashboardWidgetOrder+widgets).distinct().filter{it in widgets&&it !in preferences.dashboardCardHidden}
    LazyVerticalGrid(columns=GridCells.Adaptive(170.dp),state=listState,contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) {
            var cardPosition by remember { mutableStateOf(Offset.Zero) }
            var cardSize by remember { mutableStateOf(IntSize.Zero) }
            var buttonCenter by remember { mutableStateOf<Offset?>(null) }
            val view = LocalView.current
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape=RoundedCornerShape(28.dp),
                modifier=Modifier.onGloballyPositioned {
                    cardPosition = it.positionInRoot()
                    cardSize = it.size
                },
            ) {
                Box {
                    FluidConnectionBackground(
                        connected = state.connected,
                        origin = buttonCenter?.let { it - cardPosition },
                        size = cardSize,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    )
                    if (state.connected) {
                        NetworkChartBackground(
                            points = state.history.takeLast(60),
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        val profileLabel = state.active?.name ?: text("Choose a subscription to begin","选择订阅以开始连接")
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (state.connected) {
                                    Text(uptime ?: "00:00:00", style = MaterialTheme.typography.headlineLarge)
                                } else {
                                    Text(text("Ready when you are","随时准备连接"), style = MaterialTheme.typography.headlineLarge)
                                }
                                Text(profileLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(
                                onClick = {
                                    val next = when (state.mode) {
                                        TunnelState.Mode.Rule -> TunnelState.Mode.Global
                                        TunnelState.Mode.Global -> TunnelState.Mode.Direct
                                        else -> TunnelState.Mode.Rule
                                    }
                                    onMode(next)
                                    android.util.Log.d("GlonetXMode", "mode click $next")
                                },
                                enabled = !state.loading,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    when (state.mode) {
                                        TunnelState.Mode.Rule -> Icons.Default.Route
                                        TunnelState.Mode.Global -> Icons.Default.Public
                                        else -> Icons.Default.Link
                                    },
                                    text(
                                        "Routing mode: ${when (state.mode) { TunnelState.Mode.Rule -> "Rule"; TunnelState.Mode.Global -> "Global"; else -> "Direct" }}",
                                        "路由模式：${when (state.mode) { TunnelState.Mode.Rule -> "规则"; TunnelState.Mode.Global -> "全局"; else -> "直连" }}",
                                    ),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick={
                                    if (preferences.hapticFeedback) {
                                        val type = if (android.os.Build.VERSION.SDK_INT >= 30)
                                            android.view.HapticFeedbackConstants.CONFIRM
                                        else
                                            android.view.HapticFeedbackConstants.LONG_PRESS
                                        view.performHapticFeedback(type)
                                    }
                                    onToggle()
                                },
                                enabled=!state.loading,
                                modifier=Modifier.size(52.dp).onGloballyPositioned { buttonCenter = it.positionInRoot() },
                            ){Icon(Icons.Default.PowerSettingsNew,if(state.connected)text("Disconnect","断开")else text("Connect","连接"),Modifier.size(26.dp))}
                        }
                if(settings?.trafficPolicy==TrafficPolicy.Paused) StatusBanner(Icons.Default.PauseCircleOutline,text("Traffic paused by your network policy","网络策略已暂停流量"),tint=MaterialTheme.colorScheme.error)
                if(settings?.trafficPolicy==TrafficPolicy.Direct) StatusBanner(Icons.Default.Route,text("Direct connection policy is active","直连策略生效中"),tint=MaterialTheme.colorScheme.error)
                if(settings?.pendingRestartFields?.isNotEmpty()==true) StatusBanner(Icons.Default.RestartAlt,text("Settings saved. Reconnect to apply.","设置已保存，重新连接后生效。"))
            }
        } } }
        if(state.error!=null)item(span={GridItemSpan(maxLineSpan)}){Text(state.error,color=MaterialTheme.colorScheme.error)}
        item(span={GridItemSpan(maxLineSpan)}) { Row(verticalAlignment=Alignment.CenterVertically){Text(text("Overview","概览"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={edit=true}){Icon(Icons.Default.Tune,null);Spacer(Modifier.width(4.dp));Text(text("Customize","自定义"))}} }
        items(order,key={it},span={key->GridItemSpan(if(preferences.dashboardWidgetSizes[key]=="Small"||key in listOf("memory","connections","traffic_total"))1 else maxLineSpan)}) { key ->
            MetricCard(
                title = widgetTitle(key),
                icon = widgetIcon(key),
                modifier = Modifier.height(150.dp),
                onClick = when (key) {
                    "connections" -> ({ onNavigate("connections") })
                    "profile" -> ({ onNavigate("profiles") })
                    "ip_info" -> ({ onNavigate("quality") })
                    else -> null
                },
                trailing = if (key == "ip_info") {
                    {
                        IconButton(onClick = onRefreshIp) {
                            Icon(Icons.Default.Refresh, text("Refresh", "刷新"), Modifier.size(18.dp))
                        }
                    }
                } else null,
                background = if (key == "ip_info") {
                    {
                        RegionalMapBackground(
                            latitude = state.ip?.latitude,
                            longitude = state.ip?.longitude,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                } else ({}),
            ) {
                when (key) {
                    "traffic_total" -> {
                        Text(bytes(state.totalUpload + state.totalDownload), style = MaterialTheme.typography.headlineSmall)
                        MetricDetail(Icons.Default.Upload, text("Uploaded", "累计上传"), bytes(state.totalUpload), tint = MaterialTheme.colorScheme.tertiary)
                        MetricDetail(Icons.Default.Download, text("Downloaded", "累计下载"), bytes(state.totalDownload), tint = MaterialTheme.colorScheme.primary)
                    }
                    "connections" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (state.connected) state.connections.toString() else "—", style = MaterialTheme.typography.headlineSmall)
                            Text(text("active connections", "条活动连接"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "profile" -> {
                        Text(state.active?.name ?: text("No subscription selected", "尚未选择订阅"), style = MaterialTheme.typography.titleSmall)
                        state.active?.let { profile ->
                            val remaining = (profile.total - profile.upload - profile.download).coerceAtLeast(0)
                            val trafficProgress = if (profile.total > 0) {
                                (remaining.toDouble() / profile.total).coerceIn(0.0, 1.0).toFloat()
                            } else 0f
                            MetricDetail(Icons.Default.DataUsage, text("Traffic left", "剩余流量"), if (profile.total > 0) bytes(remaining) else "—")
                            if (profile.total > 0) {
                                LinearProgressIndicator(
                                    progress = { trafficProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                )
                            }
                            MetricDetail(Icons.Default.Event, text("Time left", "剩余时间"), formatRemainingTime(profile.expire))
                        }
                    }
                    "ip_info" -> {
                        Text(state.ip?.ip?.let(::simplifyIpAddress) ?: if (state.connected) text("Detecting…", "检测中…") else "—", style = MaterialTheme.typography.titleLarge)
                        state.ip?.let {
                            Text(
                                listOfNotNull(it.country, it.city).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if(edit) DashboardEditor(preferences,{edit=false},onPreferences)
}

@Composable
private fun FluidConnectionBackground(
    connected: Boolean,
    origin: Offset?,
    size: IntSize,
    color: Color,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(connected) {
        progress.animateTo(
            targetValue = if (connected) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (connected) 850 else 420,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        if (origin != null && size.width > 0 && size.height > 0) {
            drawFluidBlob(
                progress = progress.value,
                origin = origin,
                size = Size(size.width.toFloat(), size.height.toFloat()),
                color = color,
            )
        }
    }
}

private fun DrawScope.drawFluidBlob(
    progress: Float,
    origin: Offset,
    size: Size,
    color: Color,
) {
    if (progress <= 0f) return
    val target = Offset(0f, size.height)
    val center = Offset(
        x = origin.x + (target.x - origin.x) * progress,
        y = origin.y + (target.y - origin.y) * progress,
    )
    val corners = listOf(
        Offset.Zero,
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height),
    )
    val maxRadius = corners.maxOf { (it - center).getDistance() } * 1.2f
    val radius = maxRadius * progress
    val path = Path()
    val steps = 64
    for (index in 0..steps) {
        val angle = 2.0 * PI * index / steps
        val wave = 1.0 +
            0.06 * sin(angle * 3 + progress * 8) +
            0.035 * sin(angle * 5 - progress * 6)
        val r = radius * wave.toFloat()
        val point = center + Offset(
            x = cos(angle).toFloat() * r,
            y = sin(angle).toFloat() * r,
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}

@Composable
private fun NetworkChartBackground(
    points: List<TrafficPoint>,
    modifier: Modifier = Modifier,
) {
    val down = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val up = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val fixedMaxRate = 5.0 * 1024 * 1024
    Canvas(modifier) {
        if (points.isEmpty()) return@Canvas
        for (index in 0..4) {
            val y = size.height * (1f - index / 4f)
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        fun smoothPath(selector: (TrafficPoint) -> Long): Path {
            val path = Path()
            val coordinates = points.mapIndexed { index, point ->
                val x = index.toFloat() / (points.size - 1).coerceAtLeast(1) * size.width
                val rate = selector(point).toDouble().coerceIn(0.0, fixedMaxRate)
                val y = size.height - (rate / fixedMaxRate).toFloat() * size.height
                Offset(x, y)
            }
            path.moveTo(coordinates.first().x, coordinates.first().y)
            for (index in 1 until coordinates.lastIndex) {
                val current = coordinates[index]
                val next = coordinates[index + 1]
                val midX = (current.x + next.x) / 2f
                val midY = (current.y + next.y) / 2f
                path.quadraticTo(current.x, current.y, midX, midY)
            }
            path.lineTo(coordinates.last().x, coordinates.last().y)
            return path
        }
        fun area(selector: (TrafficPoint) -> Long, color: Color) {
            val path = smoothPath(selector)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()
            drawPath(path, color.copy(alpha = color.alpha * 0.35f))
        }
        area({ it.download }, down)
        area({ it.upload }, up)
        drawPath(smoothPath({ it.download }), down, style = Stroke(1.5.dp.toPx()))
        drawPath(smoothPath({ it.upload }), up, style = Stroke(1.5.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DashboardEditor(prefs:UiPreferences,onDismiss:()->Unit,onSave:((UiPreferences)->UiPreferences)->Unit) {
    var order by remember{mutableStateOf((prefs.dashboardWidgetOrder+widgets).distinct().filter{it in widgets})};var hidden by remember{mutableStateOf(prefs.dashboardCardHidden)};var sizes by remember{mutableStateOf(prefs.dashboardWidgetSizes)}
    val step=with(LocalDensity.current){72.dp.toPx()}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.padding(horizontal=20.dp)){Text(text("Customize overview","自定义概览"),style=MaterialTheme.typography.headlineSmall);Text(text("Hold the handle to reorder cards","长按拖动手柄调整顺序"));LazyColumn(Modifier.weight(1f,false).heightIn(max=480.dp)){itemsIndexed(order,key={_,k->k}){index,key->Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically){var travel by remember{mutableFloatStateOf(0f)};Icon(Icons.Default.DragHandle,text("Reorder","调整顺序"),Modifier.padding(12.dp).pointerInput(order){detectDragGesturesAfterLongPress(onDragStart={travel=0f},onDragEnd={travel=0f},onDragCancel={travel=0f}){change,amount->change.consume();travel+=amount.y;if(kotlin.math.abs(travel)>step){val current=order.indexOf(key);val next=(current+if(travel>0)1 else -1).coerceIn(0,order.lastIndex);if(current!=next)order=order.toMutableList().apply{add(next,removeAt(current))};travel=0f}}});Text(widgetTitle(key),Modifier.weight(1f));TextButton(onClick={sizes=sizes+(key to if(sizes[key]=="Small")"Large" else "Small")}){Text(if(sizes[key]=="Small")text("Small","半宽")else text("Wide","全宽"))};Checkbox(key !in hidden,{checked->hidden=if(checked)hidden-key else hidden+key})}}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={order=widgets;hidden=emptySet();sizes=emptyMap()}){Text(text("Reset","重置"))};Button(onClick={val o=order;val h=hidden;val s=sizes;onSave{it.copy(dashboardWidgetOrder=o,dashboardCardHidden=h,dashboardWidgetSizes=s)};onDismiss()}){Text(text("Save","保存"))}};Spacer(Modifier.height(24.dp))}}
}
