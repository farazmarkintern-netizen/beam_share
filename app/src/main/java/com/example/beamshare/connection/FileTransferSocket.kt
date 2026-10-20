package com.example.beamshare.connection

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

class FileTransferSocket(private val context: Context) {

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    /** The remote device's friendly name, received during the TCP handshake. */
    private val _remoteDeviceName = MutableStateFlow("")
    val remoteDeviceName: StateFlow<String> = _remoteDeviceName.asStateFlow()

    /** The IP address of the last device that connected to us (populated on the receiver/group-owner side). */
    private val _peerAddress = MutableStateFlow("")
    val peerAddress: StateFlow<String> = _peerAddress.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    companion object {
        private const val TAG = "FileTransferSocket"
        private const val BUFFER_SIZE = 8192
        private const val HEADER_MAGIC = 0x4245414D // "BEAM"
        private const val SOCKET_TIMEOUT = 30_000
        // Code verification result bytes
        private const val CODE_OK: Byte = 1
        private const val CODE_FAIL: Byte = 0
    }

    suspend fun startReceiving(port: Int, expectedCode: String): Unit = withContext(Dispatchers.IO) {
        try {
            // SO_REUSEADDR must be set BEFORE bind
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 60_000
            }
            var bound = false
            var lastEx: Exception? = null
            repeat(3) { attempt ->
                if (!bound) {
                    try {
                        serverSocket!!.bind(InetSocketAddress(port))
                        bound = true
                        Log.d(TAG, "Server socket bound on port $port (attempt ${attempt + 1})")
                    } catch (e: Exception) {
                        lastEx = e
                        Log.w(TAG, "Bind attempt ${attempt + 1} failed: ${e.message}")
                        if (attempt < 2) Thread.sleep(500)
                    }
                }
            }
            if (!bound) throw lastEx!!

            _transferState.value = TransferState.WaitingForReceiver

            val socket = serverSocket!!.accept()
            socket.soTimeout = SOCKET_TIMEOUT
            val peerIp = socket.inetAddress.hostAddress ?: ""
            _peerAddress.value = peerIp
            Log.d(TAG, "Sender connected from $peerIp")

            // ── Code handshake ──────────────────────────────────────────────
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val codeLength = input.readInt()
            val codeBytes = ByteArray(codeLength)
            input.readFully(codeBytes)
            val receivedCode = String(codeBytes, Charsets.UTF_8).trim()

            Log.i(TAG, "═══ CODE HANDSHAKE (RECEIVER) ═══")
            Log.i(TAG, "  received = '$receivedCode'  (length=${receivedCode.length})")
            Log.i(TAG, "  expected = '${expectedCode.trim()}'  (length=${expectedCode.trim().length})")
            Log.i(TAG, "  match    = ${receivedCode == expectedCode.trim()}")

            if (receivedCode != expectedCode.trim()) {
                Log.e(TAG, "  !! CODE MISMATCH — rejecting connection !!")
                output.writeByte(CODE_FAIL.toInt())
                output.flush()
                _transferState.value = TransferState.Failed("Wrong pairing code — connection rejected")
                socket.close()
                return@withContext
            }

            output.writeByte(CODE_OK.toInt())
            output.flush()
            Log.d(TAG, "Code verified OK")

            // ── Read sender's device name ─────────────────────────────────
            val nameLen = input.readInt()
            if (nameLen in 1..256) {
                val nameBytes = ByteArray(nameLen)
                input.readFully(nameBytes)
                val senderName = String(nameBytes, Charsets.UTF_8)
                _remoteDeviceName.value = senderName
                Log.d(TAG, "Sender device name: $senderName")
            }

            receiveFiles(socket, input, output)
        } catch (e: Exception) {
            Log.e(TAG, "Receiving failed", e)
            _transferState.value = TransferState.Failed(e.message ?: "Receive failed")
        } finally {
            serverSocket?.close()
        }
    }

    suspend fun startSending(
        hostAddress: String,
        port: Int,
        pairingCode: String,
        fileUris: List<Uri>,
        fileNames: List<String>,
        fileSizes: List<Long>
    ): Unit = withContext(Dispatchers.IO) {
        try {
            clientSocket = Socket().apply {
                connect(InetSocketAddress(hostAddress, port), SOCKET_TIMEOUT)
                soTimeout = SOCKET_TIMEOUT
            }
            Log.d(TAG, "Connected to receiver at $hostAddress:$port")

            // ── Code handshake ──────────────────────────────────────────────
            val output = DataOutputStream(BufferedOutputStream(clientSocket!!.getOutputStream()))
            val input  = DataInputStream(BufferedInputStream(clientSocket!!.getInputStream()))

            val codeBytes = pairingCode.trim().toByteArray(Charsets.UTF_8)
            Log.i(TAG, "═══ SENDING PAIRING CODE ═══")
            Log.i(TAG, "  code   = '${pairingCode.trim()}'")
            Log.i(TAG, "  length = ${codeBytes.size}")
            output.writeInt(codeBytes.size)
            output.write(codeBytes)
            output.flush()

            val ack = input.readByte()
            Log.i(TAG, "  handshake ack = $ack  (1=OK, 0=FAIL)")
            if (ack != CODE_OK) {
                _transferState.value = TransferState.Failed("Wrong pairing code — receiver rejected connection")
                clientSocket?.close()
                return@withContext
            }
            Log.d(TAG, "Handshake accepted by receiver")

            // ── Send this device's friendly name ──────────────────────────
            val myName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val myNameBytes = myName.toByteArray(Charsets.UTF_8)
            output.writeInt(myNameBytes.size)
            output.write(myNameBytes)
            output.flush()
            Log.d(TAG, "Sent device name: $myName")

            sendFiles(clientSocket!!, input, output, fileUris, fileNames, fileSizes)
        } catch (e: Exception) {
            Log.e(TAG, "Sending failed", e)
            _transferState.value = TransferState.Failed(e.message ?: "Send failed")
        } finally {
            clientSocket?.close()
        }
    }

    private suspend fun sendFiles(
        socket: Socket,
        input: DataInputStream,
        output: DataOutputStream,
        fileUris: List<Uri>,
        fileNames: List<String>,
        fileSizes: List<Long>
    ) = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver

        var successCount = 0
        var failedCount = 0
        var totalSent = 0L

        output.writeInt(HEADER_MAGIC)
        output.writeInt(fileUris.size)
        output.flush()

        for (i in fileUris.indices) {
            if (!isActive) break

            val uri = fileUris[i]
            val name = fileNames[i]
            val size = fileSizes[i]

            try {
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                output.writeInt(nameBytes.size)
                output.write(nameBytes)
                output.writeLong(size)
                output.flush()

                val inputStream = resolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file: $name")

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesSent = 0L
                val startTime = System.currentTimeMillis()

                inputStream.use { fis ->
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesSent += bytesRead
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 0) (bytesSent * 1000 / elapsed) else 0
                        _transferState.value = TransferState.Transferring(
                            fileName = name,
                            currentBytes = bytesSent,
                            totalBytes = size,
                            fileIndex = i + 1,
                            totalFiles = fileUris.size,
                            speedBytesPerSec = speed
                        )
                    }
                }
                output.flush()

                val ack = input.readByte()
                if (ack == 1.toByte()) {
                    successCount++
                    totalSent += size
                } else {
                    failedCount++
                }
                Log.d(TAG, "Sent file $name ($bytesSent bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file: $name", e)
                failedCount++
            }
        }

        _transferState.value = TransferState.Completed(
            successCount = successCount,
            failedCount = failedCount,
            totalSize = totalSent,
            savedFiles = emptyList()
        )
    }

    private suspend fun receiveFiles(
        socket: Socket,
        input: DataInputStream,
        output: DataOutputStream
    ) = withContext(Dispatchers.IO) {
        var successCount = 0
        var failedCount = 0
        var totalReceived = 0L
        val savedFiles = mutableListOf<Pair<String, Long>>()

        val magic = input.readInt()
        if (magic != HEADER_MAGIC) {
            _transferState.value = TransferState.Failed("Invalid transfer protocol")
            return@withContext
        }

        val totalFiles = input.readInt()
        Log.d(TAG, "Expecting $totalFiles files")

        val beamShareRoot = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BeamShare"
        ).apply { mkdirs() }

        for (i in 0 until totalFiles) {
            if (!isActive) break
            try {
                val nameLength = input.readInt()
                val nameBytes = ByteArray(nameLength)
                input.readFully(nameBytes)
                val fileName = String(nameBytes, Charsets.UTF_8)
                val fileSize = input.readLong()

                Log.d(TAG, "Receiving file: $fileName ($fileSize bytes)")

                val subFolder = getSubFolder(fileName)
                val receiveDir = File(beamShareRoot, subFolder).apply { mkdirs() }
                val outFile = getUniqueFile(receiveDir, fileName)

                FileOutputStream(outFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesReceived = 0L
                    val startTime = System.currentTimeMillis()

                    while (bytesReceived < fileSize) {
                        val remaining = (fileSize - bytesReceived).coerceAtMost(BUFFER_SIZE.toLong()).toInt()
                        val bytesRead = input.read(buffer, 0, remaining)
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        bytesReceived += bytesRead
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 0) (bytesReceived * 1000 / elapsed) else 0
                        _transferState.value = TransferState.Transferring(
                            fileName = fileName,
                            currentBytes = bytesReceived,
                            totalBytes = fileSize,
                            fileIndex = i + 1,
                            totalFiles = totalFiles,
                            speedBytesPerSec = speed
                        )
                    }
                    fos.flush()
                }

                MediaScannerConnection.scanFile(
                    context, arrayOf(outFile.absolutePath), null
                ) { path, uri -> Log.d(TAG, "MediaStore scanned: $path → $uri") }

                output.writeByte(1)
                output.flush()
                successCount++
                totalReceived += fileSize
                savedFiles.add(outFile.absolutePath to fileSize)
                Log.d(TAG, "Received: $fileName → ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to receive file", e)
                output.writeByte(0)
                output.flush()
                failedCount++
            }
        }

        _transferState.value = TransferState.Completed(
            successCount = successCount,
            failedCount = failedCount,
            totalSize = totalReceived,
            savedFiles = savedFiles
        )
    }

    private fun getSubFolder(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg" -> "Images"
            "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "ts"   -> "Videos"
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "opus"         -> "Music"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "rtf", "odt", "ods", "odp"                  -> "Documents"
            "apk", "xapk", "aab"                                       -> "Apps"
            "vcf"                                                       -> "Contacts"
            "zip", "rar", "7z", "tar", "gz"                            -> "Archives"
            else                                                        -> "Other"
        }
    }

    private fun getUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dotIndex = name.lastIndexOf('.')
        val baseName = if (dotIndex > 0) name.substring(0, dotIndex) else name
        val extension = if (dotIndex > 0) name.substring(dotIndex) else ""
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_$counter$extension")
            counter++
        }
        return file
    }

    /**
     * Reset transfer state to Idle without closing sockets or clearing device name.
     * Used after a transfer completes in a persistent session so the service
     * can start a new send/receive round.
     */
    fun resetState() {
        _transferState.value = TransferState.Idle
    }

    /**
     * Close the server socket only (stops listening), leaving peer info and state intact.
     * Used when this device switches from receiver to sender role within a session.
     */
    fun stopListening() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun cancel() {
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (_: Exception) {}
        _transferState.value = TransferState.Idle
        _remoteDeviceName.value = ""
        _peerAddress.value = ""
    }

    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1_048_576 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_048_576.0)
            bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}