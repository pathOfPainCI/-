package com.jizhang.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.repo.TransactionUi
import com.jizhang.app.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TransactionListScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val reviewCount by viewModel.needsReviewCount.collectAsStateWithLifecycle()

    Column(modifier) {
        if (reviewCount > 0) {
            Text(
                text = "有 $reviewCount 条记录待核对（金额/方向解析不确定）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "暂无记录\n支付后通知将自动记一笔",
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
