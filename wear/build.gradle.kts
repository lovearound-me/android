import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

// The same upload key as the phone app's (app/build.gradle.kts).
val upload = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("UPLOAD_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("UPLOAD_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("UPLOAD_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("UPLOAD_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}

android {
    // The phone app's package: one listing on Google Play, two bundles.
    namespace = "me.lovearound.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.lovearound.android"
        // Wear OS 3 and newer.
        minSdk = 30
        targetSdk = 36
        // Above every phone build: both bundles share the listing, so their
        // numbers must never meet.
        versionCode = 100_000_000 + (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0"
        buildConfigField("String", "CLIENT", "\"wearos\"")
    }

    sourceSets["main"].java.srcDir("../shared/src/main/java")
    sourceSets["main"].assets.srcDir("../shared/src/main/assets")

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (upload.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(upload.getProperty("storeFile"))
                storePassword = upload.getProperty("storePassword")
                keyAlias = upload.getProperty("keyAlias")
                keyPassword = upload.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.wear.compose:compose-material3:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    // Firebase pulls an old Fragment in; the permission question needs 1.3+.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("com.google.firebase:firebase-messaging:25.0.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.nayuki:qrcodegen:1.8.0")
    // The tile and the ♥ on the watch face.
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.wear.protolayout:protolayout-expression:1.4.2")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.3.0")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    // Draws the tile in TileScreensTest; debug builds only.
    debugImplementation("androidx.wear.tiles:tiles-renderer:1.6.2")
}
