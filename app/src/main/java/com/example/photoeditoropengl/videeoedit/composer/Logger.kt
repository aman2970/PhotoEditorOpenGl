package com.example.photoeditoropengl.videeoedit.composer

interface Logger {
    /**
     * Logs a debug message.
     *
     * @param tag     The tag of the message.
     * @param message The message body.
     */
    fun debug(tag: String?, message: String?)

    /**
     * Logs an error message.
     *
     * @param tag     The tag of the message.
     * @param message The message body.
     * @param error   The cause of the error.
     */
    fun error(tag: String?, message: String?, error: Throwable?)

    /**
     * Logs a warning message.
     *
     * @param tag     The tag of the message.
     * @param message The message body.
     */
    fun warning(tag: String?, message: String?)
}
