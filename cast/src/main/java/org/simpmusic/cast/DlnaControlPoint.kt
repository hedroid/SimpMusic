package org.simpmusic.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class DlnaControlPoint(val device: DlnaDevice) {
    data class PositionInfo(
        val positionMs: Long,
        val durationMs: Long,
    )

    enum class TransportState {
        PLAYING,
        PAUSED,
        STOPPED,
        TRANSITIONING,
        UNKNOWN,
    }

    suspend fun setMedia(
        url: String,
        title: String?,
        artist: String?,
        album: String?,
        artworkUrl: String?,
        mimeType: String,
    ) {
        val metadata = buildDidlMetadata(url, title, artist, album, artworkUrl, mimeType)
        invoke(
            device.avTransportControlUrl,
            device.avTransportServiceType,
            "SetAVTransportURI",
            mapOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadata),
        )
    }

    suspend fun play() = invokeTransport("Play", mapOf("InstanceID" to "0", "Speed" to "1"))

    suspend fun pause() = invokeTransport("Pause", mapOf("InstanceID" to "0"))

    suspend fun stop() = invokeTransport("Stop", mapOf("InstanceID" to "0"))

    suspend fun seek(positionMs: Long) =
        invokeTransport(
            "Seek",
            mapOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to formatDuration(positionMs)),
        )

    suspend fun positionInfo(): PositionInfo {
        val response = invokeTransport("GetPositionInfo", mapOf("InstanceID" to "0"))
        return PositionInfo(
            positionMs = parseDuration(response.value("RelTime")),
            durationMs = parseDuration(response.value("TrackDuration")),
        )
    }

    suspend fun transportState(): TransportState {
        val response = invokeTransport("GetTransportInfo", mapOf("InstanceID" to "0"))
        return when (response.value("CurrentTransportState")?.uppercase()) {
            "PLAYING" -> TransportState.PLAYING
            "PAUSED_PLAYBACK", "PAUSED_RECORDING" -> TransportState.PAUSED
            "STOPPED", "NO_MEDIA_PRESENT" -> TransportState.STOPPED
            "TRANSITIONING" -> TransportState.TRANSITIONING
            else -> TransportState.UNKNOWN
        }
    }

    suspend fun setVolume(volume: Float) {
        val url = device.renderingControlUrl ?: return
        val service = device.renderingControlServiceType ?: return
        invoke(
            url,
            service,
            "SetVolume",
            mapOf(
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredVolume" to (volume.coerceIn(0f, 1f) * 100).toInt().toString(),
            ),
        )
    }

    private suspend fun invokeTransport(
        action: String,
        arguments: Map<String, String>,
    ): ByteArray = invoke(device.avTransportControlUrl, device.avTransportServiceType, action, arguments)

    private suspend fun invoke(
        controlUrl: String,
        service: String,
        action: String,
        arguments: Map<String, String>,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val body =
                buildString {
                    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                    append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                    append("<s:Body><u:").append(action).append(" xmlns:u=\"").append(service).append("\">")
                    arguments.forEach { (name, value) ->
                        append('<').append(name).append('>').append(escapeXml(value)).append("</").append(name).append('>')
                    }
                    append("</u:").append(action).append("></s:Body></s:Envelope>")
                }.toByteArray(Charsets.UTF_8)
            val connection = (URI(controlUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$service#$action\"")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Connection", "close")
                setFixedLengthStreamingMode(body.size)
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val response =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.use { it.readBytes() }
                    ?: ByteArray(0)
            if (code !in 200..299) {
                throw DlnaControlException(action, code, String(response, Charsets.UTF_8))
            }
            response
        }

    class DlnaControlException(action: String, statusCode: Int, response: String) :
        Exception("DLNA $action failed: HTTP $statusCode ${response.take(240)}")

    companion object {
        private const val HTTP_TIMEOUT_MS = 5_000
        private const val USER_AGENT = "SimpMusic/3 UPnP/1.1"

        internal fun buildDidlMetadata(
            url: String,
            title: String?,
            artist: String?,
            album: String?,
            artworkUrl: String?,
            mimeType: String,
        ): String =
            buildString {
                append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
                append("<item id=\"0\" parentID=\"0\" restricted=\"1\">")
                append("<dc:title>").append(escapeXml(title ?: "SimpMusic")).append("</dc:title>")
                artist?.let { append("<upnp:artist>").append(escapeXml(it)).append("</upnp:artist>") }
                album?.let { append("<upnp:album>").append(escapeXml(it)).append("</upnp:album>") }
                artworkUrl?.let { append("<upnp:albumArtURI>").append(escapeXml(it)).append("</upnp:albumArtURI>") }
                append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
                append("<res protocolInfo=\"http-get:*:").append(escapeXml(mimeType)).append(":*\">")
                append(escapeXml(url)).append("</res></item></DIDL-Lite>")
            }

        internal fun parseDuration(value: String?): Long {
            if (value.isNullOrBlank() || value == "NOT_IMPLEMENTED") return 0L
            val parts = value.substringBefore('.').split(':')
            if (parts.size != 3) return 0L
            val hours = parts[0].toLongOrNull() ?: return 0L
            val minutes = parts[1].toLongOrNull() ?: return 0L
            val seconds = parts[2].toLongOrNull() ?: return 0L
            return ((hours * 60 + minutes) * 60 + seconds) * 1_000
        }

        internal fun formatDuration(positionMs: Long): String {
            val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000
            val hours = totalSeconds / 3_600
            val minutes = (totalSeconds % 3_600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

        private fun ByteArray.value(localName: String): String? {
            if (isEmpty()) return null
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { isXIncludeAware = false }
                    runCatching { setExpandEntityReferences(false) }
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(this))
            return document.getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim()
        }

        private fun escapeXml(value: String): String =
            value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
    }
}
