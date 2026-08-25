package com.jizhang.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.util.NotificationAccess

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var listenerEnabled by remember {
        mutableStateOf(NotificationAccess.isEnabled(context))
    }
    var rawListenerRecord by remember {
        mutableStateOf(NotificationAccess.rawValue(context))
    }

    // 从系统授权页返回时自动重新检测
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = NotificationAccess.isEnabled(context)
                rawListenerRecord = NotificationAccess.rawValue(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var baseUrl by remember { mutableStateOf(viewModel.aiBaseUrl) }
    var model by remember { mutableStateOf(viewModel.aiModel) }
    var apiKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("通知监听", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (listenerEnabled) "已授权 ✓" else "未授权（通知是自动记账的主通道）",
            color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (!listenerEnabled && rawListenerRecord.isNullOrBlank()) {
            Text(
                text = "系统记录为空：授权后请完全关闭本 App 再重开，或重启手机。小米设备若开关已开仍显示未授权，请把下方「系统记录」内容发给我。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (rawListenerRecord != null) {
            Text(
                text = "系统记录：" + rawListenerRecord,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Row {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("前往授权")
            }
            Spacer(Modifier.padding(start = 8.dp))
            OutlinedButton(onClick = {
                listenerEnabled = NotificationAccess.isEnabled(context)
                rawListenerRecord = NotificationAccess.rawValue(context)
            }) {
                Text("重新检测")
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("AI 兜底分类（可选，DeepSeek）", style = MaterialTheme.typography.titleMedium)
        Text(
            "未命中规则时调用 AI 分类，商户名会发送给 DeepSeek。不填 Key 则为纯本地规则模式。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key（Keystore 加密存储）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = {
                viewModel.saveAiSettings(baseUrl, model, apiKey)
                saved = true
            },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("保存 AI 设置")
        }
        if (saved) {
            Text("已保存", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))

        Text("月度预算", style = MaterialTheme.typography.titleMedium)
        var budgetInput by remember { mutableStateOf("") }
        LaunchedEffect(viewModel.monthBudget.value) {
            val b = viewModel.monthBudget.value
            budgetInput = if (b > 0) String.format(java.util.Locale.US, "%.2f", b / 100.0) else ""
        }
        OutlinedTextField(
            value = budgetInput,
            onValueChange = { budgetInput = it },
            label = { Text("本月总预算（元），0 或留空 = 不设") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        )
        Row {
            Button(
                onClick = { viewModel.setMonthBudget(budgetInput) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("保存预算")
            }
            Spacer(Modifier.padding(start = 8.dp))
            if (viewModel.monthBudget.value > 0) {
                OutlinedButton(
                    onClick = {
                        budgetInput = ""
                        viewModel.clearMonthBudget()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("清除")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("数据备份", style = MaterialTheme.typography.titleMedium)
        var exported by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        context.contentResolver.openOutputStream(it)?.use { os ->
                            os.write(viewModel.exportCsv().toByteArray())
                        }
                        exported = true
                    } catch (e: Exception) {
                        exported = false
                    }
                }
            }
        }
        Row {
            OutlinedButton(onClick = {
                exported = false
                val name = "记账备份-" + java.time.LocalDate.now().toString().replace("-", "") + ".csv"
                exportLauncher.launch(name)
            }) {
                Text("备份导出 CSV")
            }
        }
        if (exported) {
            Text(
                text = "已导出，请妥善保存该文件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = { viewModel.setOnboarded(false) }) {
            Text("重新打开引导页")
        }
        Spacer(Modifier.height(16.dp))

        Text(
            "数据全部存储在本地，不上传任何账单数据。通知监听为高危权限，仅用于解析记账。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "版本 " + appVersion(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun appVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
