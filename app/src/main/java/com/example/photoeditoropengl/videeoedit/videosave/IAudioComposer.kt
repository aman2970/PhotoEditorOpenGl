package com.example.photoeditoropengl.videeoedit.videosave

interface IAudioComposer {

    fun setup()

    fun stepPipeline(): Boolean

    fun getWrittenPresentationTimeUs(): Long

    fun isFinished(): Boolean

    fun release()
}