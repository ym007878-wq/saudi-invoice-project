package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.QrCodeImageDecoder
import com.example.data.ZatcaDecoder
import com.example.data.ZatcaInvoiceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class InvoiceSummary(
    val count: Int = 0,
    val totalVat: Double = 0.0,
    val grandTotal: Double = 0.0,
    val totalBeforeVat: Double = 0.0
)

enum class InputTab {
    CAMERA,
    IMAGES,
    TEXT
}

sealed class UiNotification {
    data class Success(val message: String) : UiNotification()
    data class Warning(val message: String) : UiNotification()
    data class Error(val message: String) : UiNotification()
}

class ZatcaViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.invoiceDao()

    val invoices: StateFlow<List<ZatcaInvoiceEntity>> = dao.getAllInvoices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val summary: StateFlow<InvoiceSummary> = invoices.map { list ->
        val count = list.size
        val totalVat = list.sumOf { it.vatAmount }
        val grandTotal = list.sumOf { it.totalWithVat }
        val totalBeforeVat = list.sumOf { it.amountBeforeVat }
        InvoiceSummary(count, totalVat, grandTotal, totalBeforeVat)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvoiceSummary()
    )

    private val _currentTab = MutableStateFlow(InputTab.CAMERA)
    val currentTab: StateFlow<InputTab> = _currentTab.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _notification = MutableSharedFlow<UiNotification>()
    val notification: SharedFlow<UiNotification> = _notification.asSharedFlow()

    // Scan trigger event for UI animation & haptic feedback
    private val _scanFlashEvent = MutableSharedFlow<Unit>()
    val scanFlashEvent: SharedFlow<Unit> = _scanFlashEvent.asSharedFlow()

    // Cache of recent scans in this session to prevent repeated continuous alerts
    private val recentScansCache = mutableSetOf<String>()

    fun setTab(tab: InputTab) {
        _currentTab.value = tab
    }

    fun toggleTorch() {
        _isTorchOn.value = !_isTorchOn.value
    }

    /**
     * Process raw QR text from Camera scanner.
     * Returns true if successfully recognized as a new invoice.
     */
    fun processCameraScan(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || recentScansCache.contains(trimmed)) {
            return false
        }

        when (val result = ZatcaDecoder.decode(trimmed)) {
            is ZatcaDecoder.DecodeResult.Success -> {
                recentScansCache.add(trimmed)
                viewModelScope.launch(Dispatchers.IO) {
                    val count = dao.countByRawBase64(trimmed)
                    if (count == 0) {
                        dao.insertInvoice(result.entity)
                        triggerHapticFeedback()
                        _scanFlashEvent.emit(Unit)
                        _notification.emit(
                            UiNotification.Success("تم مسح فاتورة: ${result.entity.sellerName} (${"%.2f".format(result.entity.totalWithVat)} ر.س)")
                        )
                    } else {
                        _notification.emit(UiNotification.Warning("تم مسح هذه الفاتورة مسبقاً (تم تجاهل التكرار)"))
                    }
                }
                return true
            }
            is ZatcaDecoder.DecodeResult.Error -> {
                // Ignore silent malformed QR scans during continuous video scanning
                return false
            }
        }
    }

    /**
     * Process a single or multiple images selected from gallery.
     */
    fun processImageUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            var addedCount = 0
            var duplicateCount = 0
            var failedCount = 0

            for (uri in uris) {
                val decodedQr = QrCodeImageDecoder.decodeFromUri(context, uri)
                if (decodedQr != null) {
                    when (val result = ZatcaDecoder.decode(decodedQr)) {
                        is ZatcaDecoder.DecodeResult.Success -> {
                            val count = dao.countByRawBase64(result.entity.rawBase64)
                            if (count == 0) {
                                dao.insertInvoice(result.entity)
                                addedCount++
                            } else {
                                duplicateCount++
                            }
                        }
                        is ZatcaDecoder.DecodeResult.Error -> {
                            failedCount++
                        }
                    }
                } else {
                    failedCount++
                }
            }

            _isProcessing.value = false

            if (addedCount > 0) {
                triggerHapticFeedback()
                _scanFlashEvent.emit(Unit)
                _notification.emit(
                    UiNotification.Success("تم استخراج $addedCount فاتورة بنجاح" +
                            if (duplicateCount > 0) " (تجاهل $duplicateCount مكررة)" else "")
                )
            } else if (duplicateCount > 0 && failedCount == 0) {
                _notification.emit(UiNotification.Warning("جميع الفواتير المحددة مضافة مسبقاً"))
            } else {
                _notification.emit(
                    UiNotification.Error(ZatcaDecoder.ERROR_NOT_ZATCA)
                )
            }
        }
    }

    /**
     * Process text input with one or multiple Base64 strings.
     */
    fun processTextPaste(rawText: String) {
        val lines = rawText.split("\n", "\r", ",").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            viewModelScope.launch {
                _notification.emit(UiNotification.Error("الرجاء لصق نص كود الفاتورة"))
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            var addedCount = 0
            var duplicateCount = 0
            var errorCount = 0

            for (line in lines) {
                when (val result = ZatcaDecoder.decode(line)) {
                    is ZatcaDecoder.DecodeResult.Success -> {
                        val count = dao.countByRawBase64(result.entity.rawBase64)
                        if (count == 0) {
                            dao.insertInvoice(result.entity)
                            addedCount++
                        } else {
                            duplicateCount++
                        }
                    }
                    is ZatcaDecoder.DecodeResult.Error -> {
                        errorCount++
                    }
                }
            }

            _isProcessing.value = false

            if (addedCount > 0) {
                triggerHapticFeedback()
                _scanFlashEvent.emit(Unit)
                _notification.emit(
                    UiNotification.Success("تمت إضافة $addedCount فاتورة بنجاح" +
                            if (duplicateCount > 0) " ($duplicateCount مكررة)" else "")
                )
            } else if (duplicateCount > 0 && errorCount == 0) {
                _notification.emit(UiNotification.Warning("جميع الأكواد المضافة موجودة مسبقاً"))
            } else {
                _notification.emit(UiNotification.Error(ZatcaDecoder.ERROR_NOT_ZATCA))
            }
        }
    }

    fun deleteInvoice(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteById(id)
            _notification.emit(UiNotification.Success("تم حذف الفاتورة"))
        }
    }

    fun clearAllInvoices() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAll()
            recentScansCache.clear()
            _notification.emit(UiNotification.Success("تم مسح جميع الفواتير"))
        }
    }

    fun copyForExcel(context: Context) {
        val currentList = invoices.value
        if (currentList.isEmpty()) {
            viewModelScope.launch {
                _notification.emit(UiNotification.Warning("لا توجد فواتير للنسخ"))
            }
            return
        }

        // Tab-separated with Arabic headers as required:
        // التاريخ والوقت, المورد, الرقم الضريبي, قبل الضريبة, الضريبة, الإجمالي
        val sb = StringBuilder()
        sb.append("التاريخ والوقت\tاسم المورد\tالرقم الضريبي\tالمبلغ قبل الضريبة\tمبلغ الضريبة\tالإجمالي شامل الضريبة\tمعدل الضريبة\tنوع الفاتورة\n")

        for (inv in currentList) {
            val rateText = if (inv.isVatRateStandard) "15%" else "${"%.1f".format(inv.calculatedVatRate)}%"
            val phaseText = if (inv.isPhase2) "المرحلة 2 (مشفرة)" else "المرحلة 1"
            sb.append("${inv.timestamp}\t${inv.sellerName}\t${inv.vatNumber}\t${"%.2f".format(inv.amountBeforeVat)}\t${"%.2f".format(inv.vatAmount)}\t${"%.2f".format(inv.totalWithVat)}\t$rateText\t$phaseText\n")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ZATCA Excel TSV", sb.toString())
        clipboard.setPrimaryClip(clip)

        viewModelScope.launch {
            _notification.emit(UiNotification.Success("تم نسخ بيانات الفواتير لـ Excel (جاهزة للصق مباشرة)"))
        }
    }

    fun copyJson(context: Context) {
        val currentList = invoices.value
        if (currentList.isEmpty()) {
            viewModelScope.launch {
                _notification.emit(UiNotification.Warning("لا توجد فواتير للنسخ"))
            }
            return
        }

        val jsonArray = JSONArray()
        for (inv in currentList) {
            val obj = JSONObject().apply {
                put("sellerName", inv.sellerName)
                put("vatNumber", inv.vatNumber)
                put("timestamp", inv.timestamp)
                put("amountBeforeVat", inv.amountBeforeVat)
                put("vatAmount", inv.vatAmount)
                put("totalWithVat", inv.totalWithVat)
                put("isVatNumberValid", inv.isVatNumberValid)
                put("calculatedVatRate", inv.calculatedVatRate)
                put("isVatRateStandard", inv.isVatRateStandard)
                put("isPhase2", inv.isPhase2)
                put("rawBase64", inv.rawBase64)
            }
            jsonArray.put(obj)
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ZATCA Invoices JSON", jsonArray.toString(2))
        clipboard.setPrimaryClip(clip)

        viewModelScope.launch {
            _notification.emit(UiNotification.Success("تم نسخ بيانات JSON إلى الحافظة"))
        }
    }

    fun shareExport(context: Context, isCsv: Boolean) {
        val currentList = invoices.value
        if (currentList.isEmpty()) {
            viewModelScope.launch {
                _notification.emit(UiNotification.Warning("لا توجد فواتير للمشاركة"))
            }
            return
        }

        val content: String
        val mimeType: String
        val title: String

        if (isCsv) {
            mimeType = "text/csv"
            title = "مشاركة فواتير زاتكا CSV"
            val sb = StringBuilder()
            // UTF-8 BOM so Excel opens Arabic correctly
            sb.append("\uFEFF")
            sb.append("التاريخ والوقت,اسم المورد,الرقم الضريبي,قبل الضريبة,ضريبة القيمة المضافة,الإجمالي,نسبة الضريبة,المرحلة\n")
            for (inv in currentList) {
                val seller = "\"${inv.sellerName.replace("\"", "\"\"")}\""
                val rate = if (inv.isVatRateStandard) "15%" else "${"%.1f".format(inv.calculatedVatRate)}%"
                val phase = if (inv.isPhase2) "مرحلة 2" else "مرحلة 1"
                sb.append("${inv.timestamp},$seller,${inv.vatNumber},${"%.2f".format(inv.amountBeforeVat)},${"%.2f".format(inv.vatAmount)},${"%.2f".format(inv.totalWithVat)},$rate,$phase\n")
            }
            content = sb.toString()
        } else {
            mimeType = "application/json"
            title = "مشاركة فواتير زاتكا JSON"
            val jsonArray = JSONArray()
            for (inv in currentList) {
                val obj = JSONObject().apply {
                    put("sellerName", inv.sellerName)
                    put("vatNumber", inv.vatNumber)
                    put("timestamp", inv.timestamp)
                    put("amountBeforeVat", inv.amountBeforeVat)
                    put("vatAmount", inv.vatAmount)
                    put("totalWithVat", inv.totalWithVat)
                    put("isVatNumberValid", inv.isVatNumberValid)
                    put("calculatedVatRate", inv.calculatedVatRate)
                    put("isPhase2", inv.isPhase2)
                }
                jsonArray.put(obj)
            }
            content = jsonArray.toString(2)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    private fun triggerHapticFeedback() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (_: Exception) {
            // Ignore if vibration not permitted or not supported
        }
    }
}
