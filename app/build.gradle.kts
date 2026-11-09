import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Stockfish engine + NNUE network
// ---------------------------------------------------------------------------
val stockfishDir = rootProject.layout.projectDirectory.dir("third_party/stockfish")
val nnueName = "nn-1a298aa575a0.nnue"
val nnueSha256 = "1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2"
val generatedJniLibs = layout.buildDirectory.dir("generated/stockfish/jniLibs")
val generatedAssets = layout.buildDirectory.dir("generated/stockfish/assets")

android {
    namespace = "com.grandmaster.ai"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.grandmaster.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "STOCKFISH_VERSION", "\"Stockfish 19\"")
        buildConfigField("String", "NNUE_FILE", "\"$nnueName\"")
        buildConfigField("String", "NNUE_SHA256", "\"$nnueSha256\"")
    }

    val keystoreFile = rootProject.file("keystore.properties")
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                val props = Properties().apply { FileInputStream(keystoreFile).use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use the release key when keystore.properties exists, otherwise fall back to the
            // debug key so that the release APK is still installable.
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(generatedJniLibs)
            assets.srcDir(generatedAssets)
        }
    }

    packaging {
        // The engine binaries must be extracted to nativeLibraryDir so they can be executed.
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/** Cross-compiles Stockfish for every supported ABI using the configured NDK. */
val buildStockfish by tasks.registering(Exec::class) {
    group = "stockfish"
    description = "Builds the Stockfish engine executables with the Android NDK"
    val ndkDir = android.ndkDirectory
    inputs.dir(stockfishDir.dir("src"))
    inputs.file(stockfishDir.file("build-android.sh"))
    outputs.dir(generatedJniLibs)
    doFirst { generatedJniLibs.get().asFile.deleteRecursively() }
    commandLine(
        "bash", stockfishDir.file("build-android.sh").asFile.absolutePath,
        ndkDir.absolutePath, generatedJniLibs.get().asFile.absolutePath,
    )
}

/** Provides the NNUE network as an app asset, downloading it if needed, and verifies its SHA-256. */
val prepareNnue by tasks.registering {
    group = "stockfish"
    description = "Fetches and verifies the Stockfish NNUE network"
    val local = stockfishDir.file("net/$nnueName").asFile
    val out = generatedAssets.map { it.file(nnueName) }
    outputs.file(out)
    doLast {
        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        if (!local.exists() || sha256(local) != nnueSha256) {
            local.parentFile.mkdirs()
            val urls = listOf(
                "https://tests.stockfishchess.org/api/nn/$nnueName",
                "https://github.com/official-stockfish/networks/raw/master/$nnueName",
            )
            var ok = false
            for (u in urls) {
                try {
                    logger.lifecycle("Downloading $nnueName from $u")
                    URI(u).toURL().openStream().use { input -> local.outputStream().use { input.copyTo(it) } }
                    if (sha256(local) == nnueSha256) { ok = true; break }
                    logger.warn("SHA-256 mismatch for download from $u")
                } catch (e: Exception) {
                    logger.warn("Download from $u failed: ${e.message}")
                }
            }
            if (!ok) throw GradleException(
                "Could not obtain $nnueName. Place the file (sha256 $nnueSha256) in ${local.parentFile}"
            )
        }
        val target = out.get().asFile
        target.parentFile.mkdirs()
        local.copyTo(target, overwrite = true)
    }
}

tasks.configureEach {
    if (name.matches(Regex("merge\\w*JniLibFolders")) || name.matches(Regex("merge\\w*NativeLibs"))) {
        dependsOn(buildStockfish)
    }
    if (name.matches(Regex("merge\\w*Assets")) || name.matches(Regex("generate\\w*LintModel")) ||
        name.matches(Regex("lintAnalyze\\w*")) || name.matches(Regex("\\w*LintReportModel\\w*"))
    ) {
        dependsOn(prepareNnue)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
