plugins {
    id("com.android.application")
}

android {
    namespace = "app.medialan"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.filmes.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = file("../../VERSION").readText().trim()
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the emulator's address for the host PC.
            buildConfigField("String", "DEFAULT_SERVER", "\"http://10.0.2.2:8097\"")
        }
        release {
            val server = providers.gradleProperty("defaultServer").orElse("http://192.168.0.10:8097").get()
            buildConfigField("String", "DEFAULT_SERVER", "\"$server\"")
            // No store: the debug key is enough to install over ADB.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        // TV apps read the Back key from the remote: there is no gesture navigation here.
        disable += "GestureBackNavigation"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.9.1")
    implementation("androidx.media3:media3-ui:1.9.1")
    implementation("com.github.bumptech.glide:glide:5.0.9")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
