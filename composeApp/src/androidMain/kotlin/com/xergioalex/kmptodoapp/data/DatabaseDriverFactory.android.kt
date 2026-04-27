package com.xergioalex.kmptodoapp.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.xergioalex.kmptodoapp.db.TodoDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(TodoDatabase.Schema, context, DB_NAME)

    companion object {
        private const val DB_NAME = "kmptodoapp.db"
    }
}
