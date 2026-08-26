package com.jizhang.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.repo.MonthPoint
import com.jizhang.app.data.repo.StatsData
import java.util.Locale

private val PIE_PALETTE = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFF4511E), Color(0xFF8E24AA),
    Color(0xFF00ACC1), Color(0xFFFFB300), Color(0xFFE53935), Color(0xFF6D4C41),
    Color(0xFF3949AB), Color(0xFF00897B), Color(0xFFF06292), Color(0xFF757575),
)

@Composable
fun StatsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshStats() }

    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var exported by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                    }
                    exported = true
                } catch (e: Exception) {
                    exported = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 月份切换 + 导出
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { viewModel.shiftStatsMonth(-1) }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "上月",
                )
            }
            Text(
                text = viewModel.statsMonthTitle(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            androidx.compose.material3.IconButton(onClick = { viewModel.shiftStatsMonth(1) }) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.KeyboardArrowRight,
                    contentDescription = "下月",
                )
            }
            androidx.compose.material3.TextButton(onClick = {
                exported = false
                exportLauncher.launch("统计-" + viewModel.statsMonthTitle().replace("年", "-").replace("月", "") + ".png")
            }) {
                Text("导出")
            }
        }
        if (exported) {
            Text(
                text = "已导出图片",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    // 把记录的内容绘制到屏幕（漏掉会整块空白）
                    drawLayer(graphicsLayer)
                },
        ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支出", style = MaterialTheme.typography.labelMedium)
                    Text(
                        format(stats?.expense ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFD32F2F),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("收入", style = MaterialTheme.typography.labelMedium)
                    Text(
                        format(stats?.income ?: 0L),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2E7D32),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("结余", style = MaterialTheme.typography.labelMedium)
                    Text(
                        format((stats?.income ?: 0L) - (stats?.expense ?: 0L)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        Text("本月支出分类占比", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val slices = stats?.categorySlices ?: emptyList()
        if (slices.isEmpty()) {
            Text(
                "本月暂无支出记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            PieChart(slices)
            Spacer(Modifier.height(12.dp))
            Legend(slices, stats?.categoryBudgets ?: emptyMap())
        }

        Spacer(Modifier.height(24.dp))
        Text("近 6 个月收支趋势", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val trend = stats?.trend ?: emptyList()
        if (trend.isEmpty()) {
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            TrendChart(trend)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Color(0xFFD32F2F)),
                )
                Text(" 支出   ", style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Color(0xFF2E7D32)),
                )
                Text(" 收入", style = MaterialTheme.typography.bodySmall)
            }
        }
        }
    }
}

@Composable
private fun PieChart(slices: List<Pair<String, Long>>) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Canvas(modifier = Modifier.size(220.dp)) {
        var startAngle = -90f
        slices.forEachIndexed { i, _ ->
            val sweep = slices[i].second.toFloat() / total * 360f
            drawArc(
                color = PIE_PALETTE[i % PIE_PALETTE.size],
                startAngle = startAngle,
                sweepAngle = (sweep - 0.8f).coerceAtLeast(0.5f),
                useCenter = true,
                size = Size(size.width, size.height),
                topLeft = Offset(0f, 0f),
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun Legend(slices: List<Pair<String, Long>>, budgets: Map<String, Long>) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Column {
        slices.forEachIndexed { i, (name, amount) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(PIE_PALETTE[i % PIE_PALETTE.size]),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                val budget = budgets[name]
                if (budget != null && budget > 0) {
                    if (amount > budget) {
                        Text(
                            text = "超支 " + format(amount - budget),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                        )
                    } else {
                        Text(
                            text = format(amount) + " / " + format(budget),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                } else {
                    Text(
                        text = format(amount) + "  " +
                            String.format(Locale.US, "%.1f%%", amount.toFloat() / total * 100f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendChart(points: List<MonthPoint>) {
    val maxVal = (points.maxOfOrNull { maxOf(it.expense, it.income) } ?: 0L).coerceAtLeast(1)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val w = size.width
        val h = size.height
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 12f * density
        }

        // 网格（5 条水平线）
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(
                color = Color(0xFFE0E0E0),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        val stepX = if (points.size > 1) w / (points.size - 1) else w
        fun yFor(v: Long): Float = h - h * 0.85f * v.toFloat() / maxVal - h * 0.08f

        // 支出折线（compose Path + drawPath）
        val expensePath = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = yFor(p.expense)
            if (i == 0) expensePath.moveTo(x, y) else expensePath.lineTo(x, y)
        }
        drawPath(
            path = expensePath,
            color = Color(0xFFD32F2F),
            style = Stroke(width = 3f * density),
        )

        // 收入折线
        val incomePath = Path()
        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = yFor(p.income)
            if (i == 0) incomePath.moveTo(x, y) else incomePath.lineTo(x, y)
        }
        drawPath(
            path = incomePath,
            color = Color(0xFF2E7D32),
            style = Stroke(width = 3f * density),
        )

        // 月份标签
        points.forEachIndexed { i, p ->
            val x = i * stepX - 12f * density
            drawContext.canvas.nativeCanvas.drawText(p.label, x, h - 6f, textPaint)
        }
    }
}

private fun format(cents: Long): String = String.format(Locale.US, "¥%.2f", cents / 100.0)
