package com.harmoniamusic.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 窗口创建即由系统绘制启动页（图标），首帧就绪前保持，避免深色空白屏
        val splash = installSplashScreen()
        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        setContent {
            TvApp(onReady = { ready = true })
        }
    }
}
