package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task

interface TaskSharer {
    fun share(task: Task)
}
