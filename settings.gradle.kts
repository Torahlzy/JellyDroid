import java.util.Properties
import org.gradle.api.initialization.resolve.RepositoriesMode

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "findroid"

pluginManagement {
    // 本地 Maven 镜像（可选）：local.properties 里 maven.mirror=<仓库地址>。
    // 默认只使用官方仓库；local.properties 不入库，F-Droid / CI 等干净环境因此不受影响。
    val localMavenMirror =
        java.util.Properties()
            .apply {
                settingsDir
                    .resolve("local.properties")
                    .takeIf { it.isFile }
                    ?.inputStream()
                    ?.use { load(it) }
            }
            .getProperty("maven.mirror")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    repositories {
        // 镜像排在前面用于本地加速，未命中会自动回落到下面的官方仓库。
        localMavenMirror?.let { mirrorUrl -> maven { url = uri(mirrorUrl) } }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    // 依赖仓库统一在这里配置，模块内不再各自声明；本地镜像只从 local.properties 读取。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    val localMavenMirror =
        Properties()
            .apply {
                settingsDir
                    .resolve("local.properties")
                    .takeIf { it.isFile }
                    ?.inputStream()
                    ?.use { load(it) }
            }
            .getProperty("maven.mirror")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    repositories {
        // 只用 google() 与 mavenCentral()：F-Droid 只接受来自受信任 Maven 仓库
        // （Maven Central / Google Maven / Sonatype / JitPack 等）的依赖，
        // JetBrains Space 的 dev 仓库不在白名单内，且当前所有依赖的正式版都能在 Central 解析。
        localMavenMirror?.let { mirrorUrl -> maven { url = uri(mirrorUrl) } }
        google()
        mavenCentral()
    }
}

include(":app:phone")
include(":core")
include(":data")
include(":logging")
include(":player:core")
include(":player:local")
include(":setup")
include(":modes:film")
include(":settings")
