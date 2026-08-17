package com.jizhang.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置：
 * - AI 配置（base_url / model / api_key）→ EncryptedSharedPreferences（Keystore 加密）
 * - 普通开关（onboarded 等）→ 普通 SharedPreferences
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val appPrefs: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-v4-flash"
    }

    var aiBaseUrl: String
        get() = securePrefs.getString("ai_base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = securePrefs.edit().putString("ai_base_url", value).apply()

    var aiModel: String
        get() = securePrefs.getString("ai_model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = securePrefs.edit().putString("ai_model", value).apply()

    var aiApiKey: String
        get() = securePrefs.getString("ai_api_key", "") ?: ""
        set(value) = securePrefs.edit().putString("ai_api_key", value).apply()

    /** AI 启用 = 已配置 key（纯本地规则模式时留空） */
    val aiEnabled: Boolean get() = aiApiKey.isNotBlank()

    // ---- 引导页状态 ----
    private val _onboarded = MutableStateFlow(appPrefs.getBoolean("onboarded", false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setOnboarded(value: Boolean) {
        appPrefs.edit().putBoolean("onboarded", value).apply()
        _onboarded.value = value
    }
}
