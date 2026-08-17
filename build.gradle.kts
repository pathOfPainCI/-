// Top-level build file: plugins are declared here, applied in modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kapt) apply false
    alias(libs.plugins.hilt) apply false
}
