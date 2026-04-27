plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.seanime.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.seanime.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.seanime.app.test"
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

    // The repo has jniLibs nested at app/src/main/app/src/main/jniLibs/
    // which is non-standard. We configure both paths so either location works.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs(
                "src/main/jniLibs",                    // standard path
                "src/main/app/src/main/jniLibs"        // existing repo path (nested)
            )
        }
    }

    packaging {
        // Prevent conflicts if multiple .so dirs supply the same file
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    // Core Android — no external libraries needed
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
