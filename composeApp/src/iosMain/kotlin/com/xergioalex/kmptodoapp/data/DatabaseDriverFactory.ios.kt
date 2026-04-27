package com.xergioalex.kmptodoapp.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.xergioalex.kmptodoapp.db.TodoDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(TodoDatabase.Schema, "kmptodoapp.db")
}
