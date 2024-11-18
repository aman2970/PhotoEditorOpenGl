package com.example.photoeditoropengl.model

import com.example.photoeditoropengl.enum.ComponentType

data class Component(
    val id: Long,
    val templateId: Long,
    val imageId: Long,
    val type: ComponentType,
    val posX: Float,
    val posY: Float,
    val width: Float,
    val height: Float,
    val flipped: Boolean,
    val rotation: Int,
    val opacity: Float,
    val duration: Float,
    val startTime: Float,
    val softDelete: Boolean
)