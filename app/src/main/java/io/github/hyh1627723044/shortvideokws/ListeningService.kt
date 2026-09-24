package io.github.hyh1627723044.shortvideokws

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import java.util.concurrent.Executors

class ListeningService : Service() {
    companion object {
        const val STOP = "stop"
        private const val CHANNEL = "listening"
        // Serialize rapid stop/start across service instances until the old recorder is released.
        private val worker = Executors.newSingleThreadExecutor()
    }
    private val handler = Handler(Looper.getMainLooper())
    private var engine: KeywordEngine? = null
    private var generation = 0
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { AppState.listening = false; stopSelf() }
    }
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "语音监听", NotificationManager.IMPORTANCE_LOW))
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(screenReceiver, filter)
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) { AppState.listening = false; stopSelf(); return START_NOT_STICKY }
        if (engine != null) return START_NOT_STICKY
        val stop = PendingIntent.getService(this, 1, Intent(this, ListeningService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("小刷正在监听").setContentText("说“停止控制”或点停止结束；锁屏自动停止")
            .setOngoing(true).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, notification)
        val current = ++generation
        val detector = KeywordEngine(this)
        engine = detector
        AppState.starting = true
        AppState.status = "正在加载本地关键词模型…"
        worker.execute {
            try {
                detector.run(onReady = {
                    handler.post {
                        if (generation == current) {
                            AppState.starting = false
                            AppState.listening = true
                            AppState.status = "正在监听 · 请切到抖音竖屏视频页"
                        }
                    }
                }, onKeyword = { keyword ->
                    val time = SystemClock.elapsedRealtime()
                    handler.post {
                        if (generation == current && AppState.listening) {
                            val command = Command.fromKeyword(keyword)
                            if (command == Command.STOP) { AppState.listening = false; stopSelf() }
                            else if (command != null) GestureService.instance?.execute(command, time)
                        }
                    }
                })
            } catch (e: Exception) {
                reportFailure(current, "监听失败：${e.message ?: e.javaClass.simpleName}")
            } catch (e: LinkageError) {
                reportFailure(current, "本机无法加载语音 SDK：${e.javaClass.simpleName}")
            }
        }
        return START_NOT_STICKY
    }
    private fun reportFailure(current: Int, message: String) {
        handler.post {
            if (generation == current) { AppState.status = message; stopSelf() }
        }
    }
    override fun onDestroy() {
        generation++
        AppState.listening = false
        AppState.starting = false
        if (!AppState.status.startsWith("监听失败") && !AppState.status.startsWith("本机无法")) AppState.status = "监听已停止"
        engine?.stop()
        engine = null
        unregisterReceiver(screenReceiver)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
