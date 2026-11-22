package com.luma.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

/** 调试用：`am start ... --ei center 1` 可以直接把中间卡片翻到指定页。 */
object StartPage {
    var center: Int = 0
    var stack: Int = 0
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartPage.center = intent?.getIntExtra("center", 0) ?: 0
        StartPage.stack = intent?.getIntExtra("stack", 0) ?: 0
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LumaHome()
        }
    }
}
