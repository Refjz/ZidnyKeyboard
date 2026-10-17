plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zidny.keyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zidny.keyboard"
        minSdk = 24
        targetSdk = 36
        // CI passes an increasing number so every upload to Play has a new version code.
        versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"

        // AdMob IDs come from the repository variables ADMOB_APP_ID and ADMOB_BANNER_ID.
        // Without them Google's test IDs are used, which show sample ads and earn nothing.
        val admobAppId = System.getenv("ADMOB_APP_ID")?.takeIf { it.isNotBlank() }
            ?: "ca-app-pub-3940256099942544~3347511713"
        val admobBannerId = System.getenv("ADMOB_BANNER_ID")?.takeIf { it.isNotBlank() }
            ?: "ca-app-pub-3940256099942544/9214589741"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")

        // The translation server (server/) and the Google Cloud project used for Play Integrity,
        // from the repository variables TRANSLATE_URL and CLOUD_PROJECT_NUMBER.
        val translateUrl = System.getenv("TRANSLATE_URL")?.trim() ?: ""
        val cloudProjectNumber = System.getenv("CLOUD_PROJECT_NUMBER")?.trim()?.toLongOrNull() ?: 0L
        buildConfigField("String", "TRANSLATE_URL", "\"$translateUrl\"")
        buildConfigField("long", "CLOUD_PROJECT_NUMBER", "${cloudProjectNumber}L")
    }

    buildFeatures {
        buildConfig = true
    }

    // The Play upload key is only available in CI (from repository secrets), never in the repo.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("upload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    testImplementation("junit:junit:4.13.2")
}
