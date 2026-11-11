import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val modelUrl = (project.findProperty("modelUrl") as String?) ?: ""
val tokensUrl = (project.findProperty("tokensUrl") as String?) ?: ""
val projectUrl = (project.findProperty("projectUrl") as String?) ?: "https://github.com/SrihariPHS/parakey"
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "com.parakey.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parakey.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.1"

        buildConfigField("String", "MODEL_URL", "\"$modelUrl\"")
        buildConfigField("String", "TOKENS_URL", "\"$tokensUrl\"")
        buildConfigField("String", "PROJECT_URL", "\"$projectUrl\"")
        buildConfigField("String", "MODEL_SHA256", "\"9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a\"")
        buildConfigField("String", "TOKENS_SHA256", "\"450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10\"")
        buildConfigField("long", "MODEL_BYTES", "131652171L")
        buildConfigField("long", "TOKENS_BYTES", "9953L")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.isFile) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            if (keystorePropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    testImplementation("junit:junit:4.13.2")
}
