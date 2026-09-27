package io.github.hyh1627723044.shortvideokws

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var intentJev: RadioButton
    private lateinit var intentStrict: RadioButton
    private lateinit var cloudButtons: List<Button>
    private lateinit var commandsHint: TextView
    private val lockable = mutableListOf<View>()
    private val refresh = object : Runnable {
        override fun run() {
            val heard = AppState.lastHeard.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()
            status.text = "${AppState.status}\n\n无障碍：${if (GestureService.instance != null) "已连接" else "未连接"}\n${AppState.lastAction}$heard"
            // Settings that change engine instances are locked while listening.
            val idle = !AppState.listening && !AppState.starting
            lockable.forEach { it.isEnabled = idle }
            updateCloudControls(idle)
            handler.postDelayed(this, 500)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            setBackgroundColor(Color.rgb(247, 246, 240))
        }
        fun label(text: String, size: Float = 16f): TextView = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.rgb(28, 48, 42))
            setPadding(0, dp(8), 0, dp(12)); layout.addView(this)
        }
        fun button(text: String, action: () -> Unit): Button =
            Button(this).apply { this.text = text; setOnClickListener { action() }; layout.addView(this) }
        fun radio(group: RadioGroup, text: String): RadioButton =
            RadioButton(this).apply { this.text = text; id = View.generateViewId(); group.addView(this) }

        label("小刷", 34f)
        label("说出口令，直接操作。\n默认离线运行 · 云端识别可选 · 不播报完成")
        status = label("", 16f)
        button("1. 开启无障碍服务") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("2. 开始监听") { startListening() }
        button("停止监听") {
            AppState.listening = false
            stopService(Intent(this, ListeningService::class.java))
        }

        label("语音识别", 18f)
        val recognition = RadioGroup(this)
        val local = radio(recognition, "本地关键词（离线、快速）")
        val cloud = radio(recognition, "字节云 ASR（准确、需要联网）")
        recognition.check(if (CloudSettings.recognitionMode(this) == RecognitionMode.CLOUD) cloud.id else local.id)
        recognition.setOnCheckedChangeListener { _, checked ->
            if (checked == cloud.id) confirmCloud { accepted ->
                if (accepted) CloudSettings.setRecognitionMode(this, RecognitionMode.CLOUD) else recognition.check(local.id)
                updateCloudControls(true)
            } else {
                CloudSettings.setRecognitionMode(this, RecognitionMode.LOCAL)
                updateCloudControls(true)
            }
        }
        layout.addView(recognition)

        label("行为判断", 18f)
        val intentGroup = RadioGroup(this)
        intentStrict = radio(intentGroup, "严格口令")
        intentJev = radio(intentGroup, "JEV 智能判断（理解自然表达）")
        intentGroup.check(if (CloudSettings.intentMode(this) == IntentMode.JEV) intentJev.id else intentStrict.id)
        intentGroup.setOnCheckedChangeListener { _, checked ->
            CloudSettings.setIntentMode(this, if (checked == intentJev.id) IntentMode.JEV else IntentMode.STRICT)
            updateCloudControls(true)
        }
        layout.addView(intentGroup)
        cloudButtons = listOf(
            button("字节 ASR 设置") { openSettings(CloudSettingsActivity.SECTION_ASR) },
            button("JEV 设置") { openSettings(CloudSettingsActivity.SECTION_JEV) },
            button("高级设置") { openSettings(CloudSettingsActivity.SECTION_ADVANCED) },
        )

        val prefix = Switch(this).apply {
            text = "使用“小刷＋口令”（连续说，不用停顿）"
            isChecked = AppState.requirePrefix(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                AppState.prefs(this@MainActivity).edit().putBoolean("prefix", checked).apply()
            }
        }
        layout.addView(prefix)
        commandsHint = label("")
        val positionLabel = label("评论按钮高度：${AppState.prefs(this).getInt("comment_y", 65)}%")
        val commentPosition = SeekBar(this).apply {
            min = 35; max = 85; progress = AppState.prefs(this@MainActivity).getInt("comment_y", 65)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) AppState.prefs(this@MainActivity).edit().putInt("comment_y", value).apply()
                    positionLabel.text = "评论按钮高度：$value%"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        layout.addView(commentPosition)
        label("仅适合抖音普通竖屏视频页。播放和暂停都是点一下；关闭评论执行返回。本地关键词不理解否定句；云端 VAD 也无法区分你和视频里的人声。视频外放可能误触，建议戴耳机或启用“小刷＋口令”。锁屏后需手动重新开始。", 14f)
        lockable += listOf(prefix, commentPosition, local, cloud)
        setContentView(ScrollView(this).apply { addView(layout) })
        // Respect system bars when targeting Android 15 edge-to-edge.
        layout.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(24), dp(24) + insets.systemWindowInsetTop, dp(24), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
        updateCloudControls(true)
    }
    private fun updateCloudControls(idle: Boolean) {
        val cloud = CloudSettings.recognitionMode(this) == RecognitionMode.CLOUD
        intentStrict.isEnabled = idle && cloud
        intentJev.isEnabled = idle && cloud
        cloudButtons.forEach { it.isEnabled = cloud }
        commandsHint.text = when {
            !cloud -> "口令\n下一条 / 上一条 / 播放 / 暂停\n点赞点赞 / 查看评论 / 关闭评论\n停止控制（随时可用，离线识别）"
            CloudSettings.intentMode(this) == IntentMode.JEV -> "可以说自然表达，例如“换一个”“帮我点个赞”。\n停止控制始终由本地离线识别。"
            else -> "口令（整句匹配）\n下一条 / 下一个 / 上一条 / 播放 / 暂停\n点赞 / 点个赞 / 查看评论 / 关闭评论\n停止控制（随时可用，离线识别）"
        }
    }
    // The first switch to cloud explains where audio goes; the choice is remembered once accepted.
    private fun confirmCloud(done: (Boolean) -> Unit) {
        if (CloudSettings.noticeAccepted(this)) { done(true); return }
        val host = Endpoints.parse(CloudSettings.asrUrl(this))?.host ?: "字节 ASR 服务"
        var answered = false
        fun answer(accepted: Boolean) { if (!answered) { answered = true; done(accepted) } }
        AlertDialog.Builder(this)
            .setTitle("启用云端识别？")
            .setMessage("监听时，本地 VAD 切出的每一句话会上传到 $host 转写。静音时不上传，音频不保存。\n\n" +
                "选择 JEV 智能判断时，转写文字还会发送到 JEV 服务。二者都是第三方服务，由你自己的账号计费。")
            .setPositiveButton("同意并启用") { _, _ -> CloudSettings.acceptNotice(this); answer(true) }
            .setNegativeButton("取消") { _, _ -> answer(false) }
            .setOnCancelListener { answer(false) }
            .show()
    }
    private fun openSettings(section: String) {
        startActivity(Intent(this, CloudSettingsActivity::class.java).putExtra(CloudSettingsActivity.EXTRA_SECTION, section))
    }
    private fun startListening() {
        if (GestureService.instance == null) {
            Toast.makeText(this, "请先开启小刷无障碍服务", Toast.LENGTH_SHORT).show(); return
        }
        if (CloudSettings.recognitionMode(this) == RecognitionMode.CLOUD) {
            val result = CloudSettings.profile(this, AppState.requirePrefix(this))
            if (result is CloudSettings.ProfileResult.Missing) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show(); return
            }
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2); return
        }
        launchService()
    }
    private fun launchService() {
        try { startForegroundService(Intent(this, ListeningService::class.java)) }
        catch (e: RuntimeException) { AppState.status = "无法启动监听：${e.javaClass.simpleName}" }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        else if (requestCode == 2) launchService()
        else AppState.status = "需要麦克风权限才能监听"
    }
    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
