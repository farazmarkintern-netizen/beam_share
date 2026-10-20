package com.example.beamshare.session

import java.io.InputStream
import java.io.OutputStream

/**
 * File I/O seam for the session engine.
 *
 * SessionConnection never touches Android APIs directly — it only knows these
 * interfaces. The real app plugs in [AndroidFileIo] (ContentResolver + scoped
 * storage); unit tests plug in a plain java.io.File implementation. That is what
 * lets the whole duplex engine run in an ordinary JUnit test with no emulator.
 */

/** A file this device wants to send. */
interface OutgoingFile {
    val name: String
    val size: Long
    val mime: String
    /** Opens a fresh stream over the file's bytes. Caller closes it. */
    fun openInput(): InputStream
}

/** A file currently being received. */
interface IncomingFile {
    val name: String
    val size: Long
    fun output(): OutputStream
    /** Called once all bytes are written and flushed. Returns the final saved path. */
    fun finish(): String
    /** Called if the transfer is cancelled or fails; delete any partial file. */
    fun abort()
}

/** Creates an [IncomingFile] sink for each incoming FILE_OFFER. */
interface IncomingFileFactory {
    fun create(name: String, size: Long, mime: String): IncomingFile
}
