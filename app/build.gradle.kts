plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// 读取当前版本号
fun getVersionCode(): Int {
    val versionFile = file("version.txt")
    return if (versionFile.exists()) {
        versionFile.readText().trim().toInt()
    } else {
        1
    }
}

fun getVersionName(): String {
    val versionFile = file("version_name.txt")
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        "1.0.0"
    }
}

fun incrementVersionCode() {
    val currentCode = getVersionCode()
    file("version.txt").writeText((currentCode + 1).toString())
}

fun incrementVersionName() {
    val currentName = getVersionName()
    val parts = currentName.split(".")
    if (parts.size == 3) {
        val major = parts[0].toIntOrNull() ?: 1
        val minor = parts[1].toIntOrNull() ?: 0
        val patch = parts[2].toIntOrNull() ?: 0
        val newName = "$major.$minor.${patch + 1}"
        file("version_name.txt").writeText(newName)
    }
}

android {
    namespace = "com.jdhelper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jdhelper"
        minSdk = 24
        targetSdk = 34
        versionCode = getVersionCode()
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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

    signingConfigs {
        create("release") {
            storeFile = file("jdhelper.keystore")
            storePassword = "jdhelper123"
            keyAlias = "jdhelper"
            keyPassword = "jdhelper123"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")


    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    ksp("com.google.dagger:hilt-android-compiler:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Release 构建成功后自动增加版本号
tasks.register("incrementVersionAfterRelease") {
    doLast {
        if (project.hasProperty("incrementVersion") && project.property("incrementVersion") == "true") {
            val currentCode = getVersionCode()
            val currentName = getVersionName()

            // 增加 versionCode
            file("version.txt").writeText((currentCode + 1).toString())

            // 增加 versionName (patch +1)
            val parts = currentName.split(".")
            if (parts.size == 3) {
                val newName = "${parts[0]}.${parts[1]}.${parts[2].toIntOrNull()?.plus(1) ?: 1}"
                file("version_name.txt").writeText(newName)
            }

            println("版本已更新: $currentName (versionCode: $currentCode) -> ${getVersionName()} (versionCode: ${getVersionCode()})")
        }
    }
}

// 使用 afterEvaluate 确保任务存在后再绑定
afterEvaluate {
    tasks.getByName("assembleRelease") {
        finalizedBy("incrementVersionAfterRelease")
    }
}