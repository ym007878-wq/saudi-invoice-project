package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zatca_invoices")
data class ZatcaInvoiceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawBase64: String,
    val sellerName: String,
    val vatNumber: String,
    val timestamp: String,
    val totalWithVat: Double,
    val vatAmount: Double,
    val amountBeforeVat: Double,
    val invoiceHash: String? = null,
    val digitalSignature: String? = null,
    val publicKey: String? = null,
    val cryptographicStamp: String? = null,
    val isVatNumberValid: Boolean,
    val calculatedVatRate: Double,
    val isVatRateStandard: Boolean,
    val isPhase2: Boolean,
    val isTimestampValid: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
