package io.github.hyh1627723044.shortvideokws

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent

class GestureService : AccessibilityService() {
    companion object { var instance: GestureService? = null; private set }
    private var busy = false
    private val gate = CommandGate()

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stopListening() }
    override fun onDestroy() {
        if (instance === this) instance = null
        stopListening()
        super.onDestroy()
    }
    private fun stopListening() {
        AppState.listening = false
        stopService(Intent(this, ListeningService::class.java))
    }

    fun execute(command: Command, detectedAt: Long) {
        if (!AppState.listening) return
        if (command == Command.STOP) { stopListening(); return }
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        // Read only the root package, never traverse or serialize the accessibility tree.
        val root = rootInActiveWindow ?: return
        val foreground = root.packageName?.toString()
        @Suppress("DEPRECATION")
        root.recycle()
        if (foreground != "com.ss.android.ugc.aweme") {
            AppState.lastAction = "忽略：当前不是抖音"
            return
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        if (w >= h || w <= 0f) { AppState.lastAction = "请使用竖屏"; return }
        if (!gate.accept(command, detectedAt, SystemClock.elapsedRealtime(), AppState.listening, busy)) return

        val builder = GestureDescription.Builder()
        fun tap(x: Float, y: Float, start: Long = 0) {
            builder.addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, start, 50))
        }
        when (command) {
            Command.NEXT, Command.PREVIOUS -> {
                val up = command == Command.NEXT
                val path = Path().apply {
                    moveTo(w * .48f, h * if (up) .75f else .30f)
                    lineTo(w * .48f, h * if (up) .30f else .75f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0, 160))
            }
            Command.PLAY, Command.PAUSE -> tap(w * .5f, h * .45f)
            Command.LIKE -> { tap(w * .5f, h * .45f); tap(w * .5f, h * .45f, 130) }
            Command.COMMENTS -> tap(w * .92f, h * (AppState.prefs(this).getInt("comment_y", 65) / 100f))
            Command.CLOSE_COMMENTS -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                AppState.lastAction = "已提交返回操作"
                return
            }
            Command.STOP -> return
        }
        busy = true
        val accepted = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                busy = false
                AppState.lastAction = "${command.phrase} · 手势完成（未复查页面）"
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                busy = false
                AppState.lastAction = "${command.phrase} · 手势被取消"
            }
        }, null)
        if (!accepted) { busy = false; AppState.lastAction = "系统拒绝了手势" }
    }
}
