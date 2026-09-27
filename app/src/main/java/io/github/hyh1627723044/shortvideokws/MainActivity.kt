package io.github.hyh1627723044.shortvideokws

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.hyh1627723044.shortvideokws.ui.XiaoshuaApp
import io.github.hyh1627723044.shortvideokws.ui.XiaoshuaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { XiaoshuaTheme { XiaoshuaApp() } }
    }
}
