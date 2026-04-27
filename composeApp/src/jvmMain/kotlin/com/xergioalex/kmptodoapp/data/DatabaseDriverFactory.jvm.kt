package com.xergioalex.kmptodoapp.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.xergioalex.kmptodoapp.db.TodoDatabase
import java.io.File
import java.util.Properties

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dataDir = File(System.getProperty("user.home"), ".kmptodoapp").apply { mkdirs() }
        val dbFile = File(dataDir, "kmptodoapp.db")
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties(),
        )
        if (!dbFile.exists() || dbFile.length() == 0L) {
            TodoDatabase.Schema.create(driver)
        }
        return driver
    }
}
