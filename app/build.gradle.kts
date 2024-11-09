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
        versionCode = 27
        versionName = "1.7.1"

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

        when (isSigningConfigEnabled) {
            false -> {
                signingProperties
                    .filter { property -> property !in keystoreProperties.keys }
                    .forEach { missingKey -> project.logger.warn("WARNING: missing key in '${localPropertiesFile.absolutePath}': $missingKey") }
            }

            else  -> {
                signingConfigs {
                    project.logger.lifecycle("INFO: keystore: ${rootProject.file(keystoreProperties["storeFile"] as String).absolutePath}")

                    create("release") {
                        keyAlias = keystoreProperties["keyAlias"] as String
                        keyPassword = keystoreProperties["keyPassword"] as String
                        storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                        storePassword = keystoreProperties["storePassword"] as String
                    }
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

            project.logger.lifecycle("INFO: $name isDebuggable: $isDebuggingEnabled")

            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )

            signingConfigs.findByName("release")?.also { signingConfiguration ->
                when (isSigningConfigEnabled) {
                    true -> {
                        signingConfig = signingConfiguration
                        project.logger.lifecycle("INFO: $name buildType is signed with release signing config.")
                        project.logger.lifecycle("INFO: $name signing config is located in ${signingConfiguration.storeFile?.absolutePath}")
                    }

                    else                   -> project.logger.warn(
                            "WARNING: $name buildType is not signed, " +
                            "add signing config in local.properties to enable signing."
                    )
                }
            } ?: project.logger.error("ERROR: $name signing config not found, add signing config in local.properties")
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )

            signingConfigs.findByName("release")?.also { signingConfiguration ->
                when (isSigningConfigEnabled) {
                    true -> {
                        signingConfig = signingConfiguration
                        project.logger.lifecycle("INFO: $name buildType is signed with release signing config.")
                        project.logger.lifecycle("INFO: $name signing config is located in ${signingConfiguration.storeFile?.absolutePath}")
                    }

                    else -> project.logger.lifecycle(
                            "INFO: $name buildType is signed with default signing config, " +
                            "add signing config in local.properties to enable signing."
                    )
                }
            } ?: project.logger.lifecycle(
                    "INFO: $name buildType is signed with default signing config, " +
                    "add signing config in local.properties to enable signing."
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