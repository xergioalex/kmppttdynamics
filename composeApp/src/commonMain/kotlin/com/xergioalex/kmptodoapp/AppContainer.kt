package com.xergioalex.kmptodoapp

import com.xergioalex.kmptodoapp.domain.TaskRepository
import com.xergioalex.kmptodoapp.platform.TaskSharer
import com.xergioalex.kmptodoapp.settings.AppSettings

data class AppContainer(
    val tasks: TaskRepository,
    val settings: AppSettings,
    val sharer: TaskSharer,
)
