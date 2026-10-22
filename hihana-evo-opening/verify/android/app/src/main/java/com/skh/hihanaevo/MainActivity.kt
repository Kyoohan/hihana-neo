package com.skh.hihanaevo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Minimal host so the module is a real, installable app. The harness tests do not use it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EvoOpening(onFinish = { finish() }) }
    }
}
