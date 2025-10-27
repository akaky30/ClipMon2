plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // 新增：Compose Compiler 插件（版本要与 Kotlin 一致）
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
