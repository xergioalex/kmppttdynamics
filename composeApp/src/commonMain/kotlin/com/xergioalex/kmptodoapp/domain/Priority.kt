package com.xergioalex.kmptodoapp.domain

enum class Priority(val storedValue: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        fun fromStoredValue(value: Int): Priority = entries.firstOrNull { it.storedValue == value } ?: MEDIUM
    }
}
