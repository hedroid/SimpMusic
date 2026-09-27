package org.simpmusic.cast

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

interface DlnaSessionHandler {
    fun connect(device: DlnaDevice)

    fun disconnect()
}

object DlnaSessionBridge {
    fun register(handler: DlnaSessionHandler) = Unit

    fun unregister(handler: DlnaSessionHandler) = Unit

    fun updateActiveDevice(device: DlnaDevice?) = Unit
}

class DlnaControlPoint(val device: DlnaDevice) {
    data class PositionInfo(val positionMs: Long = 0, val durationMs: Long = 0)

    enum class TransportState { PLAYING, PAUSED, STOPPED, TRANSITIONING, UNKNOWN }

    suspend fun setMedia(url: String, title: String?, artist: String?, album: String?, artworkUrl: String?, mimeType: String) = Unit

    suspend fun play() = Unit

    suspend fun pause() = Unit

    suspend fun stop() = Unit

    suspend fun seek(positionMs: Long) = Unit

    suspend fun positionInfo(): PositionInfo = PositionInfo()

    suspend fun transportState(): TransportState = TransportState.UNKNOWN

    suspend fun setVolume(volume: Float) = Unit
}
