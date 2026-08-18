package com.jizhang.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.repo.TransactionUi
import com.jizhang.app.domain.model.TransactionSource
import com.jizhang.app.domain.model.TransactionType
import com.jizhang.app.ui.MainViewModel.RangeMode
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

    var selectedTx by remember { mutableStateOf<TransactionUi?>(null) }
    var query by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "明细",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = {
                fileLauncher.launch(arrayOf(
                    "text/*",
                    "text/comma-separated-values",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/zip",
                    "application/octet-stream",
                ))
            }) {
                Text("导入 CSV")
            }
            Spacer(Modifier.width(4.dp))
        }

        // 筛选模式
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = viewModel.filterValue.mode == RangeMode.ALL,
                onClick = { viewModel.setMode(RangeMode.ALL) },
                label = { Text("全部") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = viewModel.filterValue.mode == RangeMode.DAY,
                onClick = { viewModel.setMode(RangeMode.DAY) },
                label = { Text("日") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = viewModel.filterValue.mode == RangeMode.MONTH,
                onClick = { viewModel.setMode(RangeMode.MONTH) },
                label = { Text("月") },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = viewModel.filterValue.mode == RangeMode.YEAR,
                onClick = { viewModel.setMode(RangeMode.YEAR) },
                label = { Text("年") },
            )
        }

        // 日期导航 + 标题
        if (viewModel.filterValue.mode != RangeMode.ALL && query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.shift(-1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一个")
                }
                Text(
                    text = viewModel.rangeTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { viewModel.shift(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一个")
                }
            }
        }

        // 搜索框
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.setQuery(it)
            },
            placeholder = { Text("搜索商户 / 备注 / 金额") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // 当前范围汇总
        val expenseCents = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        val incomeCents = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        Text(
            text = formatCents(expenseCents) + " 支出　" + formatCents(incomeCents) + " 收入　共 " + transactions.size + " 笔",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (reviewCount > 0) {
            Text(
                text = "有 $reviewCount 条记录待核对（金额/方向解析不确定）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isNotBlank()) "没有找到匹配的记录" else "暂无记录\n支付后通知将自动记一笔，也可点右上角「导入 CSV」对账",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transactions, key = { it.id }) { t ->
                    TransactionRow(t, onClick = { selectedTx = t })
                }
            }
        }
    }

    selectedTx?.let { tx ->
        TransactionDetailDialog(
            t = tx,
            onDismiss = { selectedTx = null },
            onCopy = {
                clipboard.setText(AnnotatedString(tx.note ?: ""))
            },
        )
    }

    ImportDialog(
        state = importState,
        onConfirm = { importViewModel.confirmImport() },
        onDismiss = { importViewModel.dismiss() },
    )
}

private fun formatCents(cents: Long): String = String.format(Locale.US, "¥%.2f", cents / 100.0)

@Composable
private fun TransactionDetailDialog(
    t: TransactionUi,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    val amountColor = when (t.type) {
        TransactionType.EXPENSE -> Color(0xFFD32F2F)
        TransactionType.INCOME -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.outline
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.needsReview) "待核对记录" else "交易详情") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = formatAmount(t.amountCents, t.type),
                    style = MaterialTheme.typography.headlineSmall,
                    color = amountColor,
                )
                Spacer(Modifier.height(8.dp))
                DetailRow("商户", t.merchant ?: merchantFallback(t.source))
                DetailRow("分类", t.categoryName ?: "未分类")
                DetailRow("时间", formatFullTime(t.transactionTime))
                DetailRow("来源", sourceName(t.source))
                if (t.needsReview) {
                    DetailRow("状态", "待核对（解析不完整）")
                }
                if (t.note != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "通知原文：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = t.note,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            if (t.note != null) {
                TextButton(onClick = onCopy) { Text("复制原文") }
            }
        },
    )
}

/** 通知无商户信息时按来源显示平台名 */
private fun merchantFallback(s: TransactionSource): String = when (s) {
    TransactionSource.WECHAT_NOTIFICATION, TransactionSource.WECHAT_CSV -> "微信支付"
    TransactionSource.ALIPAY_NOTIFICATION, TransactionSource.ALIPAY_CSV -> "支付宝"
    else -> "未知商户"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label + "：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun sourceName(s: TransactionSource): String = when (s) {
    TransactionSource.WECHAT_NOTIFICATION -> "微信通知"
    TransactionSource.ALIPAY_NOTIFICATION -> "支付宝通知"
    TransactionSource.WECHAT_CSV -> "微信账单导入"
    TransactionSource.ALIPAY_CSV -> "支付宝账单导入"
    TransactionSource.MANUAL -> "手动录入"
}

private fun formatFullTime(epochMs: Long): String {
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
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
                            "新增 " + result.inserted + " 条，合并通知记录 " + result.merged + " 条，去重跳过 " + result.duplicated + " 条"
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
private fun TransactionRow(t: TransactionUi, onClick: () -> Unit) {
    val amountColor = when (t.type) {
        TransactionType.EXPENSE -> Color(0xFFD32F2F)
        TransactionType.INCOME -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t.merchant ?: merchantFallback(t.source),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = listOfNotNull(t.categoryName ?: "未分类", formatTime(t.transactionTime)).joinToString(" · "),
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
