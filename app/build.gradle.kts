import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val firebaseConfigured = file("google-services.json").exists()

if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val deployProperties = Properties()
val deployPropertiesFile = rootProject.file("deploy-config.properties")
if (deployPropertiesFile.exists()) {
    deployProperties.load(InputStreamReader(FileInputStream(deployPropertiesFile), Charsets.UTF_8))
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun deployProperty(key: String, defaultValue: String): String =
    deployProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue

val deployCustomerName = deployProperty("customer.name", "sakemaru")
val deployCustomerDisplayName = deployProperty("customer.display_name", deployCustomerName)
val localServerUrl = localProperties.getProperty("mdm.base.url")
    ?: deployProperty("environment.local.url", "http://10.0.2.2:8000")
val testServerUrl = deployProperty("environment.test.url", "https://mdm.sakemaru.test")
val prodServerUrl = deployProperty("environment.prod.url", testServerUrl)
val defaultEnvironment = deployProperty("environment.default", "TEST").uppercase()
val defaultServerUrl = when (defaultEnvironment) {
    "LOCAL" -> localServerUrl
    "PROD" -> prodServerUrl
    else -> testServerUrl
}

android {
    namespace = "com.smartlife.sakemaru.bansuke"
    compileSdk = 36

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.smartlife.sakemaru.bansuke"
        minSdk = 26
        targetSdk = 36
        versionCode = 10106
        versionName = "1.1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("Boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "DEPLOY_CUSTOMER_NAME", deployCustomerName.asBuildConfigString())
        buildConfigField("String", "DEPLOY_CUSTOMER_DISPLAY_NAME", deployCustomerDisplayName.asBuildConfigString())
        buildConfigField("String", "MDM_URL_LOCAL", localServerUrl.asBuildConfigString())
        buildConfigField("String", "MDM_URL_TEST", testServerUrl.asBuildConfigString())
        buildConfigField("String", "MDM_URL_PROD", prodServerUrl.asBuildConfigString())
        buildConfigField("String", "DEFAULT_MDM_ENVIRONMENT", defaultEnvironment.asBuildConfigString())
        buildConfigField("String", "DEFAULT_MDM_BASE_URL", defaultServerUrl.asBuildConfigString())
    }

    buildTypes {
        debug {
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_debug"
        }

        release {
            manifestPlaceholders["networkSecurityConfig"] = "@xml/network_security_config_release"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
}
