package com.xergioalex.kmptodoapp

import com.xergioalex.kmptodoapp.platform.JsTaskSharer
import com.xergioalex.kmptodoapp.platform.TaskSharer

internal actual fun createTaskSharer(): TaskSharer = JsTaskSharer()
