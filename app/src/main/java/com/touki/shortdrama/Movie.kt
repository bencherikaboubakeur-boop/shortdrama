package com.touki.shortdrama

data class Movie(
    val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val posterUrl: String = ""
)
