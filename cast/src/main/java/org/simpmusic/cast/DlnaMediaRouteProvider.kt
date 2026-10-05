package org.simpmusic.cast

import android.content.Context
import android.content.IntentFilter
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteDiscoveryRequest
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.maxrave.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val DLNA_CONTROL_CATEGORY = "org.simpmusic.cast.category.DLNA"

private val remotePlaybackSelector: MediaRouteSelector =
    MediaRouteSelector
        .Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
            ),
        ).addControlCategory(DLNA_CONTROL_CATEGORY)
        .build()

private var installedProvider: DlnaMediaRouteProvider? = null

/** Installs the DLNA provider once and returns the combined Cast + DLNA selector. */
internal fun ensureDlnaMediaRouteProvider(context: Context): MediaRouteSelector {
    if (installedProvider == null) {
        val appContext = context.applicationContext
        installedProvider = DlnaMediaRouteProvider(appContext).also {
            MediaRouter.getInstance(appContext).addProvider(it)
        }
    }
    return remotePlaybackSelector
}

/** Publishes discovered UPnP AV renderers as ordinary MediaRouter routes. */
private class DlnaMediaRouteProvider(context: Context) : MediaRouteProvider(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var discoveryJob: Job? = null
    private var devicesByRouteId: Map<String, DlnaDevice> = emptyMap()

    init {
        descriptor = MediaRouteProviderDescriptor.Builder().build()
    }

    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        val requestsDlna = request?.selector?.hasControlCategory(DLNA_CONTROL_CATEGORY) == true
        if (!requestsDlna) {
            discoveryJob?.cancel()
            discoveryJob = null
            return
        }
        if (discoveryJob?.isActive == true) return

        discoveryJob =
            scope.launch {
                runCatching { DlnaDiscovery(context).discover() }
                    .onSuccess { devices ->
                        publishDevices(devices)
                    }
                    .onFailure { error -> Logger.e(TAG, "DLNA route discovery failed: ${error.message}", error) }
            }
    }

    override fun onCreateRouteController(routeId: String): RouteController? {
        val device = devicesByRouteId[routeId] ?: return null
        return DlnaRouteController(device, scope)
    }

    private fun publishDevices(devices: List<DlnaDevice>) {
        devicesByRouteId = devices.associateBy(::routeId)
        val filter = IntentFilter().apply { addCategory(DLNA_CONTROL_CATEGORY) }
        val providerDescriptor = MediaRouteProviderDescriptor.Builder()
        devices.forEach { device ->
            val detail =
                listOfNotNull(device.manufacturer, device.modelName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            providerDescriptor.addRoute(
                MediaRouteDescriptor
                    .Builder(routeId(device), device.name)
                    .setDescription(listOf("DLNA / UPnP AV", detail).filter { it.isNotBlank() }.joinToString(" · "))
                    .setEnabled(true)
                    .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
                    .setDeviceType(MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER)
                    .setVolumeHandling(
                        if (device.renderingControlUrl != null) {
                            MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE
                        } else {
                            MediaRouter.RouteInfo.PLAYBACK_VOLUME_FIXED
                        },
                    ).setVolumeMax(100)
                    .setVolume(50)
                    .addControlFilter(filter)
                    .build(),
            )
        }
        descriptor = providerDescriptor.build()
    }

    private fun routeId(device: DlnaDevice): String = "dlna:${device.id.hashCode().toUInt().toString(16)}"

    companion object {
        private const val TAG = "DlnaMediaRouteProvider"
    }
}

private class DlnaRouteController(
    private val device: DlnaDevice,
    private val scope: CoroutineScope,
) : MediaRouteProvider.RouteController() {
    override fun onSelect() {
        endCurrentCastSession()
        DlnaSessionBridge.connect(device)
    }

    override fun onUnselect(reason: Int) {
        DlnaSessionBridge.disconnect()
    }

    override fun onSetVolume(volume: Int) {
        scope.launch {
            runCatching { DlnaControlPoint(device).setVolume(volume.coerceIn(0, 100) / 100f) }
                .onFailure { error -> Logger.e(TAG, "Failed to set DLNA volume: ${error.message}", error) }
        }
    }

    override fun onUpdateVolume(delta: Int) {
        // UPnP AV exposes absolute volume. MediaRouter's controller dialog calls onSetVolume for
        // slider changes; relative hardware-key updates remain owned by the playback service.
    }

    companion object {
        private const val TAG = "DlnaRouteController"
    }
}
