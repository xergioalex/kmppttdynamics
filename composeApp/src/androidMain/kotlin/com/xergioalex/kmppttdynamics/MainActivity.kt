package com.xergioalex.kmppttdynamics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.SharedPreferencesSettings
import com.xergioalex.kmppttdynamics.settings.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = applicationContext.getSharedPreferences(
            "kmppttdynamics.settings",
            MODE_PRIVATE,
        )
        val container = AppContainer(
            settings = AppSettings(SharedPreferencesSettings(prefs)),
        )

        setContent { App(container) }
    }
}
