plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repo = providers.gradleProperty("milon.repo").getOrElse("eliahousmietanski-crypto/milon")
// Version des données intégrées dans l'APK (fixée par la CI, voir .github/workflows/build.yml)
val dataVersion = providers.gradleProperty("milon.dataVersion").getOrElse("0").toInt()

android {
    namespace = "com.milon.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.milon.dictionnaire"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("milon.versionCode").get().toInt()
        versionName = providers.gradleProperty("milon.versionName").get()
        buildConfigField("String", "REPO", "\"$repo\"")
        buildConfigField("int", "DATA_VERSION", "$dataVersion")
    }

    // La clé de signature n'est jamais dans le dépôt : la CI la reconstitue depuis
    // les secrets GitHub MILON_KEYSTORE_BASE64 et MILON_KEYSTORE_PASSWORD.
    val keystore = rootProject.file("keystore/milon.jks")
    val keystorePassword = System.getenv("MILON_KEYSTORE_PASSWORD")
    val canSign = keystore.exists() && !keystorePassword.isNullOrEmpty()
    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "milon"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sans clé (développement), l'APK est signé avec la clé de debug
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    androidResources {
        noCompress += "db"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
