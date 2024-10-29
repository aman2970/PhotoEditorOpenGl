package com.example.photoeditoropengl.videeoedit.videosave

object TimeScaleHelper {
    fun getScaledTime(originalTime: Long, timeScale: Int): Long {
        return originalTime / timeScale
    }

    fun getScaledDuration(originalDuration: Long, timeScale: Int): Long {
        return originalDuration / timeScale
    }

    fun getScaledFrameRate(originalFrameRate: Int, timeScale: Int): Int {
        return originalFrameRate * timeScale
    }
}