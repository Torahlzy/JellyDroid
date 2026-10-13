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
// 只有 debug 包会把真实地址打进 BuildConfig；release / staging 只放空串占位（运行时可在
// 「更多」菜单里填写），避免把私有配置泄露进发布包。
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

        // release / staging 的默认值：空串（运行时 ScraperSite.baseUrl 归一化为 null，站点被跳过），
        // 用户可在「更多」菜单里按站点 key 填写，经 AppPreferences 在运行时生效
        buildConfigField("String", "SCRAPER_JAVDB_URL", "\"\"")
        buildConfigField("String", "SCRAPER_JAVBUS_URL", "\"\"")
        buildConfigField("String", "SCRAPER_JAV321_URL", "\"\"")
    }

    buildTypes {
        named("debug") {
            // 仅 debug 包从 local.properties 读入真实地址，覆盖上面的空串占位
            buildConfigField("String", "SCRAPER_JAVDB_URL", scraperUrl("scraper.javdb.url"))
            buildConfigField("String", "SCRAPER_JAVBUS_URL", scraperUrl("scraper.javbus.url"))
            buildConfigField("String", "SCRAPER_JAV321_URL", scraperUrl("scraper.jav321.url"))
        }
        named("release") { isMinifyEnabled = false }
        // staging initWith release，同样不带真实地址
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
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
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
