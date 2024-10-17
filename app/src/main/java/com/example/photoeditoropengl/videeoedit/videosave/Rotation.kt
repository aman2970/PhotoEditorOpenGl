package com.example.photoeditoropengl.videeoedit.videosave

enum class Rotation(val rotation: Int) {
    NORMAL(0),
    ROTATION_90(90),
    ROTATION_180(180),
    ROTATION_270(270);

    companion object {
        fun fromInt(rotate: Int): Rotation {
            return values().find { it.rotation == rotate } ?: NORMAL
        }
    }

}