package com.example.photoeditoropengl.videeoedit.composer

import android.util.Log

class AndroidLogger : Logger {
    override fun debug(tag: String?, message: String?) {
        Log.d(tag, message!!)
    }

    override fun error(tag: String?, message: String?, error: Throwable?) {
        Log.e(tag, message, error)
    }

    override fun warning(tag: String?, message: String?) {
        Log.w(tag, message!!)
    }
}
