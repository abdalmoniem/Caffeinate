import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

/**
 * The package name of the application.
 *
 * This constant holds the package name used throughout the application.
 * It is primarily used for identifying the application's namespace in
 * Android and is essential for intents, broadcasting, and other system
 * interactions.
 */
private val packageName = "com.hifnawy.caffeinate"

/**
 * The file object representing the local.properties file in the root project directory.
 *
 * This file is used to store custom configuration properties for the project, such as
 * signing configurations, debugging options, and other local settings. The properties
 * are loaded during the build process to configure the build environment.
 *
 * @see Properties
 * @see FileInputStream
 */
private val localPropertiesFile = rootProject.file("local.properties")

/**
 * A flag indicating whether debugging is enabled in the release variant.
 *
 * @see ApplicationBuildType.isDebuggable
 */
private var isDebuggingEnabled = false

/**
 * A flag indicating whether signing is enabled in the release variant.
 *
 * @see ApplicationBuildType.signingConfig
 */
private var isSigningConfigEnabled = false

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    if (localPropertiesFile.exists()) {
        val keystoreProperties = Properties().apply { load(FileInputStream(localPropertiesFile)) }
        val signingProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

        isSigningConfigEnabled =
                signingProperties.all { property -> property in keystoreProperties.keys } &&
                rootProject.file(keystoreProperties["storeFile"] as String).exists()

        when {
            !isSigningConfigEnabled -> {
                signingProperties
                    .filter { property -> property !in keystoreProperties.keys }
                    .forEach { missingKey -> project.logger.warn("WARNING: missing key in '${localPropertiesFile.absolutePath}': $missingKey") }
            }

            else                    -> {
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

    defaultConfig {
        namespace = packageName
        applicationId = namespace

        minSdk = 24
        compileSdk = 35
        targetSdk = 35
        versionCode = 33
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.forEach { sourceSet ->
        sourceSet.java.srcDir("src/$sourceSet.name")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = isDebuggingEnabled

            project.logger.lifecycle("INFO: $name isDebuggable: $isDebuggingEnabled")

            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )

            signingConfigs.findByName("release")?.also { signingConfiguration ->
                when {
                    isSigningConfigEnabled -> {
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
                when {
                    isSigningConfigEnabled -> {
                        signingConfig = signingConfiguration
                        project.logger.lifecycle("INFO: $name buildType is signed with release signing config.")
                        project.logger.lifecycle("INFO: $name signing config is located in ${signingConfiguration.storeFile?.absolutePath}")
                    }

                    else                   -> project.logger.lifecycle(
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
            val applicationId = if (variant.buildType.name == "release") "$applicationId.release" else applicationId
            val baseName = "${applicationId}_${variant.buildType.name}_v${android.defaultConfig.versionName}"

            setProperty("archivesBaseName", baseName)

            outputFileName = "$baseName.apk"
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

    dataBinding {
        enable = true
    }
}

dependencies {
    implementation(libs.androidxCoreKtx)
    implementation(libs.androidxAppCompat)
    implementation(libs.androidxConstraintLayout)
    implementation(libs.activity)
    implementation(libs.material)
    implementation(libs.timber)
    implementation(libs.assent)
    implementation(libs.gson)
    implementation(libs.materialNumberPicker)
    implementation(libs.circulardurationview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidxJunit)
    androidTestImplementation(libs.androidxEspressoCore)
}