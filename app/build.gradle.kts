import java.time.Duration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.chatbox.kxmpzq"
        minSdk = 26
        targetSdk = 36
        // CI passes an always-increasing code (workflow run number) so every
        // release is a real upgrade for Android's package installer.
        versionCode = (project.findProperty("chatbox.versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("chatbox.versionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            val ks = rootProject.file("debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            val props = Properties()
            if (propsFile.exists()) {
                propsFile.inputStream().use { props.load(it) }
            }
            val storePath = props.getProperty("storeFile") ?: "release.keystore"
            val ks = rootProject.file(storePath)
            if (ks.exists()) {
                storeFile = ks
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            val releaseKs = signingConfigs.findByName("release")
            if (releaseKs?.storeFile?.exists() == true) {
                signingConfig = releaseKs
            }
            isMinifyEnabled = false
            // Real phones only (64- and 32-bit ARM). x86/x86_64 copies of the WebRTC
            // native library are for emulators and roughly doubled the download.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Full edition (WhatsApp/Messenger auto-reply): ./gradlew assembleRelease -Pchatbox.autoReply=true
    if (project.findProperty("chatbox.autoReply")?.toString() == "true") {
        sourceSets.getByName("release").manifest.srcFile("src/autoreply/AndroidManifest.xml")
    }

    // Store native libraries compressed inside the APK: much smaller download for
    // in-app updates (Android extracts them once at install time).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                listOf("robolectric.offline", "robolectric.dependency.dir").forEach { prop ->
                    System.getProperty(prop)?.takeIf { it.isNotBlank() }?.let { value ->
                        it.systemProperty(prop, value)
                    }
                }
                // Print each test as it starts so a hanging test can be identified in CI.
                it.testLogging { events("started", "failed") }
                // A single hung test must not block CI forever.
                it.timeout.set(Duration.ofMinutes(7))
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.recaptcha)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.webrtc.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
