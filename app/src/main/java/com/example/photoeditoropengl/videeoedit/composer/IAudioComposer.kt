package com.example.photoeditoropengl.videeoedit.composer

internal interface IAudioComposer {
    fun setup()

    fun stepPipeline(): Boolean

    val writtenPresentationTimeUs: Long

    val isFinished: Boolean

    fun release()
}
