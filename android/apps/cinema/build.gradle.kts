plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "com.rasha.omni.cinema"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.rasha.omni.cinema"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // TMDB credential: read from tmdb.token (git-ignored) at build time. It is compiled into the APK, so this APK is for your own TV and must not be shared.
        val tmdb = rootProject.file("tmdb.token").takeIf { it.exists() }?.readText()?.trim() ?: ""
        buildConfigField("String", "TMDB", "\"$tmdb\"")
    }
    signingConfigs {
        create("omni") {
            // Cinema shares the launcher's signing key: that is what makes the signature permission (com.rasha.omni.permission.CINEMA) mean "an OMNI app".
            val shared = file(System.getenv("OMNI_SHARED_KEYSTORE") ?: rootProject.file("../launcher/omni.keystore").path)
            if (shared.exists()) { storeFile = shared; storePassword = "android"; keyAlias = "aura"; keyPassword = "android" }
            else { storeFile = rootProject.file("omni-os.keystore"); storePassword = "omni-os-local"; keyAlias = "omni"; keyPassword = "omni-os-local" }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("omni")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json") }
}
dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:common"))
    implementation(project(":core:library"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource:1.4.1")
    testImplementation("junit:junit:4.13.2")
}
