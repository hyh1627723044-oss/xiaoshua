package io.github.hyh1627723044.shortvideokws

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var prefix: Switch
    private lateinit var commentPosition: SeekBar
    private val refresh = object : Runnable {
        override fun run() {
            status.text = "${AppState.status}\n\n无障碍：${if (GestureService.instance != null) "已连接" else "未连接"}\n${AppState.lastAction}"
            prefix.isEnabled = !AppState.listening && !AppState.starting
            commentPosition.isEnabled = !AppState.listening && !AppState.starting
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
        label("小刷", 34f)
        label("说出口令，直接操作。\n离线运行 · 无需账号 · 不播报完成")
        status = label("", 16f)
        fun button(text: String, action: () -> Unit) {
            layout.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
        }
        button("1. 开启无障碍服务") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("2. 开始监听") { startListening() }
        button("停止监听") {
            AppState.listening = false
            stopService(Intent(this, ListeningService::class.java))
        }
        prefix = Switch(this).apply {
            text = "使用“小刷＋口令”（连续说，不用停顿）"
            isChecked = AppState.prefs(this@MainActivity).getBoolean("prefix", false)
            setOnCheckedChangeListener { _, checked ->
                AppState.prefs(this@MainActivity).edit().putBoolean("prefix", checked).apply()
            }
        }
        layout.addView(prefix)
        label("口令\n下一条 / 上一条 / 播放 / 暂停\n点赞点赞 / 查看评论 / 关闭评论\n停止控制（随时可用）")
        val positionLabel = label("评论按钮高度：${AppState.prefs(this).getInt("comment_y", 65)}%")
        commentPosition = SeekBar(this).apply {
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
        label("仅适合抖音普通竖屏视频页。播放和暂停都是点一下；关闭评论执行返回。关键词检测不理解否定句，视频外放也可能触发口令，建议戴耳机或启用“小刷＋口令”。锁屏后需手动重新开始。", 14f)
        setContentView(ScrollView(this).apply { addView(layout) })
        // Respect system bars when targeting Android 15 edge-to-edge.
        layout.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(dp(24), dp(24) + insets.systemWindowInsetTop, dp(24), dp(24) + insets.systemWindowInsetBottom)
            insets
        }
    }
    private fun startListening() {
        if (GestureService.instance == null) {
            Toast.makeText(this, "请先开启小刷无障碍服务", Toast.LENGTH_SHORT).show(); return
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
