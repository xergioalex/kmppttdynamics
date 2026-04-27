package com.xergioalex.kmptodoapp.platform

import com.xergioalex.kmptodoapp.domain.Task
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosTaskSharer : TaskSharer {
    override fun share(task: Task) {
        val activity = UIActivityViewController(
            activityItems = listOf(task.toShareText()),
            applicationActivities = null,
        )
        UIApplication.sharedApplication.keyWindow
            ?.rootViewController
            ?.presentViewController(activity, animated = true, completion = null)
    }
}
