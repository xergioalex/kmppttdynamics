package com.xergioalex.kmptodoapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform