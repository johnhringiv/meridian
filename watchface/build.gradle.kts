import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signing credentials live in keystore.properties (gitignored); absent = unsigned release.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    enableKotlin = false
    namespace = "com.johnhringiv.meridian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.johnhringiv.meridian"
        // minSdk 33 = Wear OS 4, the floor for Watch Face Format v1 (all this face needs).
        minSdk = 33
        targetSdk = 36
        // versionCode bumps on every feature-branch change (CI-enforced > main).
        // versionName bumps once per PR to main (CI-enforced).
        versionCode = 3
        versionName = "0.1"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // WFF resources are looked up by well-known names at runtime — never shrink them.
            isShrinkResources = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
