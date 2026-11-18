plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.telemetry"
    compileSdk = 34

    signingConfigs {
        create("release") {
            storeFile = file("telemetry-release-key.jks")
            storePassword = "telemetry123"
            keyAlias = "telemetry_alias"
            keyPassword = "telemetry123"
        }
    }

    defaultConfig {
        applicationId = "com.example.telemetry"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        buildConfigField("String", "SUPABASE_URL", "\"https://eitriabinqdjrjwwfuvg.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_8Ve4My5LWIfZ_1Jf6u2Qhg_mlh_MgP9\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    implementation("com.google.android.gms:play-services-location:21.1.0")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
