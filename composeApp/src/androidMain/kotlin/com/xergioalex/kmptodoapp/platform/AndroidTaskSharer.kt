package com.xergioalex.kmptodoapp.platform

import android.content.Context
import android.content.Intent
import com.xergioalex.kmptodoapp.domain.Task

class AndroidTaskSharer(private val context: Context) : TaskSharer {
    override fun share(task: Task) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, task.toShareText())
        }
        val chooser = Intent.createChooser(send, task.title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
