package io.github.hyh1627723044.shortvideokws.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyh1627723044.shortvideokws.*
import kotlinx.coroutines.delay

enum class Tab { CONTROL, SETTINGS }
enum class Page { HOME, ASR, JEV, ADVANCED, HELP }

@Composable
fun XiaoshuaApp() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.CONTROL) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var service by remember { mutableStateOf(ServiceSnapshot.read()) }
    var settings by remember { mutableStateOf(SettingsSnapshot.read(context)) }
    var cloudNotice by remember { mutableStateOf(false) }
    var commentDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { service = ServiceSnapshot.read(); delay(300) }
    }
    fun refresh() { settings = SettingsSnapshot.read(context) }

    BackHandler(enabled = page != Page.HOME || tab != Tab.CONTROL) {
        if (page != Page.HOME) { page = Page.HOME; refresh() } else tab = Tab.CONTROL
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchService(context)
        else AppState.status = "需要麦克风权限才能监听"
    }
    fun toggle() {
        if (service.busy) { stopListening(context); return }
        startListening(context) { needed -> permissions.launch(needed) }
    }
    fun back() { page = Page.HOME; refresh() }

    when (page) {
        Page.ASR -> PageScaffold { AsrScreen(service.busy, ::back) }
        Page.JEV -> PageScaffold { JevScreen(service.busy, ::back) }
        Page.ADVANCED -> PageScaffold { AdvancedScreen(service.busy, ::back) }
        Page.HELP -> PageScaffold { HelpScreen(::back) }
        Page.HOME -> Scaffold(
            containerColor = Palette.Background,
            bottomBar = { BottomBar(tab) { tab = it; refresh() } },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.CONTROL -> ControlScreen(
                        service, settings,
                        onToggle = ::toggle,
                        onAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onOpenSettings = { tab = Tab.SETTINGS },
                        onPrefix = { AppState.prefs(context).edit().putBoolean("prefix", it).apply(); refresh() },
                    )
                    Tab.SETTINGS -> SettingsHome(
                        service, settings,
                        onRecognition = { mode ->
                            if (mode == RecognitionMode.CLOUD && !CloudSettings.noticeAccepted(context)) cloudNotice = true
                            else { CloudSettings.setRecognitionMode(context, mode); refresh() }
                        },
                        onIntent = { CloudSettings.setIntentMode(context, it); refresh() },
                        onOpen = { page = it },
                        onCommentPosition = { commentDialog = true },
                    )
                }
            }
        }
    }

    // The first switch to cloud explains where audio goes; the choice is remembered once accepted.
    if (cloudNotice) {
        val host = Endpoints.parse(CloudSettings.asrUrl(context))?.host ?: "字节 ASR 服务"
        AppDialog(
            title = "启用云端识别？",
            text = "监听时，本地 VAD 切出的每一句话会上传到 $host 转写。静音时不上传，音频不保存。\n\n" +
                "选择 JEV 智能判断时，转写文字还会发送到 JEV 服务。二者都是第三方服务，由你自己的账号计费。",
            confirm = "同意并启用",
            onConfirm = {
                cloudNotice = false
                CloudSettings.acceptNotice(context)
                CloudSettings.setRecognitionMode(context, RecognitionMode.CLOUD)
                refresh()
            },
            onDismiss = { cloudNotice = false },
        )
    }
    if (commentDialog) {
        CommentPositionDialog(
            settings.commentY,
            onSave = { AppState.prefs(context).edit().putInt("comment_y", it).apply(); commentDialog = false; refresh() },
            onDismiss = { commentDialog = false },
        )
    }
}

@Composable
private fun PageScaffold(content: @Composable () -> Unit) {
    Scaffold(containerColor = Palette.Background) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@Composable
private fun BottomBar(tab: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.Primary, selectedTextColor = Palette.Primary,
            indicatorColor = Palette.HeroTint, unselectedIconColor = Palette.Muted, unselectedTextColor = Palette.Muted,
        )
        NavigationBarItem(
            selected = tab == Tab.CONTROL, onClick = { onSelect(Tab.CONTROL) },
            icon = { Icon(if (tab == Tab.CONTROL) Icons.Filled.Mic else Icons.Outlined.Mic, null) },
            label = { Text("控制", fontSize = 12.sp) }, colors = colors,
        )
        NavigationBarItem(
            selected = tab == Tab.SETTINGS, onClick = { onSelect(Tab.SETTINGS) },
            icon = { Icon(if (tab == Tab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings, null) },
            label = { Text("设置", fontSize = 12.sp) }, colors = colors,
        )
    }
}

private fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

private fun startListening(context: Context, requestPermissions: (Array<String>) -> Unit) {
    if (GestureService.instance == null) { toast(context, "请先开启小刷无障碍服务"); return }
    if (CloudSettings.recognitionMode(context) == RecognitionMode.CLOUD) {
        val result = CloudSettings.profile(context, AppState.requirePrefix(context))
        if (result is CloudSettings.ProfileResult.Missing) { toast(context, result.message); return }
    }
    val needed = buildList {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (needed.isEmpty()) launchService(context) else requestPermissions(needed.toTypedArray())
}

private fun launchService(context: Context) {
    try { context.startForegroundService(Intent(context, ListeningService::class.java)) }
    catch (e: RuntimeException) { AppState.status = "无法启动监听：${e.javaClass.simpleName}" }
}

private fun stopListening(context: Context) {
    AppState.listening = false
    context.stopService(Intent(context, ListeningService::class.java))
}
