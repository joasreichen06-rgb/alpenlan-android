package ch.alpenlan.kalender.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ------------------------------------------------------------------ Datenmodell (aus /api/state)

data class Pc(val online: Boolean, val booting: Boolean, val cpu: Double?, val temp: Double?, val memory: Double?, val updates: Boolean)
data class Source(val id: String, val name: String)
data class Avr(val available: Boolean, val on: Boolean, val volume: Int?, val max: Int, val muted: Boolean, val source: String?, val sources: List<Source>)
data class Tune(val ok: Boolean, val speed: Int?, val flow: Int?, val fan: Int?, val zoff: Double?, val homed: String)
data class Printer(
    val state: String, val progress: Double, val remainMin: Int, val file: String,
    val nozzle: Double?, val bed: Double?, val camera: Boolean, val web: String,
    val tune: Tune? = null,
)
data class Guest(val id: Int, val type: String, val name: String, val status: String, val cpu: Double, val mem: Double?, val uptime: Long)
data class Disk(val name: String, val used: Double, val total: Double, val pct: Double?)
data class Backup(val lastEnd: Long?, val lastOk: Boolean?, val running: Boolean, val schedules: List<String>)
data class Proxmox(
    val configured: Boolean, val ok: Boolean, val error: String?, val node: String,
    val cpu: Double?, val mem: Double?, val memUsed: Double, val memTotal: Double, val uptime: Long,
    val guests: List<Guest>, val disks: List<Disk>, val backup: Backup?,
)
data class Service(val name: String, val ok: Boolean, val ms: Int?)
data class Event(val time: String, val src: String, val msg: String)
data class ControlModel(
    val haConnected: Boolean, val pc: Pc, val avr: Avr, val printer: Printer,
    val proxmox: Proxmox?, val services: List<Service>, val events: List<Event>,
)

private fun JSONObject.dbl(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k).takeIf { !it.isNaN() }
private inline fun <T> JSONArray?.list(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }

fun parseModel(j: JSONObject): ControlModel {
    val pc = j.optJSONObject("pc") ?: JSONObject()
    val avr = j.optJSONObject("avr") ?: JSONObject()
    val pr = j.optJSONObject("printer") ?: JSONObject()
    val srv = j.optJSONObject("server")
    val px = srv?.optJSONObject("proxmox")
    return ControlModel(
        haConnected = j.optBoolean("connected"),
        pc = Pc(pc.optBoolean("online"), pc.optBoolean("booting"), pc.dbl("cpu"), pc.dbl("temp"), pc.dbl("memory"), pc.optBoolean("updates")),
        avr = Avr(
            avr.optBoolean("available"), avr.optBoolean("on"), avr.dbl("volume")?.toInt(), avr.optInt("max", 70), avr.optBoolean("muted"),
            avr.optString("source").ifBlank { null },
            avr.optJSONArray("sources").list { Source(it.optString("id"), it.optString("name")) },
        ),
        printer = Printer(
            pr.optString("state", "unavailable"), pr.optDouble("progress", 0.0).let { if (it.isNaN()) 0.0 else it },
            pr.optInt("remain_min"), pr.optString("file"), pr.dbl("nozzle"), pr.dbl("bed"), pr.optBoolean("camera"), pr.optString("web"),
            pr.optJSONObject("tune")?.let { t ->
                Tune(t.optBoolean("ok"), t.dbl("speed")?.toInt(), t.dbl("flow")?.toInt(), t.dbl("fan")?.toInt(), t.dbl("zoff"), t.optString("homed"))
            },
        ),
        proxmox = px?.let { p ->
            val b = p.optJSONObject("backup")
            val last = b?.optJSONObject("last")
            Proxmox(
                configured = p.optBoolean("configured"), ok = p.optBoolean("ok"), error = p.optString("error").ifBlank { null }?.takeIf { it != "null" },
                node = p.optString("node"), cpu = p.dbl("cpu"), mem = p.dbl("mem"),
                memUsed = p.optDouble("mem_used", 0.0), memTotal = p.optDouble("mem_total", 0.0), uptime = p.optLong("uptime"),
                guests = p.optJSONArray("guests").list {
                    Guest(it.optInt("id"), it.optString("type"), it.optString("name"), it.optString("status"), it.optDouble("cpu", 0.0), it.dbl("mem"), it.optLong("uptime"))
                },
                disks = p.optJSONArray("storage").list { Disk(it.optString("name"), it.optDouble("used", 0.0), it.optDouble("total", 0.0), it.dbl("pct")) },
                backup = b?.let {
                    Backup(
                        lastEnd = last?.optLong("end")?.takeIf { e -> e > 0 }, lastOk = last?.optBoolean("ok"), running = it.optBoolean("running"),
                        schedules = it.optJSONArray("jobs").list { jb -> if (jb.optBoolean("enabled", true)) jb.optString("schedule") else "" }.filter { s -> s.isNotBlank() },
                    )
                },
            )
        },
        services = srv?.optJSONArray("services").list { Service(it.optString("name"), it.optBoolean("ok"), if (it.isNull("ms")) null else it.optInt("ms")) },
        events = j.optJSONArray("events").list { Event(it.optString("t"), it.optString("src"), it.optString("msg")) },
    )
}

// ------------------------------------------------------------------ Zugriff auf control.alpenlan.com

class ControlApi(private val auth: Auth) {
    private suspend fun open(path: String, method: String = "GET", body: String? = null, retried: Boolean = false): HttpURLConnection {
        val token = auth.accessToken()
        return withContext(Dispatchers.IO) {
            val c = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                instanceFollowRedirects = false
                connectTimeout = 8_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Alpenlan", "1")
                }
            }
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            c
        }.let { c ->
            val code = withContext(Dispatchers.IO) { c.responseCode }
            val type = c.contentType ?: ""
            // Authentik leitet ohne gültigen Schlüssel auf die Login-Seite um
            if (code == 401 || code == 403 || code in 300..399 || (code == 200 && path.startsWith("/app/v1/state") && !type.contains("json"))) {
                c.disconnect()
                if (!retried) {
                    auth.accessToken(forceRefresh = true)
                    return open(path, method, body, retried = true)
                }
                throw AuthError(if (code in 300..399) "Control-Zugang für die App ist im Proxy noch gesperrt ($code)." else "Control lehnt die Anmeldung ab ($code) – bitte ab- und wieder anmelden.")
            }
            c
        }
    }

    suspend fun state(): ControlModel {
        val c = open("/app/v1/state")
        return withContext(Dispatchers.IO) {
            try {
                if (c.responseCode !in 200..299) throw Exception("Control antwortet mit ${c.responseCode}")
                parseModel(JSONObject(c.inputStream.bufferedReader().use { it.readText() }))
            } finally { c.disconnect() }
        }
    }

    suspend fun action(a: String, v: Any? = null): Boolean {
        val body = JSONObject().put("a", a).put("src", "App").apply { if (v != null) put("v", v) }.toString()
        val c = open("/app/v1/action", "POST", body)
        return withContext(Dispatchers.IO) {
            try {
                c.responseCode in 200..299 && JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optBoolean("ok")
            } catch (_: Exception) { false } finally { c.disconnect() }
        }
    }

    suspend fun camera(): Bitmap? = runCatching {
        val c = open("/app/v1/camera?t=${System.currentTimeMillis()}")
        withContext(Dispatchers.IO) {
            try { if (c.responseCode == 200) c.inputStream.use { BitmapFactory.decodeStream(it) } else null } finally { c.disconnect() }
        }
    }.getOrNull()

    /**
     * Live-Video (MJPEG) lesen und jedes Bild an [onFrame] geben. Läuft, bis die Verbindung
     * abbricht oder die Coroutine beendet wird.
     */
    suspend fun stream(onFrame: suspend (Bitmap) -> Unit) {
        val c = open("/app/v1/stream")
        withContext(Dispatchers.IO) {
            try {
                if (c.responseCode != 200) throw Exception("Stream ${c.responseCode}")
                val input = java.io.BufferedInputStream(c.inputStream, 65536)
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val jpeg = Mjpeg.nextFrame(input) ?: break
                    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)?.let { onFrame(it) }
                }
            } finally { c.disconnect() }
        }
    }

    companion object {
        const val BASE = "https://control.alpenlan.com"
    }
}

/** Liest einzelne JPEG-Bilder aus einem MJPEG-Strom (Content-Length oder JPEG-Marker). */
object Mjpeg {
    private fun readLine(i: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = i.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 4096) return sb.toString()
        }
    }

    fun nextFrame(i: java.io.InputStream): ByteArray? {
        var length = -1
        var inHeaders = false
        // Kopfzeilen des nächsten Teils lesen
        while (true) {
            val line = readLine(i) ?: return null
            if (line.startsWith("--")) { inHeaders = true; continue }
            if (line.contains(":")) {
                inHeaders = true
                val (k, v) = line.split(":", limit = 2)
                if (k.trim().equals("Content-Length", true)) length = v.trim().toIntOrNull() ?: -1
                continue
            }
            if (line.isEmpty() && inHeaders) break
        }
        if (length in 1..20_000_000) {
            val buf = ByteArray(length)
            var off = 0
            while (off < length) {
                val n = i.read(buf, off, length - off)
                if (n < 0) return null
                off += n
            }
            return buf
        }
        // Ohne Längenangabe: bis zum JPEG-Ende (FF D9) lesen
        val out = java.io.ByteArrayOutputStream(65536)
        var prev = -1
        while (true) {
            val b = i.read()
            if (b < 0) return null
            out.write(b)
            if (prev == 0xFF && b == 0xD9) return out.toByteArray()
            prev = b
        }
    }
}
