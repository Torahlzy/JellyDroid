import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// nfo 抓取的站点地址属于本地配置，写在 local.properties（已被 .gitignore 忽略，不入库）。
// 按下面三个 key 填写，没填的站点会在运行时被跳过；这里只列 key，不写地址本身。
//   scraper.javdb.url=
//   scraper.javbus.url=
//   scraper.jav321.url=
val scraperUrls =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

/** 取本地配置里的站点地址并转成 Kotlin 字符串字面量，缺失时给空串。 */
fun scraperUrl(key: String): String = "\"${scraperUrls.getProperty(key).orEmpty()}\""

android {
    namespace = "dev.jdtech.jellyfin.core"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK

        buildConfigField("String", "SCRAPER_JAVDB_URL", scraperUrl("scraper.javdb.url"))
        buildConfigField("String", "SCRAPER_JAVBUS_URL", scraperUrl("scraper.javbus.url"))
        buildConfigField("String", "SCRAPER_JAV321_URL", scraperUrl("scraper.jav321.url"))
    }

    buildTypes {
        named("release") { isMinifyEnabled = false }
        register("staging") { initWith(getByName("release")) }
    }

    flavorDimensions += "variant"
    productFlavors { register("libre") }

    compileOptions {
        sourceCompatibility = Versions.JAVA
        targetCompatibility = Versions.JAVA
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(projects.data)
    implementation(projects.logging)
    implementation(projects.player.core)
    implementation(projects.settings)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.work)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
    implementation(libs.jsoup)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)
}
