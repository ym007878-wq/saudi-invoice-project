package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ZatcaInvoiceEntity
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import com.example.ui.theme.ZatcaGreenPrimary
import com.example.ui.theme.ZatcaPhase2Blue
import com.example.ui.theme.ZatcaPhase2Container
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InvoiceCard(
    invoice: ZatcaInvoiceEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("invoice_card_${invoice.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Store Name and Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ZatcaGreenPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = null,
                            tint = ZatcaGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = invoice.sellerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = invoice.timestamp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_invoice_${invoice.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف الفاتورة",
                        tint = StatusError.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Validation Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // VAT Number Badge
                if (invoice.isVatNumberValid) {
                    StatusBadge(
                        text = "رقم ضريبي سليم (${invoice.vatNumber})",
                        icon = Icons.Default.CheckCircle,
                        textColor = StatusSuccess,
                        bgColor = StatusSuccessBg
                    )
                } else {
                    StatusBadge(
                        text = "رقم ضريبي غير مطابق (${invoice.vatNumber})",
                        icon = Icons.Default.ErrorOutline,
                        textColor = StatusError,
                        bgColor = StatusErrorBg
                    )
                }

                // VAT Rate Badge
                if (invoice.isVatRateStandard) {
                    StatusBadge(
                        text = "الضريبة 15% قياسية",
                        icon = Icons.Default.CheckCircle,
                        textColor = StatusSuccess,
                        bgColor = StatusSuccessBg
                    )
                } else {
                    val rateFormatted = String.format(Locale.US, "%.1f", invoice.calculatedVatRate)
                    StatusBadge(
                        text = "نسبة الضريبة: $rateFormatted% (غير قياسية)",
                        icon = Icons.Default.WarningAmber,
                        textColor = StatusWarning,
                        bgColor = StatusWarningBg
                    )
                }

                // Phase 2 Signature Badge
                if (invoice.isPhase2) {
                    StatusBadge(
                        text = "المرحلة الثانية: توقيع رقمي",
                        icon = Icons.Default.Security,
                        textColor = ZatcaPhase2Blue,
                        bgColor = ZatcaPhase2Container
                    )
                } else {
                    StatusBadge(
                        text = "المرحلة الأولى (أساسية)",
                        icon = Icons.Default.Info,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        bgColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // Financial Breakdown Rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FinancialDetailItem(
                    title = "قبل الضريبة",
                    amount = invoice.amountBeforeVat
                )
                FinancialDetailItem(
                    title = "مبلغ الضريبة (15%)",
                    amount = invoice.vatAmount,
                    highlightColor = ZatcaGreenPrimary
                )
                FinancialDetailItem(
                    title = "الإجمالي شامل الضريبة",
                    amount = invoice.totalWithVat,
                    isTotal = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Regulatory Disclaimer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "ملاحظة: هذا الفحص لا يوثق التوقيع الرقمي، والتثبت الرسمي يتم عبر تطبيق هيئة الزكاة والضريبة والجمارك (زاتكا).",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    icon: ImageVector,
    textColor: Color,
    bgColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun FinancialDetailItem(
    title: String,
    amount: Double,
    highlightColor: Color? = null,
    isTotal: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%,.2f", amount),
                style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (isTotal) FontWeight.Bold else FontWeight.SemiBold,
                color = highlightColor ?: (if (isTotal) ZatcaGreenPrimary else MaterialTheme.colorScheme.onSurface)
            )
            Text(
                text = "ر.س",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 1.dp)
            )
        }
    }
}
