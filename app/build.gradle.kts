import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Optional local signing. Create keystore.properties at the repository root (gitignored) to sign debug and
 * release builds with your own key instead of this machine's debug keystore. See docs/release-signing.md.
 */
val localSigning = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasLocalSigning = !localSigning.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.framerevive.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.framerevive.app"
        minSdk = 29
        targetSdk = 36
       versionCode = 67
       versionName = "0.5.25-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasLocalSigning) {
            create("local") {
                storeFile = rootProject.file(localSigning.getProperty("storeFile"))
                storePassword = localSigning.getProperty("storePassword")
                keyAlias = localSigning.getProperty("keyAlias")
                keyPassword = localSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("local")
        }
        debug {
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("local")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
