package org.simpmusic.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.coroutineContext

class DlnaDiscovery(private val context: Context) {
    suspend fun discover(timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS): List<DlnaDevice> =
        withContext(Dispatchers.IO) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock =
                wifiManager
                    ?.createMulticastLock(MULTICAST_LOCK_TAG)
                    ?.apply {
                        setReferenceCounted(false)
                        runCatching { acquire() }
                    }
            try {
                val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
                val localNetworks =
                    connectivityManager
                        ?.allNetworks
                        ?.filter { network ->
                            val capabilities = connectivityManager.getNetworkCapabilities(network)
                            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                        }.orEmpty()
                Logger.d(TAG, "DLNA discovery using ${localNetworks.size} local network(s)")
                val scanNetworks: List<Network?> = localNetworks.ifEmpty { listOf(null) }
                val targets =
                    scanNetworks
                        .map { network ->
                            async {
                                runCatching { discoverLocations(timeoutMs, network) }
                                    .getOrDefault(emptySet())
                                    .map { location -> DiscoveryTarget(location, network) }
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .distinctBy { it.location }
                targets
                    .mapNotNull { target ->
                        coroutineContext.ensureActive()
                        runCatching { loadDevice(target) }
                            .onFailure { error ->
                                Logger.d(TAG, "Rejected DLNA description at ${target.location}: ${error.message}")
                            }.getOrNull()
                    }.distinctBy { it.id }
                    .sortedBy { it.name.lowercase() }
            } finally {
                if (multicastLock?.isHeld == true) multicastLock.release()
            }
        }

    private fun discoverLocations(
        timeoutMs: Long,
        network: Network?,
    ): Set<String> {
        val target = InetAddress.getByName(SSDP_ADDRESS)
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            network?.bindSocket(socket)
            socket.reuseAddress = true
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            repeat(SEARCH_ATTEMPTS) {
                SEARCH_TARGETS.forEach { searchTarget ->
                    val request = buildSearchRequest(searchTarget)
                    socket.send(DatagramPacket(request, request.size, target, SSDP_PORT))
                }
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val response = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    parseSsdpHeaders(response)["location"]?.let { location ->
                        locations.add(location)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Keep the socket open for the full discovery window: TVs often answer the
                    // second M-SEARCH after waking their UPnP stack.
                }
            }
        }
        return locations
    }

    private fun loadDevice(target: DiscoveryTarget): DlnaDevice? {
        val url = URI(target.location).toURL()
        val connection = ((target.network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection).apply {
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Connection", "close")
        }
        if (connection.responseCode !in 200..299) return null
        val xml = connection.inputStream.use { it.readBytes() }
        return parseDeviceDescription(target.location, xml)
    }

    private data class DiscoveryTarget(
        val location: String,
        val network: Network?,
    )

    companion object {
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SEARCH_ATTEMPTS = 3
        private const val RECEIVE_TIMEOUT_MS = 500
        private const val HTTP_TIMEOUT_MS = 3_000
        private const val MAX_PACKET_SIZE = 16 * 1024
        private const val MULTICAST_LOCK_TAG = "SimpMusic:DLNA"
        private const val USER_AGENT = "SimpMusic/3 UPnP/1.1"
        private const val TAG = "DlnaDiscovery"
        private val SEARCH_TARGETS =
            listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:schemas-upnp-org:service:AVTransport:1",
                "ssdp:all",
            )
        const val DEFAULT_DISCOVERY_TIMEOUT_MS = 4_500L

        private fun buildSearchRequest(searchTarget: String): ByteArray =
            buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: 239.255.255.250:1900\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: ").append(searchTarget).append("\r\n")
                append("USER-AGENT: ").append(USER_AGENT).append("\r\n")
                append("\r\n")
            }.toByteArray(Charsets.UTF_8)

        internal fun parseSsdpHeaders(response: String): Map<String, String> =
            response
                .lineSequence()
                .drop(1)
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) null else line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
                }.toMap()

        internal fun parseDeviceDescription(
            location: String,
            xml: ByteArray,
        ): DlnaDevice? {
            val factory = secureDocumentBuilderFactory()
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val controlUrlBase =
                document
                    .getElementsByTagNameNS("*", "URLBase")
                    .item(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: location
            val devices = document.getElementsByTagNameNS("*", "device")
            val deviceElements = (0 until devices.length).mapNotNull { devices.item(it) as? Element }
            // Some renderers (including vendor-customized speakers) expose a working AVTransport
            // service but use a proprietary deviceType instead of the DLNA MediaRenderer type.
            // Prefer the standard renderer, then fall back to capability-based detection.
            val renderer =
                deviceElements.firstOrNull { element ->
                    element.childText("deviceType")?.contains(":device:MediaRenderer:", ignoreCase = true) == true
                } ?: deviceElements.firstOrNull { element ->
                    element.hasService(":service:AVTransport:")
                } ?: return null
            val services = renderer.getElementsByTagNameNS("*", "service")
            var avTransport: Pair<String, String>? = null
            var renderingControl: Pair<String, String>? = null
            for (index in 0 until services.length) {
                val service = services.item(index) as? Element ?: continue
                val type = service.childText("serviceType").orEmpty()
                val controlUrl = service.childText("controlURL") ?: continue
                when {
                    type.contains(":service:AVTransport:", ignoreCase = true) ->
                        avTransport = type to resolveUrl(controlUrlBase, controlUrl)
                    type.contains(":service:RenderingControl:", ignoreCase = true) ->
                        renderingControl = type to resolveUrl(controlUrlBase, controlUrl)
                }
            }
            val transport = avTransport ?: return null
            val name = renderer.childText("friendlyName")?.takeIf { it.isNotBlank() } ?: return null
            return DlnaDevice(
                id = renderer.childText("UDN")?.takeIf { it.isNotBlank() } ?: location,
                name = name,
                modelName = renderer.childText("modelName"),
                manufacturer = renderer.childText("manufacturer"),
                locationUrl = location,
                avTransportServiceType = transport.first,
                avTransportControlUrl = transport.second,
                renderingControlServiceType = renderingControl?.first,
                renderingControlUrl = renderingControl?.second,
            )
        }

        private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Android's bundled XML parser throws UnsupportedOperationException for the
                // XInclude property even when disabling it. Keep the secure setting where the
                // implementation supports it instead of rejecting every device description.
                runCatching { isXIncludeAware = false }
                runCatching { setExpandEntityReferences(false) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }

        private fun Element.childText(localName: String): String? {
            val nodes = getElementsByTagNameNS("*", localName)
            return (0 until nodes.length)
                .mapNotNull { nodes.item(it) as? Element }
                .firstOrNull { it.parentNode === this }
                ?.textContent
                ?.trim()
        }

        private fun Element.hasService(typeFragment: String): Boolean {
            val services = getElementsByTagNameNS("*", "service")
            return (0 until services.length)
                .mapNotNull { services.item(it) as? Element }
                .any { service -> service.childText("serviceType")?.contains(typeFragment, ignoreCase = true) == true }
        }

        private fun resolveUrl(base: String, candidate: String): String {
            val trimmedCandidate = candidate.trim()
            return runCatching { URI(base).resolve(trimmedCandidate).toString() }
                .getOrElse { error ->
                    // Older HappyCast/LEBO renderers advertise control paths such as
                    // `_urn:schemas-upnp-org:service:AVTransport_control`. The colon makes
                    // java.net.URI interpret the relative value as a malformed scheme even
                    // though the renderer serves it as an ordinary path at the URLBase root.
                    if (!trimmedCandidate.startsWith("_urn:", ignoreCase = true)) throw error
                    val baseUri = URI(base)
                    URI(
                        baseUri.scheme,
                        baseUri.userInfo,
                        baseUri.host,
                        baseUri.port,
                        "/${trimmedCandidate.trimStart('/')}",
                        null,
                        null,
                    ).toString()
                }
        }
    }
}
