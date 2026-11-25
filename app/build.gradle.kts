plugins {
    alias(libs.plugins.android.application)
}

val releaseKeystorePath: String? = providers.environmentVariable("NOTESBRIDGE_KEYSTORE_PATH").orNull
val releaseKeystorePassword: String? = providers.environmentVariable("NOTESBRIDGE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.environmentVariable("NOTESBRIDGE_KEY_ALIAS").orNull
val releaseKeyPassword: String? = providers.environmentVariable("NOTESBRIDGE_KEY_PASSWORD").orNull
val hasReleaseSigning =
    listOf(releaseKeystorePath, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all {
        !it.isNullOrBlank()
    }

android {
    namespace = "com.jvmd.notesbridge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jvmd.notesbridge"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("../legal")
        }
        getByName("test") {
            resources.directories.add("src/sharedTest/fixtures")
        }
        getByName("androidTest") {
            assets.directories.add("src/sharedTest/fixtures")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.jsoup)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.androidsvg)

    testImplementation(libs.junit)
    testImplementation(libs.kxml2)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
