package com.emptyset.detector.log

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.emptyset.detector.detect.Ieee80211
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors

data class CapturedFrame(
    val id: String,
    var attemptId: String?,
    val epochMs: Long,
    val kind: String,
    val reasonCode: Int?,
    val reasonName: String,
    val channel: Int?,
    val band: String,
    val rssiDbm: Int?,
    val destination: String,
    val source: String,
    val bssid: String,
    val rawHex: String
) {
    companion object
}

data class CaptureAttempt(
    val id: String,
    val startedAtMs: Long,
    var endedAtMs: Long?,
    var frameCount: Int,
    var channels: Set<Int>,
    var bands: Set<String>,
    var sources: Set<String>,
    var destinations: Set<String>,
    var bssids: Set<String>
) {
    val durationMs: Long get() = (endedAtMs ?: System.currentTimeMillis()) - startedAtMs

    companion object
}

class CaptureLog(context: Context) {
    private val app = context.applicationContext
    private val file = File(app.filesDir, "capture.json")
    val recordingsDir: File = File(
        app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir,
        "recordings"
    ).apply { mkdirs() }
    private val lock = Any()
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "emptyset-log").apply { isDaemon = true }
    }
    private val frames = mutableListOf<CapturedFrame>()
    private val attempts = mutableListOf<CaptureAttempt>()
    private var openAttempt: CaptureAttempt? = null
    private var framesSincePersist = 0
    private var lastFramePublishMs = 0L

    private val _attempts = MutableStateFlow<List<CaptureAttempt>>(emptyList())
    val attemptsFlow: StateFlow<List<CaptureAttempt>> = _attempts.asStateFlow()

    private val _frames = MutableStateFlow<List<CapturedFrame>>(emptyList())
    val framesFlow: StateFlow<List<CapturedFrame>> = _frames.asStateFlow()

    var onRecordingSaved: ((String) -> Unit)? = null

    init {
        synchronized(lock) {
            loadLocked()
            publishAttemptsLocked()
            publishFramesLocked()
        }
    }

    fun recordFrame(event: Ieee80211.MgmtEvent, channel: Int?, rssiDbm: Int?) {
        io.execute {
            synchronized(lock) {
                val ch = event.channelHint ?: channel
                val frame = CapturedFrame(
                    id = UUID.randomUUID().toString(),
                    attemptId = openAttempt?.id,
                    epochMs = System.currentTimeMillis(),
                    kind = event.kind,
                    reasonCode = event.reasonCode,
                    reasonName = event.reasonName,
                    channel = ch,
                    band = Ieee80211.bandOf(ch),
                    rssiDbm = event.rssiDbm ?: rssiDbm,
                    destination = event.addr1,
                    source = event.addr2,
                    bssid = event.addr3,
                    rawHex = event.rawHex
                )
                frames.add(0, frame)
                openAttempt?.let { attachLocked(it, frame) }
                trimLocked()
                framesSincePersist += 1
                if (framesSincePersist >= 40) {
                    persistLocked()
                    framesSincePersist = 0
                }
                val now = System.currentTimeMillis()
                if (now - lastFramePublishMs >= 250) {
                    publishFramesLocked()
                    lastFramePublishMs = now
                }
            }
        }
    }

    fun startAttempt(windowStartMs: Long) {
        io.execute {
            val attempt: CaptureAttempt
            val json: String
            val csv: String
            synchronized(lock) {
                openAttempt?.let { if (it.endedAtMs == null) it.endedAtMs = System.currentTimeMillis() }
                attempt = CaptureAttempt(
                    id = UUID.randomUUID().toString(),
                    startedAtMs = windowStartMs,
                    endedAtMs = null,
                    frameCount = 0,
                    channels = linkedSetOf(),
                    bands = linkedSetOf(),
                    sources = linkedSetOf(),
                    destinations = linkedSetOf(),
                    bssids = linkedSetOf()
                )
                frames.filter { it.attemptId == null && it.epochMs >= windowStartMs }.forEach { frame ->
                    frame.attemptId = attempt.id
                    attachLocked(attempt, frame)
                }
                openAttempt = attempt
                attempts.add(0, attempt)
                persistLocked()
                framesSincePersist = 0
                publishAttemptsLocked()
                publishFramesLocked()
                json = exportJsonLocked(attempt.id)
                csv = exportCsvLocked(attempt.id)
            }
            writeUserFiles(attempt, json, csv, publishDownloads = true)
        }
    }

    fun endAttempt() {
        io.execute {
            val snapshot: Triple<CaptureAttempt, String, String>?
            synchronized(lock) {
                val attempt = openAttempt
                attempt?.endedAtMs = System.currentTimeMillis()
                openAttempt = null
                persistLocked()
                framesSincePersist = 0
                publishAttemptsLocked()
                publishFramesLocked()
                snapshot = attempt?.let {
                    Triple(it, exportJsonLocked(it.id), exportCsvLocked(it.id))
                }
            }
            snapshot?.let { (attempt, json, csv) ->
                writeUserFiles(attempt, json, csv, publishDownloads = true)
            }
        }
    }

    fun framesFor(attemptId: String): List<CapturedFrame> = synchronized(lock) {
        frames.filter { it.attemptId == attemptId }
    }

    fun attempt(id: String): CaptureAttempt? = synchronized(lock) {
        attempts.firstOrNull { it.id == id }
    }

    fun exportJson(attemptId: String? = null): String = synchronized(lock) {
        exportJsonLocked(attemptId)
    }

    fun exportCsv(attemptId: String? = null): String = synchronized(lock) {
        exportCsvLocked(attemptId)
    }

    fun clear() {
        io.execute {
            synchronized(lock) {
                frames.clear()
                attempts.clear()
                openAttempt = null
                persistLocked()
                publishAttemptsLocked()
                publishFramesLocked()
            }
        }
    }

    private fun attachLocked(attempt: CaptureAttempt, frame: CapturedFrame) {
        attempt.frameCount += 1
        frame.channel?.let { attempt.channels = attempt.channels + it }
        attempt.bands = attempt.bands + frame.band
        attempt.sources = attempt.sources + frame.source
        attempt.destinations = attempt.destinations + frame.destination
        attempt.bssids = attempt.bssids + frame.bssid
    }

    private fun trimLocked() {
        if (frames.size > MAX_FRAMES) {
            val keep = frames.take(MAX_FRAMES).toMutableList()
            frames.clear()
            frames.addAll(keep)
        }
        if (attempts.size > MAX_ATTEMPTS) {
            val keep = attempts.take(MAX_ATTEMPTS).toMutableList()
            attempts.clear()
            attempts.addAll(keep)
        }
    }

    private fun exportJsonLocked(attemptId: String? = null): String {
        val selectedAttempts = if (attemptId == null) attempts else attempts.filter { it.id == attemptId }
        val selectedFrames = if (attemptId == null) frames else frames.filter { it.attemptId == attemptId }
        return JSONObject()
            .put("exportedAt", iso(System.currentTimeMillis()))
            .put("passive", true)
            .put("attempts", JSONArray(selectedAttempts.map { it.toJson() }))
            .put("frames", JSONArray(selectedFrames.map { it.toJson() }))
            .toString(2)
    }

    private fun exportCsvLocked(attemptId: String? = null): String {
        val selected = if (attemptId == null) frames else frames.filter { it.attemptId == attemptId }
        return buildString {
            appendLine("time_utc,attempt_id,kind,reason_code,reason,channel,band,rssi_dbm,destination,source,bssid,raw_hex")
            selected.asReversed().forEach { frame ->
                append(
                    listOf(
                        iso(frame.epochMs),
                        frame.attemptId.orEmpty(),
                        frame.kind,
                        frame.reasonCode?.toString().orEmpty(),
                        csv(frame.reasonName),
                        frame.channel?.toString().orEmpty(),
                        frame.band,
                        frame.rssiDbm?.toString().orEmpty(),
                        frame.destination,
                        frame.source,
                        frame.bssid,
                        frame.rawHex
                    ).joinToString(",")
                )
                appendLine()
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            val json = JSONObject()
                .put("attempts", JSONArray(attempts.map { it.toJson() }))
                .put("frames", JSONArray(frames.map { it.toJson() }))
            val tmp = File(file.parentFile, "capture.json.tmp")
            tmp.writeText(json.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun publishAttemptsLocked() {
        _attempts.value = attempts.toList()
    }

    private fun publishFramesLocked() {
        lastFramePublishMs = System.currentTimeMillis()
        _frames.value = frames.toList()
    }

    private fun writeUserFiles(
        attempt: CaptureAttempt,
        json: String,
        csv: String,
        publishDownloads: Boolean
    ) {
        runCatching { recordingsDir.mkdirs() }
        val stamp = fileStamp(attempt.startedAtMs)
        val jsonName = "Deauth Notification $stamp.json"
        val csvName = "Deauth Notification $stamp.csv"
        val jsonFile = File(recordingsDir, jsonName)
        val csvFile = File(recordingsDir, csvName)
        runCatching { jsonFile.writeText(json) }
        runCatching { csvFile.writeText(csv) }
        if (publishDownloads) {
            saveToDownloads(jsonName, json, "application/json")
            saveToDownloads(csvName, csv, "text/csv")
        }
        if (jsonFile.exists()) {
            onRecordingSaved?.invoke(jsonFile.absolutePath)
        }
    }

    private fun saveToDownloads(name: String, body: String, mime: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val resolver = app.contentResolver
            val bytes = body.toByteArray(Charsets.UTF_8)
            val existing = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(name),
                null
            )
            val existingId = existing?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
            if (existingId != null) {
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    existingId
                )
                resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                return@runCatching
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun fileStamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date(epochMs))

    private fun loadLocked() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            attempts.clear()
            frames.clear()
            val attemptArray = root.optJSONArray("attempts") ?: JSONArray()
            for (i in 0 until attemptArray.length()) {
                attempts.add(CaptureAttempt.fromJson(attemptArray.getJSONObject(i)))
            }
            val frameArray = root.optJSONArray("frames") ?: JSONArray()
            for (i in 0 until frameArray.length()) {
                frames.add(CapturedFrame.fromJson(frameArray.getJSONObject(i)))
            }
            openAttempt = attempts.firstOrNull { it.endedAtMs == null }
        }
    }

    companion object {
        private const val MAX_FRAMES = 10_000
        private const val MAX_ATTEMPTS = 500
        private val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun iso(epochMs: Long): String = utc.format(Date(epochMs))

        fun displayTime(epochMs: Long): String {
            val local = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return local.format(Date(epochMs))
        }

        fun exportFileName(extension: String, epochMs: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault()).format(Date(epochMs))
            return "Deauth Notification $stamp.$extension"
        }

        private fun csv(value: String): String =
            "\"${value.replace("\"", "\"\"")}\""
    }
}

private fun CaptureAttempt.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("startedAtMs", startedAtMs)
    .put("endedAtMs", endedAtMs ?: JSONObject.NULL)
    .put("frameCount", frameCount)
    .put("channels", JSONArray(channels.toList()))
    .put("bands", JSONArray(bands.toList()))
    .put("sources", JSONArray(sources.toList()))
    .put("destinations", JSONArray(destinations.toList()))
    .put("bssids", JSONArray(bssids.toList()))

private fun CapturedFrame.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("attemptId", attemptId ?: JSONObject.NULL)
    .put("epochMs", epochMs)
    .put("kind", kind)
    .put("reasonCode", reasonCode ?: JSONObject.NULL)
    .put("reasonName", reasonName)
    .put("channel", channel ?: JSONObject.NULL)
    .put("band", band)
    .put("rssiDbm", rssiDbm ?: JSONObject.NULL)
    .put("destination", destination)
    .put("source", source)
    .put("bssid", bssid)
    .put("rawHex", rawHex)

private fun jsonStringSet(obj: JSONObject, key: String): Set<String> {
    val array = obj.optJSONArray(key) ?: return emptySet()
    return (0 until array.length()).map { array.getString(it) }.toSet()
}

private fun CaptureAttempt.Companion.fromJson(obj: JSONObject): CaptureAttempt = CaptureAttempt(
    id = obj.getString("id"),
    startedAtMs = obj.getLong("startedAtMs"),
    endedAtMs = if (obj.isNull("endedAtMs")) null else obj.optLong("endedAtMs"),
    frameCount = obj.optInt("frameCount"),
    channels = jsonStringSet(obj, "channels").mapNotNull { it.toIntOrNull() }.toSet(),
    bands = jsonStringSet(obj, "bands"),
    sources = jsonStringSet(obj, "sources"),
    destinations = jsonStringSet(obj, "destinations"),
    bssids = jsonStringSet(obj, "bssids")
)

private fun CapturedFrame.Companion.fromJson(obj: JSONObject): CapturedFrame = CapturedFrame(
    id = obj.getString("id"),
    attemptId = obj.optString("attemptId").ifBlank { null },
    epochMs = obj.getLong("epochMs"),
    kind = obj.getString("kind"),
    reasonCode = if (obj.isNull("reasonCode")) null else obj.optInt("reasonCode"),
    reasonName = obj.optString("reasonName"),
    channel = if (obj.isNull("channel")) null else obj.optInt("channel"),
    band = obj.optString("band"),
    rssiDbm = if (obj.isNull("rssiDbm")) null else obj.optInt("rssiDbm"),
    destination = obj.optString("destination"),
    source = obj.optString("source"),
    bssid = obj.optString("bssid"),
    rawHex = obj.optString("rawHex")
)
