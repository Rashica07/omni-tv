plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.rasha.omni"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.rasha.omni"
        minSdk = 26
        targetSdk = 30
        versionCode = 109
        versionName = "1.0.0-rc.9"
        // TMDB credential: read from tmdb.token (git-ignored) at build time and compiled into the APK. Optional: without it Watch says so and Cinema still works from your own server.
        val tmdb = rootProject.file("tmdb.token").takeIf { it.exists() }?.readText()?.trim() ?: ""
        buildConfigField("String", "TMDB", "\"$tmdb\"")
    }
    signingConfigs {
        create("omni") {
            // Updates install over an existing OMNI only when signed with the same key: put yours at android/omni.keystore (or set OMNI_KEYSTORE). Without one, the build is signed with the debug key.
            val ks = (System.getenv("OMNI_KEYSTORE")?.let { file(it) } ?: rootProject.file("omni.keystore")).takeIf { it.exists() }
            if (ks != null) { storeFile = ks; storePassword = System.getenv("OMNI_KEYSTORE_PASSWORD") ?: "android"; keyAlias = System.getenv("OMNI_KEY_ALIAS") ?: "aura"; keyPassword = System.getenv("OMNI_KEYSTORE_PASSWORD") ?: "android" }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("omni").takeIf { it.storeFile != null } ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    lint { checkReleaseBuilds = false; abortOnError = false }
}
dependencies {
    implementation(project(":apps:cinema"))
    implementation(project(":apps:weather"))
    implementation(project(":apps:server"))
    implementation(project(":apps:gallery"))
    implementation(project(":core:common"))
}
