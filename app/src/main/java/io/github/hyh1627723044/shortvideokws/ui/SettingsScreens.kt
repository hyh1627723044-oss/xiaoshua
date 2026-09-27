package io.github.hyh1627723044.shortvideokws.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyh1627723044.shortvideokws.*
import java.util.Locale
import kotlin.math.roundToInt

private fun fmt(value: Double) = String.format(Locale.ROOT, "%.2f", value)
private val LOCKED = Notice("监听中无法修改，请先停止监听", Tone.WARNING)
private val BAD_URL = Notice("服务 URL 必须是 https 完整地址，且不能包含账号密码或 #", Tone.DANGER)

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), content = content)
}

@Composable
fun SettingsHome(
    service: ServiceSnapshot,
    settings: SettingsSnapshot,
    onRecognition: (RecognitionMode) -> Unit,
    onIntent: (IntentMode) -> Unit,
    onOpen: (Page) -> Unit,
    onCommentPosition: () -> Unit,
) = ScreenColumn {
    val idle = !service.busy
    ScreenHeader("设置", "按你的习惯来")
    if (!idle) { Spacer(Modifier.height(12.dp)); NoticeBanner(Notice("监听中，识别与判断方式已锁定", Tone.WARNING)) }

    SectionLabel("语音识别")
    Segmented(listOf("本地关键词", "字节云 ASR"), if (settings.cloud) 1 else 0, idle) {
        onRecognition(if (it == 1) RecognitionMode.CLOUD else RecognitionMode.LOCAL)
    }
    Hint(if (settings.cloud) "短句上传，静音时不发送" else "完全离线，只识别固定口令")
    AppCard {
        SettingRow(Icons.Outlined.Cloud, "字节 ASR", subtitle = "服务地址、凭证与试录测试",
            badge = { ConfiguredPill(settings.asrConfigured) }, onClick = { onOpen(Page.ASR) })
    }

    SectionLabel("行为判断", trailing = if (settings.cloud) null else "仅云端模式")
    Segmented(listOf("严格口令", "JEV 智能判断"), if (settings.intent == IntentMode.JEV) 1 else 0, idle && settings.cloud) {
        onIntent(if (it == 1) IntentMode.JEV else IntentMode.STRICT)
    }
    Hint(if (settings.intent == IntentMode.JEV) "精确口令本地处理，其余交给 JEV 理解" else "整句匹配，否定句不会触发")
    AppCard {
        SettingRow(Icons.Outlined.Psychology, "JEV", subtitle = "服务地址、密钥与模型",
            badge = { ConfiguredPill(settings.jevConfigured) }, onClick = { onOpen(Page.JEV) })
    }

    SectionLabel("控制与调节")
    AppCard {
        SettingRow(Icons.Outlined.GpsFixed, "评论按钮位置", value = "${settings.commentY}%", enabled = idle, onClick = onCommentPosition)
        RowDivider()
        SettingRow(Icons.Outlined.Tune, "高级设置", subtitle = "语音检测与判断阈值", onClick = { onOpen(Page.ADVANCED) })
        RowDivider()
        SettingRow(Icons.AutoMirrored.Outlined.MenuBook, "使用说明", onClick = { onOpen(Page.HELP) })
    }
    SecureFooter()
}

// Blank secret inputs keep the stored value; stored secrets are never shown, only their last characters.
@Composable
private fun SecretStatus(secrets: SecretStore, name: SecretName, version: Int) {
    val value = remember(version) { secrets.get(name) }
    when {
        value == null -> Pill("未配置", Tone.NEUTRAL)
        value.length >= 8 -> Pill("已配置 ···${value.takeLast(4)}", Tone.SUCCESS)
        else -> Pill("已配置", Tone.SUCCESS)
    }
}

private fun storeSecret(secrets: SecretStore, name: SecretName, value: String): Notice? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return try { secrets.put(name, trimmed); null } catch (e: Exception) { Notice("无法加密保存密钥：${e.javaClass.simpleName}", Tone.DANGER) }
}

@Composable
private fun LinkActions(onReset: () -> Unit, onClear: () -> Unit, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = onReset, enabled = enabled) { Text("恢复默认 URL", color = Palette.Primary) }
        Text("·", Modifier.align(Alignment.CenterVertically), color = Palette.Muted)
        TextButton(onClick = onClear, enabled = enabled) { Text("清除凭证", color = Palette.Danger) }
    }
}

@Composable
fun AsrScreen(locked: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { SecretStore(context) }
    val tester = remember { CloudTest() }
    DisposableEffect(Unit) { onDispose { tester.cancel() } }
    var url by remember { mutableStateOf(CloudSettings.asrUrl(context)) }
    var resource by remember { mutableStateOf(CloudSettings.resourceId(context)) }
    var legacy by remember { mutableStateOf(CloudSettings.asrAuthMode(context) == AsrAuthMode.LEGACY) }
    var apiKey by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf(CloudSettings.asrAppId(context)) }
    var token by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmRecord by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) confirmRecord = true else notice = Notice("需要麦克风权限才能试录", Tone.DANGER)
    }

    fun save(): Boolean {
        if (locked) { notice = LOCKED; return false }
        if (Endpoints.parse(url) == null) { notice = BAD_URL; return false }
        CloudSettings.saveAsr(context, url, if (legacy) AsrAuthMode.LEGACY else AsrAuthMode.API_KEY, appId, resource)
        val error = storeSecret(secrets, SecretName.ASR_API_KEY, apiKey) ?: storeSecret(secrets, SecretName.ASR_ACCESS_TOKEN, token)
        if (error != null) { notice = error; return false }
        apiKey = ""; token = ""; version++
        notice = Notice("已保存", Tone.SUCCESS)
        return true
    }

    ScreenColumn {
        ScreenHeader("字节 ASR", "火山引擎录音文件识别极速版", onBack = onBack)
        if (locked) { Spacer(Modifier.height(12.dp)); NoticeBanner(LOCKED) }
        SectionLabel("连接")
        AppCard {
            LabeledField("服务 URL", url, { url = it }, enabled = !locked,
                supporting = "目标域名：${Endpoints.parse(url)?.host ?: "地址无效"}，可替换为兼容中转站")
            LabeledField("Resource ID", resource, { resource = it }, enabled = !locked, supporting = "默认 volc.seedasr.auc（模型 2.0）；开通的是 1.0 极速版则填 volc.bigasr.auc_turbo")
        }
        SectionLabel("凭证")
        Segmented(listOf("新版 API Key", "旧版 AppID + Token"), if (legacy) 1 else 0, !locked) { legacy = it == 1 }
        Spacer(Modifier.height(10.dp))
        AppCard {
            if (!legacy) {
                LabeledField("API Key", apiKey, { apiKey = it }, enabled = !locked, secret = true,
                    placeholder = "留空则保留已保存的密钥", badge = { SecretStatus(secrets, SecretName.ASR_API_KEY, version) })
            } else {
                LabeledField("AppID", appId, { appId = it }, enabled = !locked)
                LabeledField("Access Token", token, { token = it }, enabled = !locked, secret = true,
                    placeholder = "留空则保留已保存的 Token", badge = { SecretStatus(secrets, SecretName.ASR_ACCESS_TOKEN, version) })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row {
            PrimaryButton("保存", Modifier.weight(1f), enabled = !locked && !testing) { save() }
            Spacer(Modifier.width(12.dp))
            OutlineButton(if (testing) "测试中…" else "试录测试", Modifier.weight(1f), Icons.Filled.Mic, enabled = !locked && !testing) {
                if (!save()) return@OutlineButton
                if (CloudSettings.asrAuth(context, secrets) == null) { notice = Notice("请先填写凭证", Tone.WARNING); return@OutlineButton }
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                else confirmRecord = true
            }
        }
        notice?.let { Spacer(Modifier.height(12.dp)); NoticeBanner(it) }
        LinkActions(
            onReset = { if (locked) notice = LOCKED else { CloudSettings.resetAsrUrl(context); url = CloudDefaults.ASR_URL; notice = Notice("已恢复官方端点", Tone.SUCCESS) } },
            onClear = { confirmClear = true },
            enabled = !locked && !testing,
        )
        SecureFooter("密钥由 Android Keystore 加密保存在本机")
    }

    if (confirmRecord) {
        val host = Endpoints.parse(CloudSettings.asrUrl(context))?.host ?: ""
        AppDialog(
            title = "试录测试",
            text = "点击开始后录制 3 秒，并上传到 $host 识别。请说一句话，例如“下一条”。",
            confirm = "开始",
            onConfirm = {
                confirmRecord = false
                val endpoint = Endpoints.parse(CloudSettings.asrUrl(context))
                val auth = CloudSettings.asrAuth(context, secrets)
                if (endpoint == null || auth == null || AppState.listening || AppState.starting) { notice = LOCKED; return@AppDialog }
                testing = tester.asr(endpoint, auth, CloudSettings.resourceId(context),
                    onProgress = { notice = Notice(it, Tone.INFO) },
                    onDone = { notice = it; testing = false })
            },
            onDismiss = { confirmRecord = false },
        )
    }
    if (confirmClear) {
        AppDialog(
            title = "清除字节凭证？", text = "将删除已保存的 API Key 和 Access Token，云端模式需要重新填写后才能使用。",
            confirm = "清除", destructive = true,
            onConfirm = {
                confirmClear = false
                secrets.remove(SecretName.ASR_API_KEY, SecretName.ASR_ACCESS_TOKEN)
                apiKey = ""; token = ""; version++
                notice = Notice("已清除字节凭证", Tone.SUCCESS)
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun JevScreen(locked: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val secrets = remember { SecretStore(context) }
    val tester = remember { CloudTest() }
    DisposableEffect(Unit) { onDispose { tester.cancel() } }
    var url by remember { mutableStateOf(CloudSettings.jevUrl(context)) }
    var model by remember { mutableStateOf(CloudSettings.jevModel(context)) }
    var key by remember { mutableStateOf("") }
    var version by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var testing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    fun save(): Boolean {
        if (locked) { notice = LOCKED; return false }
        if (Endpoints.parse(url) == null) { notice = BAD_URL; return false }
        CloudSettings.saveJev(context, url, model)
        storeSecret(secrets, SecretName.JEV_API_KEY, key)?.let { notice = it; return false }
        key = ""; version++
        notice = Notice("已保存", Tone.SUCCESS)
        return true
    }

    ScreenColumn {
        ScreenHeader("JEV", "从固定行为中选择一个，拿不准就不执行", onBack = onBack)
        if (locked) { Spacer(Modifier.height(12.dp)); NoticeBanner(LOCKED) }
        SectionLabel("连接")
        AppCard {
            LabeledField("服务 URL", url, { url = it }, enabled = !locked,
                supporting = "目标域名：${Endpoints.parse(url)?.host ?: "地址无效"}，可替换为兼容中转站")
            LabeledField("模型名称", model, { model = it }, enabled = !locked, supporting = "建议固定版本，例如 ${CloudDefaults.JEV_MODEL}")
        }
        SectionLabel("凭证")
        AppCard {
            LabeledField("API Key", key, { key = it }, enabled = !locked, secret = true,
                placeholder = "留空则保留已保存的密钥", badge = { SecretStatus(secrets, SecretName.JEV_API_KEY, version) })
        }
        Hint("只发送最终转写文字，不发送音频或屏幕内容。JEV 官方建议密钥放在服务端，这里保存的是你设备上自己的密钥。")
        Spacer(Modifier.height(8.dp))
        Row {
            PrimaryButton("保存", Modifier.weight(1f), enabled = !locked && !testing) { save() }
            Spacer(Modifier.width(12.dp))
            OutlineButton(if (testing) "测试中…" else "测试连接", Modifier.weight(1f), Icons.Outlined.NetworkCheck, enabled = !locked && !testing) {
                if (!save()) return@OutlineButton
                val endpoint = Endpoints.parse(CloudSettings.jevUrl(context)) ?: return@OutlineButton
                val apiKey = secrets.get(SecretName.JEV_API_KEY) ?: run { notice = Notice("请先填写 API Key", Tone.WARNING); return@OutlineButton }
                notice = Notice("正在发送示例“${CloudTest.JEV_SAMPLE}”到 ${endpoint.host}…", Tone.INFO)
                testing = tester.jev(endpoint, apiKey, CloudSettings.jevModel(context), CloudSettings.thresholds(context)) {
                    notice = it; testing = false
                }
            }
        }
        notice?.let { Spacer(Modifier.height(12.dp)); NoticeBanner(it) }
        LinkActions(
            onReset = { if (locked) notice = LOCKED else { CloudSettings.resetJevUrl(context); url = CloudDefaults.JEV_URL; notice = Notice("已恢复官方端点", Tone.SUCCESS) } },
            onClear = { confirmClear = true },
            enabled = !locked && !testing,
        )
        SecureFooter("密钥由 Android Keystore 加密保存在本机")
    }
    if (confirmClear) {
        AppDialog(
            title = "清除 JEV 凭证？", text = "将删除已保存的 JEV API Key，JEV 智能判断需要重新填写后才能使用。",
            confirm = "清除", destructive = true,
            onConfirm = {
                confirmClear = false
                secrets.remove(SecretName.JEV_API_KEY)
                key = ""; version++
                notice = Notice("已清除 JEV 凭证", Tone.SUCCESS)
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
fun AdvancedScreen(locked: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val vad = remember { CloudSettings.vad(context) }
    val thresholds = remember { CloudSettings.thresholds(context) }
    var start by remember { mutableStateOf(fmt(vad.startThreshold.toDouble())) }
    var startMs by remember { mutableStateOf(vad.startMs.toString()) }
    var endMs by remember { mutableStateOf(vad.endMs.toString()) }
    var preRoll by remember { mutableStateOf(vad.preRollMs.toString()) }
    var accept by remember { mutableStateOf(fmt(thresholds.accept)) }
    var like by remember { mutableStateOf(fmt(thresholds.like)) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    val startValue = start.toFloatOrNull()?.takeIf { it in VadSettings.START_THRESHOLD_RANGE }

    fun reset() {
        if (locked) { notice = LOCKED; return }
        CloudSettings.resetAdvanced(context)
        val d = VadSettings()
        start = fmt(d.startThreshold.toDouble()); startMs = d.startMs.toString(); endMs = d.endMs.toString()
        preRoll = d.preRollMs.toString(); accept = fmt(JevThresholds.DEFAULT_ACCEPT); like = fmt(JevThresholds.DEFAULT_LIKE)
        notice = Notice("已恢复默认值", Tone.SUCCESS)
    }
    fun save() {
        if (locked) { notice = LOCKED; return }
        val sm = startMs.toIntOrNull(); val em = endMs.toIntOrNull(); val pr = preRoll.toIntOrNull()
        val a = accept.toDoubleOrNull(); val l = like.toDoubleOrNull()
        val error = when {
            startValue == null -> "起始阈值需在 0.30～0.90"
            sm == null || sm !in VadSettings.START_MS_RANGE -> "起始确认需在 32～500 ms"
            em == null || em !in VadSettings.END_MS_RANGE -> "结束静音需在 96～2000 ms"
            pr == null || pr !in VadSettings.PRE_ROLL_MS_RANGE -> "前置缓存需在 0～1000 ms"
            a == null || a !in JevThresholds.RANGE -> "接受阈值需在 0.50～0.99"
            l == null || l !in JevThresholds.RANGE -> "点赞阈值需在 0.50～0.99"
            else -> null
        }
        if (error != null) { notice = Notice(error, Tone.DANGER); return }
        CloudSettings.saveVad(context, VadSettings(startValue!!, sm!!, em!!, pr!!))
        CloudSettings.saveThresholds(context, JevThresholds(a!!, l!!))
        notice = Notice("已保存，下次开始监听时生效", Tone.SUCCESS)
    }

    ScreenColumn {
        ScreenHeader("高级设置", "调整语音检测的灵敏度", onBack = onBack,
            action = { TextButton(onClick = ::reset, enabled = !locked) { Text("重置", color = Palette.Primary, fontWeight = FontWeight.SemiBold) } })
        if (locked) { Spacer(Modifier.height(12.dp)); NoticeBanner(LOCKED) }
        SectionLabel("语音检测")
        AppCard {
            ValueRow("起始阈值", start, { start = it }, enabled = !locked)
            Slider(
                value = startValue ?: VadSettings.DEFAULT_START_THRESHOLD,
                onValueChange = { start = fmt((it * 100).roundToInt() / 100.0) },
                valueRange = VadSettings.START_THRESHOLD_RANGE, enabled = !locked,
                colors = SliderDefaults.colors(thumbColor = Palette.Primary, activeTrackColor = Palette.Primary, inactiveTrackColor = Palette.Track),
            )
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Background).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("结束阈值", Modifier.weight(1f), fontSize = 14.sp, color = Palette.Text)
                    Text(startValue?.let { "${fmt(VadSettings(startThreshold = it).endThreshold.toDouble())} · 自动计算" } ?: "—",
                        fontSize = 14.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
                }
                Text("始终比起始阈值低 0.15", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
            }
            RowDivider()
            ValueRow("起始确认", startMs, { startMs = it }, "ms", !locked)
            ValueRow("结束静音", endMs, { endMs = it }, "ms", !locked)
            ValueRow("前置缓存", preRoll, { preRoll = it }, "ms", !locked)
            Text("以约 32 ms 为一个判断窗口，时长会向上对齐；前置缓存保留开口前的声音，避免漏掉首字",
                fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        }
        SectionLabel("智能判断")
        AppCard {
            ValueRow("接受阈值", accept, { accept = it }, enabled = !locked)
            ValueRow("点赞阈值", like, { like = it }, enabled = !locked)
            Text("JEV 置信度低于阈值时不执行；点赞更严格", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        }
        notice?.let { Spacer(Modifier.height(12.dp)); NoticeBanner(it) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("保存设置", Modifier.fillMaxWidth(), enabled = !locked, onClick = ::save)
        Text("下次开始监听时生效", Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            fontSize = 12.sp, color = Palette.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) = ScreenColumn {
    ScreenHeader("使用说明", "三步开始，一句话操作", onBack = onBack)
    val sections = listOf(
        "开始使用" to "1. 在控制页点“无障碍服务未开启”，在系统设置中启用“小刷手势控制”。\n2. 点“开始监听”，授予麦克风和通知权限。\n3. 切到抖音普通竖屏视频页，直接说口令。",
        "停止" to "说“停止控制”、点通知上的停止，或回到小刷点“停止监听”。锁屏会自动停止，解锁后不会自动恢复。",
        "本地关键词" to "完全离线，只识别固定口令：下一条、上一条、播放、暂停、点赞点赞、查看评论、关闭评论。不理解否定句。",
        "字节云 ASR" to "本地 VAD 切出一句话后上传识别，静音时不上传。可选严格口令（整句匹配）或 JEV 智能判断（理解自然表达）。云端模式下“停止控制”仍由本地离线识别。",
        "注意" to "播放和暂停都是点一下屏幕中央；关闭评论执行系统返回，只在评论打开时说。视频外放可能误触，建议戴耳机或开启“小刷 + 口令”。",
    )
    sections.forEach { (title, body) ->
        SectionLabel(title)
        AppCard { Text(body, Modifier.padding(vertical = 10.dp), fontSize = 14.sp, color = Palette.Text, lineHeight = 22.sp) }
    }
    SecureFooter("本地模式不联网；云端模式只上传语音短句")
}

@Composable
fun CommentPositionDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AppDialog(
        title = "评论按钮位置",
        confirm = "保存",
        onConfirm = { onSave(value.roundToInt()) },
        onDismiss = onDismiss,
        body = {
            Column {
                Text("说“查看评论”时点击屏幕右侧 92% 宽度处。调整高度，使其对准评论图标。", fontSize = 14.sp, color = Palette.Muted)
                Spacer(Modifier.height(16.dp))
                Text("${value.roundToInt()}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Primary)
                Slider(value, { value = it }, valueRange = 35f..85f, steps = 49,
                    colors = SliderDefaults.colors(thumbColor = Palette.Primary, activeTrackColor = Palette.Primary, inactiveTrackColor = Palette.Track))
            }
        },
    )
}

// All dialogs share this shape and palette so they match the screens.
@Composable
fun AppDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    destructive: Boolean = false,
    dismiss: String = "取消",
    body: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text) },
        text = body ?: text?.let { { Text(it, fontSize = 14.sp, color = Palette.Muted, lineHeight = 21.sp) } },
        confirmButton = {
            Button(onClick = onConfirm, shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (destructive) Palette.Danger else Palette.Primary, contentColor = Color.White)) {
                Text(confirm, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = Palette.Muted) } },
    )
}
