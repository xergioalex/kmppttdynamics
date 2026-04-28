package com.xergioalex.kmppttdynamics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Container is owned by PttApplication so it survives activity
        // recreation (rotation, theme change, Compose hot reload). This
        // is what stops the global presence tracker from being spawned
        // a second time and inflating the lobby count.
        val container = (application as PttApplication).container

        setContent { App(container) }
    }
}
