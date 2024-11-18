package com.example.photoeditoropengl.videeoedit.composer

enum class Rotation(val rotation: Int) {
    NORMAL(0),
    ROTATION_90(90),
    ROTATION_180(180),
    ROTATION_270(270);

    companion object {
        fun fromInt(rotate: Int): Rotation {
            var calcRotate = rotate
            if (calcRotate > 360) {
                calcRotate -= 360
            }

            for (rotation in entries) {
                if (calcRotate == rotation.rotation) return rotation
            }

            return NORMAL
        }
    }
}
