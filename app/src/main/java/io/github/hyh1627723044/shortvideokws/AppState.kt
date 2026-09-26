package io.github.hyh1627723044.shortvideokws

import android.content.Context

object AppState {
    @Volatile var listening = false
    @Volatile var starting = false
    @Volatile var status = "尚未开始监听"
    @Volatile var lastAction = "还没有收到口令"
    // Latest cloud utterance outcome. Memory only; may contain the transcript.
    @Volatile var lastHeard = ""
    fun prefs(context: Context) = context.getSharedPreferences("control", Context.MODE_PRIVATE)
    fun requirePrefix(context: Context) = prefs(context).getBoolean("prefix", false)
}
