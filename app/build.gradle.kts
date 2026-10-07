import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing for the public "Spooky" Play build lives in a gitignored
// keystore.properties (see .gitignore). Absent (fresh clone / CI without the
// secret) → release falls back to debug signing so the build still succeeds.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "net.amiantos.lurker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // 33 (Android 13) so the app installs on e-ink Android devices, which lag
        // the mainline API level — a Boox Palma is API 33. Nothing here needs 34.
        minSdk = 31
        targetSdk = 36
        versionCode = 128
        versionName = "0.12.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ONE codebase, two identities. Fork-only features (custom aliases, DCC
    // send/chat, fserve) are gated at RUNTIME on the connected server's
    // capabilities (LurkerClient.serverExtended), so BOTH flavors carry all the
    // code and simply light those surfaces up on a fork server and hide them on
    // stock/hosted Lurker — no stripped branch to maintain. Flavor-specific name
    // + launcher icon live in src/<flavor>/res.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            // The GitHub-release build, "Spooky (GitHub)" — name + blue icon come
            // from src/main. Its own package: it used net.amiantos.lurker / "Lurker",
            // which is now the OFFICIAL Lurker app on Play (amiantos/lurker-android),
            // so the two couldn't be installed together and ours read as theirs.
            applicationId = "chat.irc.lurker.full"
        }
        create("spooky") {
            dimension = "distribution"
            // Public Play "Spooky for Lurker" — name + green icon in src/spooky.
            applicationId = "chat.irc.lurker"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + obfuscate. Play flags an app under 25% obfuscated
            // (it was 1% with this off), and the APK gets much smaller. Keep
            // rules for the reflection-driven libraries: src/main/keepRules.
            optimization {
                enable = true
            }
            // Only the spooky flavor's release ships to Play; debug fallback keeps
            // fresh clones building.
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // KICL pulls Netty, which ships duplicated META-INF entries that break the
    // APK merge unless excluded. (Direct-IRC-mode spike / Phase 0.)
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // LifecycleResumeEffect — re-pin the chat list to the tail on app resume
    // (version aligned with the Compose BOM's transitive lifecycle).
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(libs.okhttp)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    // Direct-IRC-mode engine (Phase 0 spike → Direct backend). Netty-based; see
    // packaging{} excludes above.
    implementation("org.kitteh.irc:client-lib:9.0.0")
    // Encrypt direct-mode IRC/SASL secrets at rest (with a plaintext fallback).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation(libs.junit)
    // Android ships org.json, but the unit-test android.jar stubs every method to
    // THROW — so any parser that touches JSON is untestable without the real thing
    // on the test classpath. Wire-format parsing is most of this client's risk
    // surface, so it needs to be testable.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
