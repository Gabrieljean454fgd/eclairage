plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// versionCode grows with every commit (git rev-list --count HEAD); -PversionCode=N overrides it (CI, local tests).
val gitCommitCount: Int = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// « Build N · <paquet> » on the home screen (build 199 feedback, point 0): raw name from LUMISITE_PAQUET,
// else GITHUB_HEAD_REF / GITHUB_REF_NAME (CI), else the current git branch. The transform mirrors
// core app.lumisite.core.version.PaquetName.from (tested on the JVM): etape-3-v176g-x -> v176g, etape-3-local-x -> local-x, else dev.
val paquetRaw: String? = sequenceOf(System.getenv("LUMISITE_PAQUET"), System.getenv("GITHUB_HEAD_REF"), System.getenv("GITHUB_REF_NAME"))
    .firstOrNull { !it.isNullOrBlank() }?.trim()
    ?: runCatching { providers.exec { commandLine("git", "rev-parse", "--abbrev-ref", "HEAD") }.standardOutput.asText.get().trim() }
        .getOrNull()?.takeIf { it.isNotEmpty() && it != "HEAD" }
val paquetName: String = paquetRaw?.let {
    Regex("^etape-\\d+-(v\\d+[a-z]?)(\\b.*)?$").matchEntire(it)?.groupValues?.get(1)
        ?: Regex("^etape-\\d+-(local-.+)$").matchEntire(it)?.groupValues?.get(1) ?: it
} ?: "dev"

android {
    namespace = "app.lumisite.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.lumisite.android"
        minSdk = 24
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "0.3.0-alpha"
        buildConfigField("String", "PAQUET", "\"${paquetName.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    // PUBLIC TEST KEY (docs/SIGNATURE_APK.md): committed on purpose so that every debug APK, built locally or by CI,
    // has the same certificate and installs over the previous one. Never usable for a store/production release.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("app/keystore/lumisite-test.jks")
            storePassword = "lumisite-test"
            keyAlias = "lumisite-test"
            keyPassword = "lumisite-test"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].assets.srcDir(rootProject.file("catalog"))
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.ar:core:1.57.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}
