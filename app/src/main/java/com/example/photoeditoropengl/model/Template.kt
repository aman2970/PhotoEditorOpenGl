package com.example.photoeditoropengl.model

data class Template(
    val id: Long,
    val type: String,
    val name: String,
    val duration: Float,
    val createdAt: String,
    val updatedAt: String,
    val thumbnailPath: String,
    val thumbTime: Float,
    val ratio: String
)