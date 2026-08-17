package com.jizhang.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@Composable
fun StatsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshSummary() }

    Column(modifier.padding(16.dp)) {
        Text("本月概览", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Row(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支出", style = MaterialTheme.typography.labelMedium)
                    Text(
                        format(summary?.expense ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("收入", style = MaterialTheme.typography.labelMedium)
                    Text(
                        format(summary?.income ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text(
            text = "分类占比饼图 / 月度趋势折线图（Compose Canvas 自绘）将在后续版本加入，见设计文档 §6.6。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

private fun format(cents: Long): String = String.format(Locale.US, "¥%.2f", cents / 100.0)
