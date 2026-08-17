package com.jizhang.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.repo.TransactionRepository

@Composable
fun SettingsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var listenerEnabled by remember {
        mutableStateOf(isListenerEnabled(context))
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
            text = if (listenerEnabled) "已授权" else "未授权（通知是自动记账的主通道）",
            color = if (listenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Row {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) {
                Text("前往授权")
            }
            Spacer(Modifier.padding(start = 8.dp))
            OutlinedButton(onClick = { listenerEnabled = isListenerEnabled(context) }) {
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

        OutlinedButton(onClick = { viewModel.setOnboarded(false) }) {
            Text("重新打开引导页")
        }
        Spacer(Modifier.height(16.dp))

        Text(
            "数据全部存储在本地，不上传任何账单数据。通知监听为高危权限，仅用于解析记账。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
