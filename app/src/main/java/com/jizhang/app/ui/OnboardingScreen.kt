package com.jizhang.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

/** 首次启动引导：隐私说明 → 通知授权 → 自启动/电池白名单（详见设计文档 §13） */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var listenerEnabled by remember { mutableStateOf(isListenerEnabled(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))
            when (step) {
                0 -> StepPrivacy()
                1 -> StepNotification(context, listenerEnabled, onChecked = { listenerEnabled = it })
                2 -> StepBackground()
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                    Text("上一步")
                }
                Spacer(Modifier.height(0.dp))
            }
            Button(
                onClick = {
                    if (step < 2) step++ else onDone()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (step < 2) "下一步" else "开始使用")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepPrivacy() {
    Text("自动记账", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        "· 自动记录微信支付、支付宝的付款/收款，免手动记账\n" +
            "· 所有账单数据仅存储在本地，不上传任何第三方\n" +
            "· 通知监听权限仅用于解析支付通知\n" +
            "· AI 分类（可选）会把商户名发送给 DeepSeek，可在设置中关闭",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StepNotification(
    context: Context,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Text("① 通知监听授权", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        if (enabled) "已授权 ✓" else "未授权。该权限为系统级，只能手动开启：",
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        enabled = !enabled,
    ) {
        Text("前往系统设置授权")
    }
    OutlinedButton(onClick = { onChecked(isListenerEnabled(context)) }) {
        Text("授权完成，检测一下")
    }
}

@Composable
private fun StepBackground() {
    Text("② 自启动与电池白名单", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(
        "国产 ROM 激进杀后台会让通知监听静默失效，建议手动设置：\n" +
            "· 小米/华为/OPPO/vivo：应用自启动 → 开启\n" +
            "· 电池 → 省电策略/后台限制 → 无限制\n" +
            "· 具体路径见设计文档 §13.2 授权路径表\n\n" +
            "即使被系统清理，CSV 账单导入仍可兜底对账，不必担心丢账。",
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
