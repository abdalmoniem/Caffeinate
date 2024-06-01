import com.android.build.gradle.internal.api.BaseVariantOutputImpl

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
        versionCode = 6
        versionName = "1.2.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            this as BaseVariantOutputImpl
            val applicationId = variant.buildType.applicationIdSuffix?.let { "${variant.applicationId}.$it" } ?: variant.applicationId
            // val appName = applicationId.split(".").last()
            // val formattedDate = SimpleDateFormat("E_dd-MM-yyyy_hh-mm-ss_S_a").format(Date())
            // val apkName = "${appName}_${variant.buildType.name}_v${android.defaultConfig.versionName}_${formattedDate}.apk"
            val apkName = "${applicationId}_${variant.buildType.name}_v${android.defaultConfig.versionName}.apk"

            println(apkName)

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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}