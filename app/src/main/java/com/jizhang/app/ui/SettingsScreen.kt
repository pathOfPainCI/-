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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.SettingsStore
import com.jizhang.app.data.db.RuleEntity
import com.jizhang.app.data.repo.TransactionRepository
import com.jizhang.app.domain.model.RuleMatchType
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
            label = {
                Text(
                    if (viewModel.aiKeyConfigured) {
                        "API Key 已配置（尾号 " + viewModel.aiKeyTail + "），留空保存则保持不变"
                    } else {
                        "API Key（Keystore 加密存储）"
                    }
                )
            },
            placeholder = { Text(if (viewModel.aiKeyConfigured) "输入新 Key 可替换" else "sk- 开头") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row {
            Button(
                onClick = {
                    viewModel.saveAiSettings(baseUrl, model, apiKey)
                    saved = true
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("保存 AI 设置")
            }
            Spacer(Modifier.padding(start = 8.dp))
            var testing by remember { mutableStateOf(false) }
            var testResult by remember { mutableStateOf<String?>(null) }
            val scope = rememberCoroutineScope()
            OutlinedButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = viewModel.testAi()
                        testing = false
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (testing) "测试中..." else "测试 AI 连接")
            }
        }
        if (testResult != null) {
            Text(
                text = testResult!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (testResult!!.contains("正常")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (saved) {
            Text(
                text = "已保存（Key 留空则保留原值）",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "AI 作用：规则没覆盖的商户自动分类（如 蜜雪冰城→餐饮），结果在明细页商户名旁显示；同一商户只调用一次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
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

        Text("分类规则", style = MaterialTheme.typography.titleMedium)
        Text(
            "命中规则时自动归类（如 蜜雪冰城 → 餐饮），后添加的规则优先。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        val rules by viewModel.rules.collectAsStateWithLifecycle()
        val categories by viewModel.categories.collectAsStateWithLifecycle()
        var showRules by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showRules = true }) {
            Text("管理规则（当前 " + rules.size + " 条）")
        }
        if (showRules) {
            RulesDialog(
                rules = rules,
                categories = categories,
                onAdd = { cat, type, pattern -> viewModel.addRule(cat, type, pattern) },
                onDelete = { id -> viewModel.deleteRule(id) },
                onDismiss = { showRules = false },
            )
        }
        Spacer(Modifier.height(12.dp))

        // 分类预算入口
        var showCatBudget by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showCatBudget = true }) {
            Text("分类预算")
        }
        if (showCatBudget) {
            CategoryBudgetDialog(
                categories = categories,
                onDismiss = { showCatBudget = false },
            )
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
        Spacer(Modifier.height(8.dp))
        var restoreResult by remember { mutableStateOf<String?>(null) }
        val restoreLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                scope.launch {
                    restoreResult = try {
                        val bytes = context.contentResolver.openInputStream(it)?.use { r -> r.readBytes() }
                        if (bytes != null) {
                            val csv = com.jizhang.app.data.CsvFileReader.readAndDetect(bytes)
                            val parsed = com.jizhang.app.data.CsvFormatDetector.parseAuto(csv)
                            if (parsed.error != null) {
                                "恢复失败：" + parsed.error
                            } else {
                                val result = viewModel.restoreBackup(parsed)
                                "恢复完成：新增 " + result.inserted + " 条，去重跳过 " + result.duplicated + " 条"
                            }
                        } else {
                            "无法读取文件"
                        }
                    } catch (e: Exception) {
                        "恢复失败：" + (e.message ?: "未知错误")
                    }
                }
            }
        }
        Row {
            OutlinedButton(onClick = {
                restoreResult = null
                restoreLauncher.launch(arrayOf("text/*", "text/comma-separated-values", "application/octet-stream"))
            }) {
                Text("恢复备份")
            }
        }
        if (restoreResult != null) {
            Text(
                text = restoreResult!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (restoreResult!!.startsWith("恢复完成")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("帮助与诊断", style = MaterialTheme.typography.titleMedium)
        var crashLog by remember { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = {
            crashLog = try {
                val f = java.io.File(context.filesDir, "crash.log")
                if (f.exists()) f.readText().takeLast(4000) else "暂无崩溃记录"
            } catch (e: Exception) {
                "读取失败"
            }
        }) {
            Text("查看崩溃日志")
        }
        if (crashLog != null) {
            Text(
                text = crashLog!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))

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

@Composable
private fun CategoryBudgetDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var catExpanded by remember { mutableStateOf(false) }
    val amountCents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<MainViewModel>()
    val budgets by viewModel.categoryBudgets.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类预算（本月）") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row {
                    OutlinedButton(onClick = { catExpanded = true }) {
                        Text(selectedCategory ?: "选择分类")
                    }
                    DropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    selectedCategory = c
                                    catExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("预算金额（元）") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
                Button(
                    onClick = {
                        val cat = selectedCategory
                        if (cat != null && amountCents > 0) {
                            viewModel.addCategoryBudgetByName(cat, amountCents)
                            amount = ""
                        }
                    },
                    enabled = selectedCategory != null && amountCents > 0,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("保存")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("已有分类预算", style = MaterialTheme.typography.titleSmall)
                if (budgets.isEmpty()) {
                    Text(
                        "暂无分类预算",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                budgets.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = b.categoryName + "：" + String.format(java.util.Locale.US, "¥%.2f", b.amountCents / 100.0),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.deleteBudget(b.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun matchTypeName(t: RuleMatchType): String = when (t) {
    RuleMatchType.CONTAINS -> "包含"
    RuleMatchType.MERCHANT_EXACT -> "完全匹配"
    RuleMatchType.REGEX -> "正则"
}

@Composable
private fun RulesDialog(
    rules: List<RuleEntity>,
    categories: List<String>,
    onAdd: (String, RuleMatchType, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var matchType by remember { mutableStateOf(RuleMatchType.CONTAINS) }
    var catExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分类规则") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("新增规则", style = MaterialTheme.typography.titleSmall)
                Row {
                    OutlinedButton(onClick = { catExpanded = true }) {
                        Text(selectedCategory ?: "分类")
                    }
                    DropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                    ) {
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    selectedCategory = c
                                    catExpanded = false
                                },
                            )
                        }
                    }
                    Spacer(Modifier.padding(start = 8.dp))
                    OutlinedButton(onClick = { typeExpanded = true }) {
                        Text(matchTypeName(matchType))
                    }
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        RuleMatchType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(matchTypeName(t)) },
                                onClick = {
                                    matchType = t
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("匹配词（如：瑞幸）") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val cat = selectedCategory
                        if (cat != null && pattern.isNotBlank()) {
                            onAdd(cat, matchType, pattern.trim())
                            pattern = ""
                        }
                    },
                    enabled = selectedCategory != null && pattern.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("添加")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("已有规则（后添加的优先）", style = MaterialTheme.typography.titleSmall)
                if (rules.isEmpty()) {
                    Text(
                        "暂无规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                rules.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            text = r.categoryName + " · " + matchTypeName(r.matchType) + "：「" + r.pattern + "」",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onDelete(r.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun appVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
