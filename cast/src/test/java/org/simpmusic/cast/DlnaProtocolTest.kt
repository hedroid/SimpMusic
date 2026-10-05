package org.simpmusic.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaProtocolTest {
    @Test
    fun `SSDP headers are parsed case-insensitively`() {
        val response =
            "HTTP/1.1 200 OK\r\n" +
                "LOCATION: http://192.168.1.20:1400/xml/device.xml\r\n" +
                "St: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

        val headers = DlnaDiscovery.parseSsdpHeaders(response)

        assertEquals("http://192.168.1.20:1400/xml/device.xml", headers["location"])
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", headers["st"])
    }

    @Test
    fun `device description resolves renderer control URLs`() {
        val xml =
            """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Living Room TV</friendlyName>
                <manufacturer>Example</manufacturer>
                <modelName>TV 1</modelName>
                <UDN>uuid:renderer-1</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/transport</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>rendering/control</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """.trimIndent().toByteArray()

        val device = DlnaDiscovery.parseDeviceDescription("http://192.168.1.20:1400/xml/device.xml", xml)!!

        assertEquals("uuid:renderer-1", device.id)
        assertEquals("Living Room TV", device.name)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", device.avTransportServiceType)
        assertEquals("http://192.168.1.20:1400/upnp/control/transport", device.avTransportControlUrl)
        assertEquals("http://192.168.1.20:1400/xml/rendering/control", device.renderingControlUrl)
    }

    @Test
    fun `device description honors URLBase`() {
        val xml =
            """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <URLBase>http://192.168.1.20:1500/upnp/</URLBase>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Bedroom Speaker</friendlyName>
                <UDN>uuid:renderer-2</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>transport</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """.trimIndent().toByteArray()

        val device = DlnaDiscovery.parseDeviceDescription("http://192.168.1.20:1400/xml/device.xml", xml)!!

        assertEquals("http://192.168.1.20:1500/upnp/transport", device.avTransportControlUrl)
    }

    @Test
    fun `vendor speaker is accepted by AVTransport capability`() {
        val xml =
            """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:vendor-example:device:SmartSpeaker:1</deviceType>
                <friendlyName>Living Room Speaker</friendlyName>
                <manufacturer>Vendor</manufacturer>
                <UDN>uuid:vendor-speaker-1</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:avtransport:1</serviceType>
                    <controlURL>/control/avtransport</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
            """.trimIndent().toByteArray()

        val device = DlnaDiscovery.parseDeviceDescription("http://192.168.1.30:8080/device.xml", xml)!!

        assertEquals("Living Room Speaker", device.name)
        assertEquals("http://192.168.1.30:8080/control/avtransport", device.avTransportControlUrl)
    }

    @Test
    fun `HappyCast malformed urn control paths are treated as URLBase paths`() {
        val xml =
            """
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Living Room TV (lebo)</friendlyName>
                <manufacturer>LEBO</manufacturer>
                <modelName>HappyCast</modelName>
                <UDN>uuid:happycast-renderer</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>_urn:schemas-upnp-org:service:AVTransport_control</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>_urn:schemas-upnp-org:service:RenderingControl_control</controlURL>
                  </service>
                </serviceList>
              </device>
              <URLBase>http://192.168.1.40:49152</URLBase>
            </root>
            """.trimIndent().toByteArray()

        val device = DlnaDiscovery.parseDeviceDescription("http://192.168.1.40:49152/description.xml", xml)!!

        assertEquals(
            "http://192.168.1.40:49152/_urn:schemas-upnp-org:service:AVTransport_control",
            device.avTransportControlUrl,
        )
        assertEquals(
            "http://192.168.1.40:49152/_urn:schemas-upnp-org:service:RenderingControl_control",
            device.renderingControlUrl,
        )
    }

    @Test
    fun `DIDL metadata escapes text and keeps audio protocol info`() {
        val didl =
            DlnaControlPoint.buildDidlMetadata(
                url = "https://example.com/audio?a=1&b=2",
                title = "A < B",
                artist = "Artist & Friend",
                album = null,
                artworkUrl = null,
                mimeType = "audio/mp4",
            )

        assertTrue(didl.contains("A &lt; B"))
        assertTrue(didl.contains("Artist &amp; Friend"))
        assertTrue(didl.contains("http-get:*:audio/mp4:*"))
        assertTrue(didl.contains("audio?a=1&amp;b=2"))
    }

    @Test
    fun `UPnP time conversion is stable`() {
        assertEquals(3_723_000L, DlnaControlPoint.parseDuration("01:02:03.250"))
        assertEquals("01:02:03", DlnaControlPoint.formatDuration(3_723_999L))
        assertEquals(0L, DlnaControlPoint.parseDuration("NOT_IMPLEMENTED"))
    }
}
