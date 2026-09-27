package io.github.hyh1627723044.shortvideokws

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GestureService : AccessibilityService() {
    companion object {
        var instance: GestureService? = null; private set
        private const val CALIBRATION_ATTEMPTS = 4
        private const val MAX_SCAN_NODES = 5000
    }
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private val gate = CommandGate()

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stopListening() }
    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        AppState.calibrating = false
        stopListening()
        super.onDestroy()
    }
    private fun stopListening() {
        AppState.listening = false
        stopService(Intent(this, ListeningService::class.java))
    }
    private fun screen(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    fun execute(request: CommandRequest) {
        val command = request.command
        if (!AppState.listening) return
        if (command == Command.STOP) { stopListening(); return }
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        // Read only the root package, never traverse or serialize the accessibility tree.
        val root = rootInActiveWindow ?: return
        val foreground = root.packageName?.toString()
        @Suppress("DEPRECATION")
        root.recycle()
        if (foreground != ButtonLayout.DOUYIN) {
            AppState.lastAction = "忽略：当前不是抖音"
            return
        }
        val (sw, sh) = screen()
        val w = sw.toFloat()
        val h = sh.toFloat()
        if (w >= h || w <= 0f) { AppState.lastAction = "请使用竖屏"; return }
        if (!gate.accept(request, SystemClock.elapsedRealtime(), AppState.listening, busy)) return

        val builder = GestureDescription.Builder()
        fun tap(x: Float, y: Float, start: Long = 0) {
            builder.addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, start, 50))
        }
        // Saved positions come from calibration or manual adjustment; nothing is looked up here.
        fun tap(spot: ButtonSpot) = tap(w * spot.xPercent / 100f, h * spot.yPercent / 100f)
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
            Command.COMMENTS -> tap(ButtonLayout.comment(this))
            Command.FAVORITE -> tap(ButtonLayout.favorite(this))
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

    // One-off calibration: open Douyin, find the comment and favorite buttons by their accessibility
    // labels, save their positions, then return to 小刷. Commands never scan the tree themselves.
    // Returns an error message, or null once calibration has started.
    fun startCalibration(): String? {
        if (AppState.listening || AppState.starting) return "请先停止监听"
        if (AppState.calibrating) return null
        val launch = packageManager.getLaunchIntentForPackage(ButtonLayout.DOUYIN) ?: return "没有找到抖音，请先安装"
        AppState.calibrating = true
        AppState.calibrationOk = false
        AppState.calibration = "正在打开抖音…请停留在普通竖屏视频页"
        includeAllViews(true)
        try { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: RuntimeException) { finishCalibration(null, null, 0, "无法打开抖音：${e.javaClass.simpleName}"); return null }
        handler.postDelayed({ calibrationAttempt(1) }, 2500)
        return null
    }

    private fun calibrationAttempt(attempt: Int) {
        if (!AppState.calibrating) return
        val scan = scanButtons()
        if ((scan.comment != null && scan.favorite != null) || attempt >= CALIBRATION_ATTEMPTS) {
            finishCalibration(scan.comment, scan.favorite, scan.elapsedMs, scan.error)
        } else {
            AppState.calibration = "正在查找按钮…（第 ${attempt + 1} 次）"
            handler.postDelayed({ calibrationAttempt(attempt + 1) }, 1000)
        }
    }

    private class Scan(val comment: ButtonSpot?, val favorite: ButtonSpot?, val elapsedMs: Long, val error: String?)

    private fun scanButtons(): Scan {
        val start = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow ?: return Scan(null, null, 0, "读取不到当前窗口")
        if (root.packageName?.toString() != ButtonLayout.DOUYIN) return Scan(null, null, 0, "当前不是抖音页面")
        val (w, h) = screen()
        if (w >= h) return Scan(null, null, 0, "请使用竖屏")
        val candidates = ArrayList<NodeCandidate>()
        val stack = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val rect = Rect()
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val node = stack.removeLast()
            visited++
            val label = listOfNotNull(node.contentDescription, node.text).joinToString(" ")
            if (node.isVisibleToUser && ("评论" in label || "收藏" in label)) {
                node.getBoundsInScreen(rect)
                candidates += NodeCandidate(label, rect.left, rect.top, rect.right, rect.bottom)
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(stack::addLast)
        }
        return Scan(ButtonFinder.pick(candidates, "评论", w, h), ButtonFinder.pick(candidates, "收藏", w, h),
            SystemClock.elapsedRealtime() - start, null)
    }

    private fun finishCalibration(comment: ButtonSpot?, favorite: ButtonSpot?, elapsedMs: Long, error: String?) {
        includeAllViews(false)
        if (comment != null || favorite != null) ButtonLayout.saveCalibration(this, comment, favorite)
        AppState.calibrationOk = comment != null && favorite != null
        AppState.calibration = when {
            comment != null && favorite != null -> "校准完成：评论 $comment，收藏 $favorite（读取用时 $elapsedMs ms）"
            comment != null -> "只找到评论按钮（$comment），收藏请手动调整"
            favorite != null -> "只找到收藏按钮（$favorite），评论请手动调整"
            else -> "没有找到按钮${error?.let { "：$it" } ?: "：抖音可能没有提供按钮描述"}，请手动调整位置"
        }
        AppState.calibrating = false
        // Accessibility services may start activities from the background, so return to 小刷 to show the result.
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (e: RuntimeException) { Unit }
    }

    // Some apps hide their buttons from the default accessibility view; include them only while scanning.
    private fun includeAllViews(enabled: Boolean) {
        val info = serviceInfo ?: return
        val flag = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.flags = if (enabled) info.flags or flag else info.flags and flag.inv()
        serviceInfo = info
    }
}
