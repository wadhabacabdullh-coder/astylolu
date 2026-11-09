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
        // ABIs for which a Stockfish executable is built.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "3g"
            // Optional: alternative Maven repository for Robolectric's Android runtime jars.
            (project.findProperty("robolectricRepoUrl") as String?)?.let { url ->
                it.systemProperty("robolectric.dependency.repo.url", url)
            }
            System.getenv("STOCKFISH_HOST_BIN")?.let { bin -> it.environment("STOCKFISH_HOST_BIN", bin) }
            it.systemProperty("screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
        }
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
    inputs.file(stockfishDir.file("Copying.txt"))
    inputs.file(stockfishDir.file("AUTHORS"))
    outputs.dir(generatedAssets)
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
        // GPL compliance: ship the Stockfish licence and authors list inside the app.
        val licDir = File(target.parentFile, "licenses").apply { mkdirs() }
        stockfishDir.file("Copying.txt").asFile.copyTo(File(licDir, "stockfish-COPYING.txt"), overwrite = true)
        stockfishDir.file("AUTHORS").asFile.copyTo(File(licDir, "stockfish-AUTHORS.txt"), overwrite = true)
    }
}

// The engine binaries and the network must exist before any variant task reads
// the generated jniLibs/assets source folders (merge, lint, packaging...).
tasks.named("preBuild") { dependsOn(buildStockfish, prepareNnue) }

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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
