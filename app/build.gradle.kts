import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Gmail used to send OTP codes. Values come from (first match wins):
//   1. secrets.properties in the project root (git-ignored, see secrets.properties.example)
//   2. local.properties (git-ignored)
//   3. environment variables MAIL_USERNAME / MAIL_APP_PASSWORD
// NOTE: values compiled into BuildConfig can be extracted from the APK. Use a demo Gmail only.
fun loadProperties(name: String) = Properties().apply {
    val file = rootProject.file(name)
    if (file.exists()) file.inputStream().use { load(it) }
}
val secretProperties = loadProperties("secrets.properties")
val localProperties = loadProperties("local.properties")

fun secret(key: String): String =
    (secretProperties.getProperty(key) ?: localProperties.getProperty(key) ?: System.getenv(key) ?: "").trim()

fun asJavaString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val mailUsername = secret("MAIL_USERNAME")
val mailAppPassword = secret("MAIL_APP_PASSWORD")
if (mailUsername.isEmpty() || mailAppPassword.isEmpty()) {
    logger.warn("MAIL_USERNAME / MAIL_APP_PASSWORD chưa được cấu hình: chức năng gửi OTP qua Gmail sẽ báo lỗi cấu hình.")
}

android {
    namespace = "com.example.jewelrymanagement"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jewelrymanagement"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MAIL_USERNAME", asJavaString(mailUsername))
        buildConfigField("String", "MAIL_APP_PASSWORD", asJavaString(mailAppPassword))
        // VNPay sandbox credentials (test money only). A real shop must sign requests on a server.
        buildConfigField("String", "VNPAY_TMN_CODE", asJavaString(secret("VNPAY_TMN_CODE")))
        buildConfigField("String", "VNPAY_HASH_SECRET", asJavaString(secret("VNPAY_HASH_SECRET")))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md", "META-INF/NOTICE.md",
                "META-INF/LICENSE.txt", "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // JavaMail for Android: SMTP client used by GmailOtpSender (called from Kotlin)
    implementation(libs.android.mail)
    implementation(libs.android.activation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
