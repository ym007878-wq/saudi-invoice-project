package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ZatcaInvoiceDao {
    @Query("SELECT * FROM zatca_invoices ORDER BY id DESC")
    fun getAllInvoices(): Flow<List<ZatcaInvoiceEntity>>

    @Query("SELECT COUNT(*) FROM zatca_invoices WHERE rawBase64 = :rawBase64")
    suspend fun countByRawBase64(rawBase64: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: ZatcaInvoiceEntity): Long

    @Query("DELETE FROM zatca_invoices WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM zatca_invoices")
    suspend fun clearAll()
}
