package com.example.data

import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object ZatcaDecoder {

    const val ERROR_NOT_ZATCA = "الرمز ليس فاتورة إلكترونية سعودية"

    sealed class DecodeResult {
        data class Success(val entity: ZatcaInvoiceEntity) : DecodeResult()
        data class Error(val message: String) : DecodeResult()
    }

    /**
     * Decodes a raw QR content string into a ZatcaInvoiceEntity.
     * Handles Base64 standard, Base64 URL-safe, padding, and whitespace.
     */
    fun decode(rawInput: String): DecodeResult {
        val trimmed = rawInput.trim().replace("\\s+".toRegex(), "")
        if (trimmed.isEmpty()) {
            return DecodeResult.Error(ERROR_NOT_ZATCA)
        }

        val bytes = decodeBase64Safe(trimmed) ?: return DecodeResult.Error(ERROR_NOT_ZATCA)

        if (bytes.size < 4) {
            return DecodeResult.Error(ERROR_NOT_ZATCA)
        }

        val tagMap = mutableMapOf<Int, ByteArray>()
        var index = 0

        try {
            while (index + 2 <= bytes.size) {
                val tag = bytes[index].toInt() and 0xFF
                val length = bytes[index + 1].toInt() and 0xFF
                index += 2

                if (index + length > bytes.size) {
                    return DecodeResult.Error(ERROR_NOT_ZATCA)
                }

                val valueBytes = bytes.copyOfRange(index, index + length)
                index += length
                tagMap[tag] = valueBytes
            }
        } catch (_: Exception) {
            return DecodeResult.Error(ERROR_NOT_ZATCA)
        }

        // Required tags 1 to 5
        val tag1Bytes = tagMap[1] ?: return DecodeResult.Error(ERROR_NOT_ZATCA)
        val tag2Bytes = tagMap[2] ?: return DecodeResult.Error(ERROR_NOT_ZATCA)
        val tag3Bytes = tagMap[3] ?: return DecodeResult.Error(ERROR_NOT_ZATCA)
        val tag4Bytes = tagMap[4] ?: return DecodeResult.Error(ERROR_NOT_ZATCA)
        val tag5Bytes = tagMap[5] ?: return DecodeResult.Error(ERROR_NOT_ZATCA)

        val sellerName = String(tag1Bytes, Charsets.UTF_8).trim()
        val vatNumber = String(tag2Bytes, Charsets.UTF_8).trim()
        val timestamp = String(tag3Bytes, Charsets.UTF_8).trim()
        val totalStr = String(tag4Bytes, Charsets.UTF_8).trim()
        val vatStr = String(tag5Bytes, Charsets.UTF_8).trim()

        if (sellerName.isEmpty() || vatNumber.isEmpty() || timestamp.isEmpty()) {
            return DecodeResult.Error(ERROR_NOT_ZATCA)
        }

        val totalWithVat = totalStr.toDoubleOrNull() ?: return DecodeResult.Error(ERROR_NOT_ZATCA)
        val vatAmount = vatStr.toDoubleOrNull() ?: return DecodeResult.Error(ERROR_NOT_ZATCA)

        // Phase 2 tags (optional)
        val invoiceHash = tagMap[6]?.let { encodeHexOrBase64(it) }
        val digitalSignature = tagMap[7]?.let { encodeHexOrBase64(it) }
        val publicKey = tagMap[8]?.let { encodeHexOrBase64(it) }
        val cryptographicStamp = tagMap[9]?.let { encodeHexOrBase64(it) }

        val isPhase2 = (digitalSignature != null && digitalSignature.isNotEmpty()) ||
                (cryptographicStamp != null && cryptographicStamp.isNotEmpty())

        // Validation 1: VAT number matches /^3\d{13}3$/ (15 digits, starts and ends with 3)
        val isVatNumberValid = vatNumber.matches(Regex("^3\\d{13}3$"))

        // Validation 2: Amount calculation & VAT rate
        // VAT rate = tax / (total - tax), tolerance 0.5% around 15%
        val amountBeforeVat = totalWithVat - vatAmount
        val calculatedVatRate = if (amountBeforeVat > 0.001) {
            (vatAmount / amountBeforeVat) * 100.0
        } else {
            0.0
        }
        val isVatRateStandard = abs(calculatedVatRate - 15.0) <= 0.5

        // Validation 3: Timestamp is a valid date
        val isTimestampValid = validateIsoTimestamp(timestamp)

        val entity = ZatcaInvoiceEntity(
            rawBase64 = trimmed,
            sellerName = sellerName,
            vatNumber = vatNumber,
            timestamp = timestamp,
            totalWithVat = totalWithVat,
            vatAmount = vatAmount,
            amountBeforeVat = amountBeforeVat,
            invoiceHash = invoiceHash,
            digitalSignature = digitalSignature,
            publicKey = publicKey,
            cryptographicStamp = cryptographicStamp,
            isVatNumberValid = isVatNumberValid,
            calculatedVatRate = calculatedVatRate,
            isVatRateStandard = isVatRateStandard,
            isPhase2 = isPhase2,
            isTimestampValid = isTimestampValid
        )

        return DecodeResult.Success(entity)
    }

    private fun decodeBase64Safe(input: String): ByteArray? {
        val sanitized = input.replace('-', '+').replace('_', '/')
        val padded = when (sanitized.length % 4) {
            2 -> "$sanitized=="
            3 -> "$sanitized="
            else -> sanitized
        }

        return try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (_: Exception) {
            try {
                Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun encodeHexOrBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            bytes.joinToString("") { "%02x".format(it) }
        }
    }

    private fun validateIsoTimestamp(timestamp: String): Boolean {
        val formats = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.isLenient = false
                val date = sdf.parse(timestamp)
                if (date != null) return true
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return false
    }
}
