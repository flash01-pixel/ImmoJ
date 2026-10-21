// Configuration du module « app » (l'application elle-même).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Configuration intégrée à l'APK (fournie par le workflow de compilation via les
// propriétés Gradle supabaseUrl / supabaseAnonKey / ntfyTopic). Vide = l'appli
// demande les réglages au premier lancement.
fun propriete(nom: String): String = (project.findProperty(nom) as String?)?.trim() ?: ""

android {
    namespace = "be.immoj"
    compileSdk = 35

    defaultConfig {
        applicationId = "be.immoj"
        minSdk = 26          // Android 8.0 et plus
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"${propriete("supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${propriete("supabaseAnonKey")}\"")
        buildConfigField("String", "NTFY_TOPIC", "\"${propriete("ntfyTopic")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid)
}
