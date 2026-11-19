package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

class FrameCertificateTest {
    @Test fun certificateIsSelfSignedNonCaWithStableAndUsbIdentities() {
        val cert=ManagementTlsFixture.certificate
        cert.verify(ManagementTlsFixture.pair.public)
        cert.checkValidity()
        assertEquals(-1,cert.basicConstraints)
        val names=cert.subjectAlternativeNames.map { it[1].toString() }
        assertTrue(names.contains("framerevive-0123abcd.local")); assertTrue(names.contains("127.0.0.1"))
        assertTrue(cert.keyUsage[0]); assertFalse(cert.keyUsage[5])
        assertThrows(IllegalArgumentException::class.java) { FrameCertificate.create(ManagementTlsFixture.pair,"evil.example",null) }
    }
    @Test fun validityUsesUtcTimeForDatesBefore2050() {
        val near=FrameCertificate.time(2_085_000_000_000L)   // 2036
        assertEquals(0x17,near[0].toInt() and 0xff)
        assertEquals(13,near[1].toInt())
        assertTrue(String(near,2,13,Charsets.US_ASCII).matches(Regex("\\d{12}Z")))
        val far=FrameCertificate.time(4_102_444_800_000L)    // 2100
        assertEquals(0x18,far[0].toInt() and 0xff)
        assertEquals(15,far[1].toInt())
    }
    @Test fun encodedCertificateCarriesAUtcTimeNotBefore() {
        val cert=ManagementTlsFixture.certificate
        val stamp=java.text.SimpleDateFormat("yyMMddHHmmss'Z'",java.util.Locale.US)
            .apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }.format(cert.notBefore)
        val encoded=String(cert.encoded,Charsets.ISO_8859_1)
        val at=encoded.indexOf(stamp)
        assertTrue("notBefore must be present",at>0)
        assertEquals(13,encoded[at-1].code and 0xff)          // DER length of the 13-byte UTCTime
        assertEquals(0x17,encoded[at-2].code and 0xff)        // ...preceded by the UTCTime tag
    }
    @Test fun browserMustTrustTheSpecificDeviceBeforeSendingHttp() {
        FrameRemote("key",{"secret"},{"saved"},ManagementTlsFixture.listener()).use { s ->
            val socket=SSLContext.getDefault().socketFactory.createSocket("127.0.0.1",s.port) as SSLSocket
            socket.use { it.soTimeout=2000; assertThrows(SSLException::class.java) { it.startHandshake() } }
        }
    }
}
