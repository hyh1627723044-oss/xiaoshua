package io.github.hyh1627723044.shortvideokws

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CloudSettingsActivity : Activity() {
    companion object {
        const val EXTRA_SECTION = "section"
        const val SECTION_ASR = "asr"
        const val SECTION_JEV = "jev"
        const val SECTION_ADVANCED = "advanced"
        private const val TEST_RECORD_MS = 3000
        private const val TEST_TEXT = "帮我点个赞"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val secrets by lazy { SecretStore(this) }
    private lateinit var content: LinearLayout
    private lateinit var result: TextView
    private val testRunning = AtomicBoolean(false)
    @Volatile private var testCall: CloudCall? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 246, 240))
        }
        when (intent.getStringExtra(EXTRA_SECTION)) {
            SECTION_JEV -> buildJev()
            SECTION_ADVANCED -> buildAdvanced()
            else -> buildAsr()
        }
        result = label("", 14f)
        label("密钥使用 Android Keystore 加密后保存在本机，不写入日志或备份。Root 或已被攻破的设备上无法保证密钥安全。", 13f)
        setContentView(ScrollView(this).apply { addView(content) })
        content.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(24), dp(24) + insets.systemWindowInsetTop, dp(24), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
    }

    override fun onDestroy() {
        testRunning.set(false)
        testCall?.cancel()
        super.onDestroy()
    }

    private fun buildAsr() {
        label("字节 ASR 设置", 26f)
        label("火山引擎豆包语音「录音文件识别极速版」。每句话一次请求，默认 Resource ID 为 volc.bigasr.auc_turbo，需要在控制台开通。", 14f)
        val url = field("服务 URL（完整端点，可替换为兼容中转站）", CloudSettings.asrUrl(this))
        val host = label("", 13f)
        showHost(url, host)
        val authGroup = RadioGroup(this)
        val newKey = RadioButton(this).apply { text = "新版控制台 API Key"; id = View.generateViewId(); authGroup.addView(this) }
        val legacy = RadioButton(this).apply { text = "旧版控制台 AppID + Access Token"; id = View.generateViewId(); authGroup.addView(this) }
        content.addView(authGroup)
        val apiKey = secretField("API Key", SecretName.ASR_API_KEY)
        val appId = field("AppID", CloudSettings.asrAppId(this))
        val token = secretField("Access Token", SecretName.ASR_ACCESS_TOKEN)
        val resource = field("Resource ID", CloudSettings.resourceId(this))
        fun showAuth(isLegacy: Boolean) {
            apiKey.visibility = if (isLegacy) View.GONE else View.VISIBLE
            appId.visibility = if (isLegacy) View.VISIBLE else View.GONE
            token.visibility = if (isLegacy) View.VISIBLE else View.GONE
        }
        authGroup.setOnCheckedChangeListener { _, checked -> showAuth(checked == legacy.id) }
        authGroup.check(if (CloudSettings.asrAuthMode(this) == AsrAuthMode.LEGACY) legacy.id else newKey.id)
        showAuth(CloudSettings.asrAuthMode(this) == AsrAuthMode.LEGACY)

        fun save(): Boolean {
            if (!editable()) return false
            if (Endpoints.parse(url.text.toString()) == null) { show("服务 URL 必须是 https 完整地址，且不能包含账号密码或 #"); return false }
            val mode = if (authGroup.checkedRadioButtonId == legacy.id) AsrAuthMode.LEGACY else AsrAuthMode.API_KEY
            CloudSettings.saveAsr(this, url.text.toString(), mode, appId.text.toString(), resource.text.toString())
            if (!storeSecret(apiKey, SecretName.ASR_API_KEY) || !storeSecret(token, SecretName.ASR_ACCESS_TOKEN)) return false
            show("已保存")
            return true
        }
        button("保存") { save() }
        button("试录并测试") {
            if (!save()) return@button
            val auth = CloudSettings.asrAuth(this, secrets) ?: run { show("请先填写凭证"); return@button }
            confirmTestRecording(auth)
        }
        button("清除凭证") {
            if (!editable()) return@button
            secrets.remove(SecretName.ASR_API_KEY, SecretName.ASR_ACCESS_TOKEN)
            apiKey.setText(""); token.setText("")
            apiKey.hint = "API Key（未配置）"; token.hint = "Access Token（未配置）"
            show("已清除字节凭证")
        }
        button("恢复默认 URL") {
            if (!editable()) return@button
            CloudSettings.resetAsrUrl(this)
            url.setText(CloudDefaults.ASR_URL)
            show("已恢复官方端点")
        }
    }

    private fun confirmTestRecording(auth: AsrAuth) {
        if (AppState.listening || AppState.starting) { show("请先停止监听再试录"); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        val endpoint = Endpoints.parse(CloudSettings.asrUrl(this)) ?: return
        AlertDialog.Builder(this)
            .setTitle("试录并测试")
            .setMessage("点击开始后录制 3 秒，并上传到 ${endpoint.host} 识别。请说一句话，例如“下一条”。")
            .setPositiveButton("开始") { _, _ -> runTestRecording(endpoint, auth) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun runTestRecording(endpoint: okhttp3.HttpUrl, auth: AsrAuth) {
        if (!testRunning.compareAndSet(false, true)) return
        val resourceId = CloudSettings.resourceId(this)
        show("正在录音（3 秒）…")
        Thread {
            val message = try {
                val frames = ArrayList<ShortArray>()
                val needed = VadSettings.SAMPLE_RATE * TEST_RECORD_MS / 1000 / VadSettings.FRAME_SAMPLES
                AudioCapture.run(testRunning, onReady = {}) { frame ->
                    frames.add(frame.copyOf())
                    if (frames.size >= needed) testRunning.set(false)
                }
                val pcm = ShortArray(frames.size * VadSettings.FRAME_SAMPLES)
                frames.forEachIndexed { i, f -> f.copyInto(pcm, i * VadSettings.FRAME_SAMPLES) }
                handler.post { show("正在识别…") }
                val client = ByteAsrClient()
                val call = client.newCall(endpoint, auth, resourceId, Wav.encodePcm16Mono(pcm)).also { testCall = it }
                when (val r = client.execute(call)) {
                    is AsrResult.Text -> "测试成功，识别结果：${r.text}"
                    AsrResult.NoSpeech -> "连接成功，但没有识别到语音"
                    is AsrResult.Failure -> "测试失败：${r.reason}"
                }
            } catch (e: Exception) {
                "测试失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                testRunning.set(false)
                testCall = null
            }
            handler.post { if (!isDestroyed) show(message) }
        }.start()
    }

    private fun buildJev() {
        label("JEV 设置", 26f)
        label("转写文字会发送到 JEV，由它从固定行为中选择一个。无法确认时选择“不执行”。JEV 官方建议密钥只放在服务端；这里保存的是你自己设备上的密钥。", 14f)
        val url = field("服务 URL（完整端点，可替换为兼容中转站）", CloudSettings.jevUrl(this))
        val host = label("", 13f)
        showHost(url, host)
        val key = secretField("API Key", SecretName.JEV_API_KEY)
        val model = field("模型名称（建议固定版本）", CloudSettings.jevModel(this))
        fun save(): Boolean {
            if (!editable()) return false
            if (Endpoints.parse(url.text.toString()) == null) { show("服务 URL 必须是 https 完整地址，且不能包含账号密码或 #"); return false }
            CloudSettings.saveJev(this, url.text.toString(), model.text.toString())
            if (!storeSecret(key, SecretName.JEV_API_KEY)) return false
            show("已保存")
            return true
        }
        button("保存") { save() }
        button("测试连接") {
            if (!save()) return@button
            val endpoint = Endpoints.parse(CloudSettings.jevUrl(this)) ?: return@button
            val apiKey = secrets.get(SecretName.JEV_API_KEY) ?: run { show("请先填写 API Key"); return@button }
            if (!testRunning.compareAndSet(false, true)) return@button
            val modelName = CloudSettings.jevModel(this)
            val thresholds = CloudSettings.thresholds(this)
            show("正在发送示例文本“$TEST_TEXT”到 ${endpoint.host}…")
            Thread {
                val jev = JevIntentResolver()
                val call = jev.newCall(endpoint, apiKey, modelName, TEST_TEXT).also { testCall = it }
                val decision = try { jev.execute(call, thresholds) } finally { testRunning.set(false); testCall = null }
                val message = if (decision.command != null) "测试成功：${decision.note}" else "返回：${decision.note}"
                handler.post { if (!isDestroyed) show(message) }
            }.start()
        }
        button("清除凭证") {
            if (!editable()) return@button
            secrets.remove(SecretName.JEV_API_KEY)
            key.setText(""); key.hint = "API Key（未配置）"
            show("已清除 JEV 凭证")
        }
        button("恢复默认 URL") {
            if (!editable()) return@button
            CloudSettings.resetJevUrl(this)
            url.setText(CloudDefaults.JEV_URL)
            show("已恢复官方端点")
        }
    }

    private fun buildAdvanced() {
        label("高级设置", 26f)
        label("Silero VAD 以约 32ms 为一个判断窗口，时长会向上对齐到窗口整数倍，例如 60ms 实际约 64ms。", 14f)
        val vad = CloudSettings.vad(this)
        val start = field("起始阈值（0.30～0.90）", fmt(vad.startThreshold.toDouble()), decimal = true)
        val end = label("", 14f)
        fun showEnd() {
            val value = start.text.toString().toFloatOrNull()
            end.text = if (value != null && value in VadSettings.START_THRESHOLD_RANGE)
                "结束阈值（自动）：${fmt(VadSettings(startThreshold = value).endThreshold.toDouble())}" else "结束阈值（自动）：—"
        }
        start.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = showEnd()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        showEnd()
        val startMs = field("起始确认时长 ms（32～500）", vad.startMs.toString(), number = true)
        val endMs = field("结束确认时长 ms（96～2000）", vad.endMs.toString(), number = true)
        val preRoll = field("前置缓存 ms（0～1000）", vad.preRollMs.toString(), number = true)
        val t = CloudSettings.thresholds(this)
        val accept = field("JEV 接受阈值（0.50～0.99）", fmt(t.accept), decimal = true)
        val like = field("JEV 点赞阈值（0.50～0.99）", fmt(t.like), decimal = true)
        button("保存") {
            if (!editable()) return@button
            val s = start.text.toString().toFloatOrNull()
            val sm = startMs.text.toString().toIntOrNull()
            val em = endMs.text.toString().toIntOrNull()
            val pr = preRoll.text.toString().toIntOrNull()
            val a = accept.text.toString().toDoubleOrNull()
            val l = like.text.toString().toDoubleOrNull()
            val error = when {
                s == null || s !in VadSettings.START_THRESHOLD_RANGE -> "起始阈值需在 0.30～0.90"
                sm == null || sm !in VadSettings.START_MS_RANGE -> "起始确认时长需在 32～500ms"
                em == null || em !in VadSettings.END_MS_RANGE -> "结束确认时长需在 96～2000ms"
                pr == null || pr !in VadSettings.PRE_ROLL_MS_RANGE -> "前置缓存需在 0～1000ms"
                a == null || a !in JevThresholds.RANGE -> "JEV 接受阈值需在 0.50～0.99"
                l == null || l !in JevThresholds.RANGE -> "JEV 点赞阈值需在 0.50～0.99"
                else -> null
            }
            if (error != null) { show(error); return@button }
            CloudSettings.saveVad(this, VadSettings(s!!, sm!!, em!!, pr!!))
            CloudSettings.saveThresholds(this, JevThresholds(a!!, l!!))
            show("已保存，下次开始监听时生效")
        }
        button("恢复高级设置默认值") {
            if (!editable()) return@button
            CloudSettings.resetAdvanced(this)
            val d = VadSettings()
            start.setText(fmt(d.startThreshold.toDouble())); startMs.setText(d.startMs.toString())
            endMs.setText(d.endMs.toString()); preRoll.setText(d.preRollMs.toString())
            accept.setText(fmt(JevThresholds.DEFAULT_ACCEPT)); like.setText(fmt(JevThresholds.DEFAULT_LIKE))
            show("已恢复默认值")
        }
    }

    // Blank input keeps the stored secret; saved secrets are never shown again, only their last characters.
    private fun storeSecret(input: EditText, name: SecretName): Boolean {
        val value = input.text.toString().trim()
        if (value.isEmpty()) return true
        return try {
            secrets.put(name, value)
            input.setText("")
            input.hint = maskedHint(input.tag as String, name)
            true
        } catch (e: Exception) {
            show("无法加密保存密钥：${e.javaClass.simpleName}")
            false
        }
    }
    private fun maskedHint(title: String, name: SecretName): String {
        val value = secrets.get(name) ?: return "$title（未配置）"
        return if (value.length >= 8) "$title（已配置，尾号 ${value.takeLast(4)}）" else "$title（已配置）"
    }
    private fun secretField(title: String, name: SecretName): EditText = EditText(this).apply {
        tag = title
        hint = maskedHint(title, name)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        isSingleLine = true
        content.addView(this)
    }
    private fun field(title: String, value: String, number: Boolean = false, decimal: Boolean = false): EditText {
        label(title, 14f)
        return EditText(this).apply {
            setText(value)
            isSingleLine = true
            inputType = when {
                decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                number -> InputType.TYPE_CLASS_NUMBER
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
            content.addView(this)
        }
    }
    private fun showHost(url: EditText, host: TextView) {
        fun update() { host.text = "实际目标域名：${Endpoints.parse(url.text.toString())?.host ?: "（地址无效）"}" }
        url.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = update()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
        })
        update()
    }
    private fun editable(): Boolean {
        if (AppState.listening || AppState.starting) { show("监听中无法修改，请先停止监听"); return false }
        return true
    }
    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(28, 48, 42))
        setPadding(0, dp(8), 0, dp(8)); content.addView(this)
    }
    private fun button(text: String, action: () -> Unit) {
        content.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
    }
    private fun show(message: String) { result.text = message }
    private fun fmt(value: Double) = String.format(Locale.ROOT, "%.2f", value)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
