package com.example.ui.components

import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ZatcaGreenPrimary
import java.io.ByteArrayOutputStream

@Composable
fun TextPasteTab(
    isProcessing: Boolean,
    onSubmitText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "لصق نصوص Base64 مباشرة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedButton(
                onClick = {
                    textInput = generateSampleZatcaBase64()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("insert_sample_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = ZatcaGreenPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "إدراج عينات",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZatcaGreenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = "يمكنك لصق كود واحد أو عدة أكواد (كود واحد لكل سطر). يدعم رموز المرحلة الأولى والثانية.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .testTag("base64_text_input"),
            placeholder = {
                Text(
                    text = "الصق نص الفاتورة المشفر بـ Base64 هنا...\nمثال: ARHYcmlrYXQgQWxr...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                focusedIndicatorColor = ZatcaGreenPrimary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onSubmitText(textInput)
                    }
                },
                enabled = textInput.isNotBlank() && !isProcessing,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZatcaGreenPrimary),
                modifier = Modifier
                    .weight(1f)
                    .testTag("submit_text_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "فحص وفك التشفير",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (textInput.isNotEmpty()) {
                OutlinedButton(
                    onClick = { textInput = "" },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("clear_text_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "مسح النص",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Generates two sample standard Saudi ZATCA TLV Base64 strings
 * for testing (one Phase 1 and one Phase 2).
 */
private fun generateSampleZatcaBase64(): String {
    val sample1 = createTlv(
        seller = "شركة تقنية المستقبل لحلول الأعمال",
        vatNo = "310123456789003",
        timestamp = "2024-10-28T09:15:00Z",
        total = "1150.00",
        vat = "150.00"
    )

    val sample2 = createTlv(
        seller = "مؤسسة الرياض التجارية والتوزيع",
        vatNo = "300987654321003",
        timestamp = "2024-10-27T16:40:00Z",
        total = "575.00",
        vat = "75.00",
        phase2Sig = "MEQCIBx7K89jLz0="
    )

    return "$sample1\n$sample2"
}

private fun createTlv(
    seller: String,
    vatNo: String,
    timestamp: String,
    total: String,
    vat: String,
    phase2Sig: String? = null
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

    if (phase2Sig != null) {
        writeTag(7, phase2Sig)
    }

    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}
