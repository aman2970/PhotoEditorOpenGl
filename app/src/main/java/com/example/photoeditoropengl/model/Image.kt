package com.example.photoeditoropengl.model

import com.example.photoeditoropengl.enum.ImageType

data class Image(
    val id: Long,
    val type: ImageType,
    val colorValue: String,
    val contentPath: String,
    val cropPoints: String,
    val templateId: Long
)