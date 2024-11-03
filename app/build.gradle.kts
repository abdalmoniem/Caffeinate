import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.hifnawy.caffeinate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hifnawy.caffeinate"
        minSdk = 24
        targetSdk = 35
        versionCode = 23
        versionName = "1.6.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val localPropertiesFile = rootProject.file("local.properties")
    var isDebuggingEnabled = false
    var isSigningConfigEnabled = false

    if (localPropertiesFile.exists()) {
        val keystoreProperties = Properties().apply { load(FileInputStream(localPropertiesFile)) }
        val signingProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

        isSigningConfigEnabled =
                signingProperties.all { property -> property in keystoreProperties.keys } &&
                rootProject.file(keystoreProperties["storeFile"] as String).exists()

        if (!isSigningConfigEnabled) {
            signingProperties
                .filter { property -> property !in keystoreProperties.keys }
                .forEach { missingKey -> project.logger.warn("WARNING: missing key in '${localPropertiesFile.absolutePath}': $missingKey") }
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
    } else {
        project.logger.warn("WARNING: local.properties not found, add local.properties in root directory to enable signing.")
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
                    if (variant.buildType.name == "release") "$applicationId.release" else applicationId
            val apkName =
                    "${applicationId}_${variant.buildType.name}_v${android.defaultConfig.versionName}.apk"

            outputFileName = apkName
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxAppCompat)
    implementation(libs.material)
    implementation(libs.androidxConstraintLayout)
    implementation(libs.timber)
    implementation(libs.gson)
    implementation(libs.materialNumberPicker)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidxJunit)
    androidTestImplementation(libs.androidxEspressoCore)
}