import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
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
            // FORK-RENAME (optional): the iOS framework name is referenced from
            // iosApp/iosApp/ContentView.swift via `import ComposeApp`. If you rename
            // here, also update the Swift import. Most forks keep "ComposeApp" — it
            // is an internal name no end user sees. See docs/FORK_CUSTOMIZATION.md.
            baseName = "ComposeApp"
            isStatic = true
            // SQLDelight's NativeSqliteDriver calls into libsqlite3 (system framework
            // on iOS); tell the Kotlin/Native linker to link it so symbols like
            // _sqlite3_open_v2 are resolved when Xcode links the app.
            linkerOpts("-lsqlite3")
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
            implementation(libs.sqldelight.driver.android)
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
            implementation(libs.multiplatformSettings)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val nonWebMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(nonWebMain)
        iosMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.driver.sqlite)
        }
    }
}

sqldelight {
    databases {
        create("TodoDatabase") {
            packageName.set("com.xergioalex.kmptodoapp.db")
            srcDirs.setFrom("src/nonWebMain/sqldelight")
            generateAsync.set(false)
        }
    }
}

android {
    // FORK-RENAME: namespace must match the Kotlin package; both change together when forking.
    // See docs/FORK_CUSTOMIZATION.md.
    namespace = "com.xergioalex.kmptodoapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // FORK-RENAME: applicationId is the Play Store identity. NEVER change after publishing.
        // Pick the final value before your first Play upload. See docs/FORK_CUSTOMIZATION.md.
        applicationId = "com.xergioalex.kmptodoapp"
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
        // FORK-RENAME: mainClass must match the Kotlin package of jvmMain/main.kt.
        // See docs/FORK_CUSTOMIZATION.md.
        mainClass = "com.xergioalex.kmptodoapp.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // FORK-RENAME: packageName is the desktop installer identifier.
            packageName = "com.xergioalex.kmptodoapp"
            packageVersion = "1.0.0"
        }
    }
}
