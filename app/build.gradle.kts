import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Ensure debug.keystore is decoded from debug.keystore.base64 if debug.keystore does not exist,
// so that every build maintains the exact same signing signature certificate.
val ksFile = file("${rootDir}/debug.keystore")
val ksBase64File = file("${rootDir}/debug.keystore.base64")
if (!ksFile.exists() && ksBase64File.exists()) {
  try {
    val base64Str = ksBase64File.readText().trim()
    if (base64Str.isNotBlank()) {
      val decodedBytes = Base64.getDecoder().decode(base64Str)
      ksFile.writeBytes(decodedBytes)
      println("Decoded debug.keystore from debug.keystore.base64 successfully.")
    }
  } catch (e: Exception) {
    println("Warning: Failed to decode debug.keystore.base64: ${e.message}")
  }
}

// Ensure Release keystore my-upload-key.jks is decoded from KEYSTORE_BASE64 secret or keystore_base64.txt
val uploadKsFile = file("${rootDir}/my-upload-key.jks")
val keystoreBase64Env = System.getenv("KEYSTORE_BASE64") ?: System.getenv("KEYSTORE_BASE64_SECRET")
val txtKsFile = file("${rootDir}/keystore_base64.txt")
if (!uploadKsFile.exists() || uploadKsFile.length() == 0L) {
  val base64ToUse = when {
    !keystoreBase64Env.isNullOrBlank() -> keystoreBase64Env.trim()
    txtKsFile.exists() && txtKsFile.readText().trim().isNotBlank() -> txtKsFile.readText().trim()
    else -> null
  }
  if (base64ToUse != null) {
    try {
      val cleanBase64 = base64ToUse.replace("\\s".toRegex(), "")
      val decodedBytes = Base64.getDecoder().decode(cleanBase64)
      uploadKsFile.writeBytes(decodedBytes)
      println("Decoded my-upload-key.jks successfully for Release build.")
    } catch (e: Exception) {
      println("Warning: Failed to decode release keystore: ${e.message}")
    }
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.maxbird.ff.app"
    minSdk = 26
    targetSdk = 36
    val envVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()
    versionCode = envVersionCode ?: (System.currentTimeMillis() / 1000).toInt()
    versionName = System.getenv("VERSION_NAME") ?: "1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val storePasswordEnv = System.getenv("STORE_PASSWORD")
      val keyPasswordEnv = System.getenv("KEY_PASSWORD")
      val keyAliasEnv = System.getenv("KEY_ALIAS")
      
      if (file(keystorePath).exists()) {
        storeFile = file(keystorePath)
        storePassword = storePasswordEnv?.takeIf { it.isNotBlank() } ?: "android"
        keyAlias = keyAliasEnv?.takeIf { it.isNotBlank() } ?: "upload"
        keyPassword = keyPasswordEnv?.takeIf { it.isNotBlank() } ?: storePasswordEnv ?: "android"
        enableV1Signing = true
        enableV2Signing = true
      } else {
        // Fallback to debug keystore if release keys are not provided
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
        enableV1Signing = true
        enableV2Signing = true
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.ERROR }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.exoplayer.hls)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.session)
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.messaging)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.zxing.core)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
