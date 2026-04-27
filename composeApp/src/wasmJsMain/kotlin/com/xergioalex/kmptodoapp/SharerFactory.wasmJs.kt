package com.xergioalex.kmptodoapp

import com.xergioalex.kmptodoapp.platform.TaskSharer
import com.xergioalex.kmptodoapp.platform.WasmTaskSharer

internal actual fun createTaskSharer(): TaskSharer = WasmTaskSharer()
