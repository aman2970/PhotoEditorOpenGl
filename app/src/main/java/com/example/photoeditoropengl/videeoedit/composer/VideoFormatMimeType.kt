package com.example.photoeditoropengl.videeoedit.composer

import android.media.MediaFormat


enum class VideoFormatMimeType(val format: String) {
    HEVC(MediaFormat.MIMETYPE_VIDEO_HEVC),
    AVC(MediaFormat.MIMETYPE_VIDEO_AVC),
    MPEG4(MediaFormat.MIMETYPE_VIDEO_MPEG4),
    H263(MediaFormat.MIMETYPE_VIDEO_H263),
    AUTO("")
}
