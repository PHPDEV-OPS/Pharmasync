import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.pharmasync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.pharmasync"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Optional geocoding fallback; the on-device Geocoder is used first.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GEOAPIFY_API_KEY", "\"${localProperties.getProperty("GEOAPIFY_API_KEY", "")}\"")

        // Pharmasync backend: a Neon Function in front of Neon Postgres and Neon Object Storage.
        val apiBaseUrl = localProperties.getProperty(
            "API_BASE_URL",
            "https://br-long-thunder-b4wh1lkb-api.compute.c-6.us-east-2.aws.neon.tech/",
        )
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // CARTO basemap key for raster map tiles; falls back to plain OpenStreetMap when unset.
        buildConfigField("String", "CARTO_API_KEY", "\"${localProperties.getProperty("CARTO_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
        buildConfig = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // AndroidX + Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Room (local storage)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase
    // Firebase is used for authentication only; all data lives in Neon.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Maps, location, barcode scanning
    implementation(libs.osmdroid) // OpenStreetMap tiles: no API key or billing required
    implementation(libs.play.services.location)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.guava) // ListenableFuture used by CameraX

    // Networking + images
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.glide)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
