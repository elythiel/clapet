import java.util.Properties

// AGP 9 ships built-in Kotlin support: no kotlin-android plugin needed.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing: keystore path and alias live in keystore.properties, kept out of git.
// Without it, debug builds still work; only release builds fail.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

// The keystore password never sits in a file: the CLAPET_KEYSTORE_PASSWORD env var
// (for CI), else the macOS Keychain item stored under the "clapet-keystore" service.
val keystorePassword: String? = System.getenv("CLAPET_KEYSTORE_PASSWORD")
    ?: providers.exec {
        commandLine("security", "find-generic-password", "-s", "clapet-keystore", "-w")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }

android {
    namespace = "xyz.martelle.clapet"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.martelle.clapet"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = keystoreProperties.getProperty("storeFile")?.let(rootProject::file)
            // keytool's PKCS12 keystores use one password for the store and the key.
            storePassword = keystorePassword
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        // IShellService, the Shizuku user service interface.
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.colorpicker.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests: use the real library there.
    testImplementation(libs.org.json)
}
