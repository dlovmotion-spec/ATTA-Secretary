package com.attaproductions.secretary

data class Task(
    val id: Long,
    val title: String,
    val notes: String,
    val dueAt: Long,
    val priority: Int,
    val repeat: String,
    val done: Boolean
)
