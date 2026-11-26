plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
android {
 namespace = "com.cobalt.app"
 compileSdk { version = release(37) }
 defaultConfig { applicationId = "com.cobalt.app"; minSdk = 26; targetSdk = 37; versionCode = 10000; versionName = "1.0.0" }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
 packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
dependencies {
 implementation(libs.androidx.core.ktx)
 implementation(libs.androidx.dynamicanimation)
 implementation(libs.kyant.shapes)
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.androidx.activity.compose)
 implementation(libs.androidx.compose.foundation)
 implementation(libs.androidx.compose.ui)
 implementation(libs.androidx.compose.material3)
 implementation(libs.androidx.compose.icons.extended)
 implementation(project(":coui-ui"))
 implementation(project(":coui-icons"))
 compileOnly(files("libs/xposed-stubs.jar"))
 testImplementation(libs.junit)
}
