import java.util.Properties

// Top of build.gradle (Module: app)
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.calibreboxnew"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            java.srcDirs("build/generated/sqldelight/code/CalibreMetadata/main")
        }
    }

    defaultConfig {
        applicationId = "com.example.calibreboxnew"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // This makes the key available in your Manifest
        manifestPlaceholders["dropboxAppKey"] = "db-${localProperties.getProperty("dropbox.key")}"

        // This makes the key available in your Kotlin code as BuildConfig.DROPBOX_APP_KEY
        buildConfigField("String", "DROPBOX_APP_KEY", "\"${localProperties.getProperty("dropbox.key")}\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

sqldelight {
  databases {
    create("CalibreMetadata") {
      packageName.set("com.example.calibreboxnew.db")
    }
  }
}

dependencies {

    implementation(libs.dropbox.core.sdk)
    implementation(libs.dropbox.android.sdk)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended.android)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}