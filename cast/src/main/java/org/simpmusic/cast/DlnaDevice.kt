package org.simpmusic.cast

/** A UPnP AV MediaRenderer and the control endpoints advertised by its device description. */
data class DlnaDevice(
    val id: String,
    val name: String,
    val modelName: String?,
    val manufacturer: String?,
    val locationUrl: String,
    val avTransportServiceType: String,
    val avTransportControlUrl: String,
    val renderingControlServiceType: String?,
    val renderingControlUrl: String?,
)

/** Implemented by the playback module so the UI/discovery module stays independent of Media3. */
interface DlnaSessionHandler {
    fun connect(device: DlnaDevice)

    fun disconnect()
}

/** Process-local bridge between the route picker and the playback service. */
object DlnaSessionBridge {
    @Volatile
    private var handler: DlnaSessionHandler? = null

    private var pendingDevice: DlnaDevice? = null

    @Volatile
    var activeDevice: DlnaDevice? = null
        private set

    @Synchronized
    fun register(handler: DlnaSessionHandler) {
        this.handler = handler
        pendingDevice?.let { device ->
            pendingDevice = null
            handler.connect(device)
        }
    }

    @Synchronized
    fun unregister(handler: DlnaSessionHandler) {
        if (this.handler === handler) this.handler = null
    }

    @Synchronized
    fun connect(device: DlnaDevice) {
        activeDevice = device
        val currentHandler = handler
        if (currentHandler == null) {
            pendingDevice = device
        } else {
            currentHandler.connect(device)
        }
    }

    @Synchronized
    fun disconnect() {
        pendingDevice = null
        activeDevice = null
        handler?.disconnect()
    }

    fun updateActiveDevice(device: DlnaDevice?) {
        activeDevice = device
    }
}
