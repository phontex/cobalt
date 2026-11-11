plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
android {
 namespace = "app.cobalt.translate"
 compileSdk { version = release(37) }
 defaultConfig { applicationId = "app.cobalt.translate"; minSdk = 26; targetSdk = 37; versionCode = 56; versionName = "0.56-package-profiles" }
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
}
