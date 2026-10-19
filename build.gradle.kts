plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktfmt) apply false
}

// 依赖仓库统一在 settings.gradle.kts 的 dependencyResolutionManagement 中声明，
// 本地镜像从 local.properties 读取，避免把国内镜像写进入库文件。

subprojects {
    plugins.apply("com.ncorti.ktfmt.gradle")

    configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        kotlinLangStyle()
    }
}

tasks.register<Delete>("clean") {
    description = "Deletes all build artifacts."
    delete(rootProject.layout.buildDirectory)
}
