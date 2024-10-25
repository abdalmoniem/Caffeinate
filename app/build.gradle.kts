import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.hifnawy.caffeinate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hifnawy.caffeinate"
        minSdk = 24
        targetSdk = 34
        versionCode = 19
        versionName = "1.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val localPropertiesFile = rootProject.file("local.properties")
    var isDebuggingEnabled = false
    var isSigningConfigEnabled = false

    if (localPropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(FileInputStream(localPropertiesFile))

        val signingProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

        isSigningConfigEnabled =
            signingProperties.all { property -> property in keystoreProperties.keys } &&
                    rootProject.file(keystoreProperties["storeFile"] as String).exists()

        if (!isSigningConfigEnabled) {
            project.logger.warn("WARNING: signing config not found, add signing config in local.properties")
        } else {
            signingConfigs {
                project.logger.lifecycle("keystore: ${rootProject.file(keystoreProperties["storeFile"] as String).absolutePath}")

                create("release") {
                    keyAlias = keystoreProperties["keyAlias"] as String
                    keyPassword = keystoreProperties["keyPassword"] as String
                    storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                    storePassword = keystoreProperties["storePassword"] as String
                }
            }
        }

        isDebuggingEnabled =
            keystoreProperties.getProperty("isDebuggingEnabled")?.toBoolean() ?: false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = isDebuggingEnabled

            project.logger.lifecycle("release isDebuggable: $isDebuggingEnabled")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (isSigningConfigEnabled) {
                signingConfig = signingConfigs.findByName("release")
                    ?: error("release signing config not found, add signing config in local.properties")

            } else {
                project.logger.warn("WARNING: release buildType is not signed, add signing config in local.properties to enable signing.")
            }
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            this as BaseVariantOutputImpl
            val applicationId =
                variant.buildType.applicationIdSuffix?.let { "${variant.applicationId}.$it" }
                    ?: variant.applicationId
            val apkName =
                "${applicationId}_${variant.buildType.name}_v${android.defaultConfig.versionName}.apk"

            outputFileName = apkName
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.timber)
    implementation(libs.gson)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}