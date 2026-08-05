import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.io.FileInputStream

plugins {
    kotlin("android")
    kotlin("kapt")
    id("com.android.application")
}

android {
    defaultConfig {
        // SUBLINKS_API_URL is defined in buildTypes
    }

    buildTypes {
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val updateEnabled = localProperties.getProperty("UPDATE_ENABLED")?.toBoolean() ?: false
        val updateApiUrl = localProperties.getProperty("UPDATE_API_URL") ?: ""
        val updateAppName = localProperties.getProperty("UPDATE_APP_NAME") ?: ""
        val heartbeatEnabled = localProperties.getProperty("HEARTBEAT_ENABLED")?.toBoolean() ?: false
        val heartbeatInterval = localProperties.getProperty("HEARTBEAT_INTERVAL")?.toIntOrNull() ?: 60

        getByName("release") {
            // Read specific key first, fallback to empty if not set
            val apiUrl = localProperties.getProperty("SUBLINKS_APIURL_RELEASE") ?: ""
            buildConfigField("String", "SUBLINKS_API_URL", "\"$apiUrl\"")
            buildConfigField("boolean", "UPDATE_ENABLED", updateEnabled.toString())
            buildConfigField("String", "UPDATE_API_URL", "\"$updateApiUrl\"")
            buildConfigField("String", "UPDATE_APP_NAME", "\"$updateAppName\"")
            // Heartbeat config
            val heartbeatApiUrl = localProperties.getProperty("HEARTBEAT_APIURL_RELEASE") ?: ""
            val heartbeatApiKey = localProperties.getProperty("HEARTBEAT_APIKEY_RELEASE") ?: ""
            buildConfigField("boolean", "HEARTBEAT_ENABLED", heartbeatEnabled.toString())
            buildConfigField("int", "HEARTBEAT_INTERVAL", heartbeatInterval.toString())
            buildConfigField("String", "HEARTBEAT_API_URL", "\"$heartbeatApiUrl\"")
            buildConfigField("String", "HEARTBEAT_API_KEY", "\"$heartbeatApiKey\"")
        }
        getByName("debug") {
            // Read specific key first, fallback to default
            val apiUrl = localProperties.getProperty("SUBLINKS_APIURL_DEBUG")
                ?: "http://192.168.1.100:3000/"
            buildConfigField("String", "SUBLINKS_API_URL", "\"$apiUrl\"")
            buildConfigField("boolean", "UPDATE_ENABLED", updateEnabled.toString())
            buildConfigField("String", "UPDATE_API_URL", "\"$updateApiUrl\"")
            buildConfigField("String", "UPDATE_APP_NAME", "\"$updateAppName\"")
            // Heartbeat config
            val heartbeatApiUrl = localProperties.getProperty("HEARTBEAT_APIURL_DEBUG") ?: "http://127.0.0.1:8787"
            val heartbeatApiKey = localProperties.getProperty("HEARTBEAT_APIKEY_DEBUG") ?: ""
            buildConfigField("boolean", "HEARTBEAT_ENABLED", heartbeatEnabled.toString())
            buildConfigField("int", "HEARTBEAT_INTERVAL", heartbeatInterval.toString())
            buildConfigField("String", "HEARTBEAT_API_URL", "\"$heartbeatApiUrl\"")
            buildConfigField("String", "HEARTBEAT_API_KEY", "\"$heartbeatApiKey\"")
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiFilter = output.getFilter(com.android.build.OutputFile.ABI)
            val abi = abiFilter ?: "universal"
            output.outputFileName = "SCA-${defaultConfig.versionName}-$abi-${buildType.name}.apk"
        }
    }
}

dependencies {
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coil)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val geoFilesDownloadDir = "src/main/assets"

task("downloadGeoFiles") {

    val geoFilesUrls = mapOf(
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb" to "geoip.metadb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat" to "geosite.dat",
        // "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/country.mmdb" to "country.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb" to "ASN.mmdb",
        "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z" to "BundleMRS.7z",
    )

    doLast {
        geoFilesUrls.forEach { (downloadUrl, outputFileName) ->
            val url = URL(downloadUrl)
            val outputPath = file("$geoFilesDownloadDir/$outputFileName")
            outputPath.parentFile.mkdirs()
            url.openStream().use { input ->
                Files.copy(input, outputPath.toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("$outputFileName downloaded to $outputPath")
            }
        }
    }
}

afterEvaluate {
    val downloadGeoFilesTask = tasks["downloadGeoFiles"]

    tasks.forEach {
        if (it.name.startsWith("assemble")) {
            it.dependsOn(downloadGeoFilesTask)
        }
    }
}

tasks.getByName("clean", type = Delete::class) {
    delete(file(geoFilesDownloadDir))
}