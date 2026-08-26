package com.jizhang.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.jizhang.app.R
import com.jizhang.app.data.db.AppDatabase
import com.jizhang.app.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** 桌面小组件：本月支出 + 预算 + 进度条 */
class BudgetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        Thread {
            try {
                val db = AppDatabase.getInstance(context)
                val now = LocalDate.now()
                val month = now.toString().substring(0, 7)
                val start = now.withDayOfMonth(1).atStartOfDay()
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = now.withDayOfMonth(1).plusMonths(1).atStartOfDay()
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                kotlinx.coroutines.runBlocking {
                    val expense = db.transactionDao().sumByType(TransactionType.EXPENSE.name, start, end)
                    val budget = db.budgetDao().getTotalBudget(month) ?: 0L

                    val views = RemoteViews(context.packageName, R.layout.widget_budget)
                    views.setTextViewText(R.id.widget_title, "本月预算")
                    views.setTextViewText(R.id.widget_expense, "本月支出 " + formatYuan(expense))
                    views.setTextViewText(
                        R.id.widget_budget,
                        if (budget > 0) "预算 " + formatYuan(budget) else "未设预算（设置页可添加）",
                    )
                    val ratio = if (budget > 0) {
                        ((expense.toFloat() / budget) * 100).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    views.setProgressBar(R.id.widget_progress, 100, ratio, false)
                    appWidgetManager.updateAppWidget(appWidgetIds, views)
                }
            } catch (e: Exception) {
                // 小组件更新失败不打扰
            }
        }.start()
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BudgetWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                BudgetWidgetProvider().onUpdate(context, manager, ids)
            }
        }

        private fun formatYuan(cents: Long): String =
            String.format(Locale.US, "¥%.2f", cents / 100.0)
    }
}
