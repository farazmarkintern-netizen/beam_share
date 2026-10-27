package com.example.beamshare.videotoaudio

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.beamshare.videotoaudio.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Sealed UI state for conversion screen ────────────────────────────────────
sealed class ConversionState {
    object Idle : ConversionState()
    data class Converting(
        val progressPercent: Int,
        val elapsedMs: Long,
        val speedLabel: String,
        val logs: List<LogEntry>
    ) : ConversionState()
    data class Done(val result: ConversionResult) : ConversionState()
    data class Error(val message: String, val details: String? = null) : ConversionState()
    object Cancelled : ConversionState()
}

class VideoToAudioViewModel(app: Application) : AndroidViewModel(app) {

    // ── VA01: recent video list ───────────────────────────────────────────────
    private val _videos = MutableStateFlow<List<VideoFile>>(emptyList())
    val videos: StateFlow<List<VideoFile>> = _videos.asStateFlow()

    private val _videosLoading = MutableStateFlow(false)
    val videosLoading: StateFlow<Boolean> = _videosLoading.asStateFlow()

    // ── VA01/VA02: selected file ──────────────────────────────────────────────
    private val _selectedVideo = MutableStateFlow<VideoFile?>(null)
    val selectedVideo: StateFlow<VideoFile?> = _selectedVideo.asStateFlow()

    // ── VA02: bitrate selection ───────────────────────────────────────────────
    private val _bitrateKbps = MutableStateFlow(192)
    val bitrateKbps: StateFlow<Int> = _bitrateKbps.asStateFlow()

    // ── VA03: conversion state ────────────────────────────────────────────────
    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private var conversionJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    // VA01: load recent videos from MediaStore
    // ─────────────────────────────────────────────────────────────────────────
    fun loadRecentVideos() {
        viewModelScope.launch {
            _videosLoading.value = true
            _videos.value = queryRecentVideos(getApplication())
            _videosLoading.value = false
        }
    }

    private suspend fun queryRecentVideos(context: Context): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<VideoFile>()
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, sortOrder
            )?.use { cursor ->
                val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext() && list.size < 20) {
                    val id = cursor.getLong(idCol)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()
                    ).toString()
                    list += VideoFile(
                        id          = id,
                        displayName = cursor.getString(nameCol) ?: "video_$id",
                        duration    = cursor.getLong(durationCol),
                        size        = cursor.getLong(sizeCol),
                        uri         = uri
                    )
                }
            }
            list
        }

    // ─────────────────────────────────────────────────────────────────────────
    // VA01/VA02: select a video (from list or file picker)
    // ─────────────────────────────────────────────────────────────────────────
    fun selectVideo(video: VideoFile) {
        _selectedVideo.value = video
    }

    fun selectVideoFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    _selectedVideo.value = VideoFile(
                        id          = 0L,
                        displayName = cursor.getString(nameCol) ?: uri.lastPathSegment ?: "video",
                        duration    = cursor.getLong(durationCol),
                        size        = cursor.getLong(sizeCol),
                        uri         = uri.toString()
                    )
                }
            } ?: run {
                // Fallback: uri exists but no MediaStore row (e.g. from Downloads)
                _selectedVideo.value = VideoFile(
                    id          = 0L,
                    displayName = uri.lastPathSegment ?: "video",
                    duration    = 0L,
                    size        = 0L,
                    uri         = uri.toString()
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VA02: set bitrate
    // ─────────────────────────────────────────────────────────────────────────
    fun setBitrate(kbps: Int) {
        _bitrateKbps.value = kbps
    }

    fun getEstimatedSize(): String {
        val video = _selectedVideo.value ?: return "—"
        return ConversionConfig(video, bitrateKbps = _bitrateKbps.value).estimatedSizeLabel
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VA03: start conversion
    // Uses MediaExtractor + MediaMuxer (AAC audio track extraction).
    // For true MP3 output, replace this with FFmpegKit.
    // ─────────────────────────────────────────────────────────────────────────
    fun startConversion() {
        val video = _selectedVideo.value ?: return
        val config = ConversionConfig(video, bitrateKbps = _bitrateKbps.value)

        conversionJob = viewModelScope.launch(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            val logs = mutableListOf<LogEntry>()

            fun addLog(text: String, status: LogStatus = LogStatus.DONE) {
                val elapsed = System.currentTimeMillis() - startMs
                val sec = elapsed / 1000
                val timestamp = "%d:%02d".format(sec / 60, sec % 60)
                logs.add(LogEntry(logs.size, text, timestamp, status))
            }

            fun emitProgress(percent: Int) {
                val elapsed = System.currentTimeMillis() - startMs
                val speed = if (elapsed > 0) "%.1fx".format(
                    (video.duration * (percent / 100.0)) / elapsed
                ) else "—"
                _conversionState.value = ConversionState.Converting(
                    progressPercent = percent,
                    elapsedMs       = elapsed,
                    speedLabel      = speed,
                    logs            = logs.toList()
                )
            }

            try {
                _conversionState.value = ConversionState.Converting(0, 0, "—", emptyList())

                // Step 1: prepare extractor
                addLog("Reading video file…", LogStatus.DONE)
                emitProgress(5)

                val context = getApplication<Application>()
                val extractor = MediaExtractor()
                extractor.setDataSource(context, Uri.parse(video.uri), null)

                // Find audio track
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        audioFormat = fmt
                        break
                    }
                }
                if (audioTrackIndex == -1) {
                    _conversionState.value = ConversionState.Error(
                        message = "No audio track found in this video.",
                        details = "The selected video file appears to have no audio stream or uses an unsupported codec."
                    )
                    extractor.release()
                    return@launch
                }

                addLog("Audio track found: ${audioFormat?.getString(MediaFormat.KEY_MIME)}", LogStatus.DONE)
                emitProgress(15)

                // Step 2: create output file
                val outputFile = createOutputFile(context, config)
                addLog("Output file created: ${outputFile.name}", LogStatus.DONE)
                emitProgress(20)

                // Step 3: mux audio
                extractor.selectTrack(audioTrackIndex)
                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrackIndex = muxer.addTrack(audioFormat!!)
                muxer.start()

                addLog("Extracting audio stream…", LogStatus.IN_PROGRESS)
                emitProgress(25)

                val bufferSize = 1024 * 1024
                val buffer = java.nio.ByteBuffer.allocate(bufferSize)
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                val totalDuration = video.duration * 1000L // microseconds

                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxTrackIndex, buffer, bufferInfo)
                    extractor.advance()

                    // Update progress (25–90%)
                    if (totalDuration > 0) {
                        val pct = 25 + ((bufferInfo.presentationTimeUs.toDouble() / totalDuration) * 65).toInt()
                        emitProgress(pct.coerceIn(25, 90))
                    }
                }

                muxer.stop()
                muxer.release()
                extractor.release()

                addLog("Audio extraction complete", LogStatus.DONE)
                emitProgress(92)

                // Step 4: save to MediaStore
                val savedUri = saveToMediaStore(context, outputFile)
                addLog("Saved to Music library", LogStatus.DONE)
                emitProgress(98)

                val finalSize = outputFile.length()
                emitProgress(100)
                addLog("Conversion complete ✓", LogStatus.DONE)

                _conversionState.value = ConversionState.Done(
                    ConversionResult(
                        outputPath     = savedUri?.toString() ?: outputFile.absolutePath,
                        outputFileName = outputFile.name,
                        fileSizeBytes  = finalSize,
                        durationMs     = video.duration,
                        bitrateKbps    = config.bitrateKbps
                    )
                )

            } catch (e: Exception) {
                val userMsg = when {
                    e is java.io.FileNotFoundException -> "Video file not found or cannot be opened. Please check storage access."
                    e is IllegalArgumentException -> "Unsupported video format: ${e.localizedMessage}"
                    e is SecurityException -> "Storage permission denied while reading video file."
                    e is IllegalStateException -> "Decoder error: ${e.localizedMessage ?: "Failed to process audio stream."}"
                    else -> e.localizedMessage ?: e.message ?: "An unexpected error occurred during conversion."
                }
                _conversionState.value = ConversionState.Error(
                    message = userMsg,
                    details = "${e.javaClass.simpleName}: ${e.message ?: "Extraction failed"}"
                )
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        conversionJob = null
        _conversionState.value = ConversionState.Cancelled
    }

    fun resetConversion() {
        _conversionState.value = ConversionState.Idle
        _selectedVideo.value = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun createOutputFile(context: Context, config: ConversionConfig): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${config.videoFile.displayName.substringBeforeLast('.')}_$timestamp.m4a"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        return File(dir, name)
    }

    private fun saveToMediaStore(context: Context, file: File): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/BeamShare")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    file.inputStream().copyTo(out)
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
            uri
        } else {
            Uri.fromFile(file)
        }
    }
}