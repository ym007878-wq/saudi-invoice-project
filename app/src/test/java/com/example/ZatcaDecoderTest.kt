package com.example

import android.util.Base64
import com.example.data.ZatcaDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ZatcaDecoderTest {

    private fun createTlvBase64(
        seller: String,
        vatNo: String,
        timestamp: String,
        total: String,
        vat: String,
        signature: String? = null
    ): String {
        val baos = ByteArrayOutputStream()
        fun writeTag(tag: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            baos.write(tag)
            baos.write(bytes.size)
            baos.write(bytes)
        }

        writeTag(1, seller)
        writeTag(2, vatNo)
        writeTag(3, timestamp)
        writeTag(4, total)
        writeTag(5, vat)
        if (signature != null) {
            writeTag(7, signature)
        }

        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    @Test
    fun testValidPhase1ZatcaInvoice() {
        val base64 = createTlvBase64(
            seller = "شركة المراعي للتجارة",
            vatNo = "310123456789003",
            timestamp = "2024-05-10T12:00:00Z",
            total = "115.00",
            vat = "15.00"
        )

        val result = ZatcaDecoder.decode(base64)
        assertTrue(result is ZatcaDecoder.DecodeResult.Success)
        val entity = (result as ZatcaDecoder.DecodeResult.Success).entity

        assertEquals("شركة المراعي للتجارة", entity.sellerName)
        assertEquals("310123456789003", entity.vatNumber)
        assertTrue(entity.isVatNumberValid)
        assertEquals(115.00, entity.totalWithVat, 0.001)
        assertEquals(15.00, entity.vatAmount, 0.001)
        assertEquals(100.00, entity.amountBeforeVat, 0.001)
        assertEquals(15.0, entity.calculatedVatRate, 0.001)
        assertTrue(entity.isVatRateStandard)
        assertFalse(entity.isPhase2)
        assertTrue(entity.isTimestampValid)
    }

    @Test
    fun testValidPhase2InvoiceWithSignature() {
        val base64 = createTlvBase64(
            seller = "أسواق العثيم",
            vatNo = "300123456700003",
            timestamp = "2024-06-01T08:30:00",
            total = "230.00",
            vat = "30.00",
            signature = "MEQCIBx7K89jLz0="
        )

        val result = ZatcaDecoder.decode(base64)
        assertTrue(result is ZatcaDecoder.DecodeResult.Success)
        val entity = (result as ZatcaDecoder.DecodeResult.Success).entity

        assertTrue(entity.isPhase2)
        assertTrue(entity.isVatNumberValid)
        assertTrue(entity.isVatRateStandard)
    }

    @Test
    fun testInvalidVatNumberDetection() {
        // VAT number with only 10 digits or not starting/ending with 3
        val base64 = createTlvBase64(
            seller = "متجر الاختبار",
            vatNo = "1234567890",
            timestamp = "2024-05-10T12:00:00Z",
            total = "115.00",
            vat = "15.00"
        )

        val result = ZatcaDecoder.decode(base64)
        assertTrue(result is ZatcaDecoder.DecodeResult.Success)
        val entity = (result as ZatcaDecoder.DecodeResult.Success).entity

        assertFalse(entity.isVatNumberValid)
    }

    @Test
    fun testNonStandardVatRateDetection() {
        // Total 110, VAT 10 -> before VAT = 100 -> 10% rate
        val base64 = createTlvBase64(
            seller = "مؤسسة التوريدات",
            vatNo = "310123456789003",
            timestamp = "2024-05-10T12:00:00Z",
            total = "110.00",
            vat = "10.00"
        )

        val result = ZatcaDecoder.decode(base64)
        assertTrue(result is ZatcaDecoder.DecodeResult.Success)
        val entity = (result as ZatcaDecoder.DecodeResult.Success).entity

        assertFalse(entity.isVatRateStandard)
        assertEquals(10.0, entity.calculatedVatRate, 0.001)
    }

    @Test
    fun testInvalidOrMalformedTlvReturnsArabicError() {
        val result = ZatcaDecoder.decode("SGVsbG8gV29ybGQ=")
        assertTrue(result is ZatcaDecoder.DecodeResult.Error)
        assertEquals(ZatcaDecoder.ERROR_NOT_ZATCA, (result as ZatcaDecoder.DecodeResult.Error).message)
    }
}
