package io.github.hyh1627723044.shortvideokws

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

// Tap targets as percentages of the real screen size.
data class ButtonSpot(val xPercent: Int, val yPercent: Int) {
    override fun toString() = "$xPercent%, $yPercent%"
}

// A visible node whose text or description mentions a button name, in screen pixels.
data class NodeCandidate(val label: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val area get() = (right - left).toLong() * (bottom - top)
}

object ButtonFinder {
    // Douyin's action buttons sit in a narrow rail on the right. Captions and comment text on the
    // left are excluded, as are oversized containers; the smallest matching node wins.
    fun pick(candidates: List<NodeCandidate>, keyword: String, width: Int, height: Int): ButtonSpot? {
        if (width <= 0 || height <= 0) return null
        return candidates.asSequence()
            .filter { keyword in it.label }
            .filter { it.right > it.left && it.bottom > it.top }
            .filter { it.centerX > width * 0.75 && it.centerY > height * 0.25 && it.centerY < height * 0.95 }
            .filter { it.right - it.left < width * 0.25 && it.bottom - it.top < height * 0.2 }
            .minByOrNull { it.area }
            ?.let { ButtonSpot((it.centerX * 100 / width).coerceIn(1, 99), (it.centerY * 100 / height).coerceIn(1, 99)) }
    }
}

// Calibrated or manually adjusted positions persist until the user recalibrates.
object ButtonLayout {
    const val DOUYIN = "com.ss.android.ugc.aweme"
    val DEFAULT_COMMENT = ButtonSpot(92, 65)
    val DEFAULT_FAVORITE = ButtonSpot(92, 73)
    private const val COMMENT_X = "comment_x"
    private const val COMMENT_Y = "comment_y"
    private const val FAVORITE_X = "favorite_x"
    private const val FAVORITE_Y = "favorite_y"
    private const val CALIBRATED_VERSION = "calibrated_douyin_version"

    private fun prefs(c: Context) = AppState.prefs(c)

    fun comment(c: Context) = prefs(c).run {
        ButtonSpot(getInt(COMMENT_X, DEFAULT_COMMENT.xPercent), getInt(COMMENT_Y, DEFAULT_COMMENT.yPercent))
    }
    fun favorite(c: Context) = prefs(c).run {
        ButtonSpot(getInt(FAVORITE_X, DEFAULT_FAVORITE.xPercent), getInt(FAVORITE_Y, DEFAULT_FAVORITE.yPercent))
    }
    fun setCommentHeight(c: Context, y: Int) = prefs(c).edit().putInt(COMMENT_Y, y).apply()
    fun setFavoriteHeight(c: Context, y: Int) = prefs(c).edit().putInt(FAVORITE_Y, y).apply()

    fun saveCalibration(c: Context, comment: ButtonSpot?, favorite: ButtonSpot?) {
        prefs(c).edit().apply {
            comment?.let { putInt(COMMENT_X, it.xPercent); putInt(COMMENT_Y, it.yPercent) }
            favorite?.let { putInt(FAVORITE_X, it.xPercent); putInt(FAVORITE_Y, it.yPercent) }
            douyinVersion(c)?.let { putLong(CALIBRATED_VERSION, it) }
        }.apply()
    }

    fun calibrated(c: Context) = prefs(c).contains(CALIBRATED_VERSION)

    // True when Douyin was updated since the last calibration; layouts may have moved.
    fun needsRecalibration(c: Context): Boolean {
        val saved = prefs(c).getLong(CALIBRATED_VERSION, -1)
        val current = douyinVersion(c) ?: return false
        return saved != -1L && saved != current
    }

    fun douyinVersion(c: Context): Long? = try {
        val info = c.packageManager.getPackageInfo(DOUYIN, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    } catch (e: PackageManager.NameNotFoundException) { null }
}
