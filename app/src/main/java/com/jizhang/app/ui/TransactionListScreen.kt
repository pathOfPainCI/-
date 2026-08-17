package com.jizhang.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.repo.TransactionUi
import com.jizhang.app.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionListScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    importViewModel: ImportViewModel = hiltViewModel(),
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val reviewCount by viewModel.needsReviewCount.collectAsStateWithLifecycle()
    val importState by importViewModel.state.collectAsStateWithLifecycle()

    // SAF 文件选择：无需存储权限
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { importViewModel.loadAndParse(it) }
    }

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "明细",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = {
                fileLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream"))
            }) {
                Text("导入 CSV")
            }
            Spacer(Modifier.width(4.dp))
        }

        if (reviewCount > 0) {
            Text(
                text = "有 $reviewCount 条记录待核对（金额/方向解析不确定）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无记录\n支付后通知将自动记一笔，也可点右上角「导入 CSV」对账",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transactions, key = { it.id }) { t ->
                    TransactionRow(t)
                }
            }
        }
    }

    ImportDialog(
        state = importState,
        onConfirm = { importViewModel.confirmImport() },
        onDismiss = { importViewModel.dismiss() },
    )
}

@Composable
private fun ImportDialog(
    state: ImportState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Preview -> {
            val parsed = state.parsed
            if (parsed.error != null) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("导入失败") },
                    text = { Text(parsed.error) },
                    confirmButton = {
                        TextButton(onClick = onDismiss) { Text("好的") }
                    },
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    title = { Text("解析完成，确认导入？") },
                    text = {
                        Text(
                            "解析出 " + parsed.transactions.size + " 条记录\n" +
                                "跳过坏行 " + parsed.skippedLines + " 条\n\n" +
                                "已存在的记录将自动去重跳过。"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onConfirm) { Text("导入") }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismiss) { Text("取消") }
                    },
                )
            }
        }
        is ImportState.Done -> {
            val result = state.result
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导入完成") },
                text = {
                    Text(
                        if (result.error != null) {
                            "导入失败：" + result.error
                        } else {
                            "新增 " + result.inserted + " 条，去重跳过 " + result.duplicated + " 条"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("好的") }
                },
            )
        }
        is ImportState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("导入失败") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("好的") }
                },
            )
        }
    }
}

@Composable
private fun TransactionRow(t: TransactionUi) {
    val amountColor = when (t.type) {
        TransactionType.EXPENSE -> Color(0xFFD32F2F)
        TransactionType.INCOME -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t.merchant ?: "（未识别商户）",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = listOfNotNull(t.categoryName, formatTime(t.transactionTime)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            if (t.needsReview) {
                Text(
                    text = "待核对",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = formatAmount(t.amountCents, t.type),
            style = MaterialTheme.typography.titleMedium,
            color = amountColor,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

private fun formatAmount(cents: Long, type: TransactionType): String {
    val sign = when (type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.INCOME -> "+"
        else -> ""
    }
    return String.format(Locale.US, "%s¥%.2f", sign, cents / 100.0)
}

private fun formatTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}
