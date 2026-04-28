import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.cio)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.materialIconsCore)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.ktor.client.core)
            implementation(libs.multiplatformSettings)
            implementation(libs.supabase.postgrestKt)
            implementation(libs.supabase.realtimeKt)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

android {
    namespace = "com.xergioalex.kmppttdynamics"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xergioalex.kmppttdynamics"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.xergioalex.kmppttdynamics.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PTTDynamics"
            packageVersion = "1.0.0"
        }
    }
}

// ─── Supabase client config ──────────────────────────────────────────
// BuildKonfig generates a `BuildConfig` Kotlin object that is included
// in commonMain on every target. Treat it as a public-shippable config
// surface: anything baked here can be inspected by anyone running the
// app.
//
// Only two values belong here — both are designed by Supabase to ship
// inside clients (RLS is the gate, not the key):
//
//     SUPABASE_URL                   project URL
//     SUPABASE_PUBLISHABLE_KEY       anon-tier public key
//
// NEVER add any of the following to BuildKonfig, app resources, or any
// committed config — they grant elevated access:
//
//     SUPABASE_SECRET_KEY            (formerly service_role)
//     SUPABASE_ACCESS_TOKEN          personal access token (CLI)
//     SUPABASE_DB_PASSWORD           direct Postgres credential
//     SUPABASE_PROJECT_ID            (low risk, but CLI-only by convention)
//
// Those live exclusively in `.env` (gitignored) and are read by
// scripts/supabase_apply.sh on the developer's machine.
val envProps: Properties = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use(::load)
    }
}
fun envOrSystem(key: String, fallback: String = ""): String =
    envProps.getProperty(key)
        ?: System.getenv(key)
        ?: fallback

buildkonfig {
    packageName = "com.xergioalex.kmppttdynamics.config"
    objectName = "BuildConfig"
    // Intentionally NOT setting exposeObjectWithName: that generates a
    // @JsExport-decorated object, and @JsExport on a standalone object
    // is rejected by the Kotlin/Wasm compiler.

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "SUPABASE_URL", envOrSystem("SUPABASE_URL"))
        buildConfigField(FieldSpec.Type.STRING, "SUPABASE_PUBLISHABLE_KEY", envOrSystem("SUPABASE_PUBLISHABLE_KEY"))
    }
}
