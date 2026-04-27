package com.xergioalex.kmptodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.SharedPreferencesSettings
import com.xergioalex.kmptodoapp.data.DatabaseDriverFactory
import com.xergioalex.kmptodoapp.data.SqlTaskRepository
import com.xergioalex.kmptodoapp.platform.AndroidTaskSharer
import com.xergioalex.kmptodoapp.settings.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = applicationContext.getSharedPreferences("kmptodoapp.settings", MODE_PRIVATE)
        val container = AppContainer(
            tasks = SqlTaskRepository(DatabaseDriverFactory(applicationContext)),
            settings = AppSettings(SharedPreferencesSettings(prefs)),
            sharer = AndroidTaskSharer(applicationContext),
        )

        setContent {
            App(container)
        }
    }
}
