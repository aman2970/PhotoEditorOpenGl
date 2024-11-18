package com.example.photoeditoropengl.model

data class VideoSettings(
    val id: Long,
    val componentId: Long,
    val reversed: Boolean,
    val mute: Boolean,
    val inbuiltAudio: Boolean,
    val fps: Int,
    val volume: Float,
    val speed: Float,
    val trimPoints: String,
    val useOriginalPitch: Boolean,
    val syncAudioSpeed: Boolean
)