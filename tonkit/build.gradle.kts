import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.androidx.room)
    id("maven-publish")
}

// The Room 2.6.1 fixtures' expected contents, shared by the host, desktop and device tests.
val sharedFixtureDir = "src/test/sharedFixture"

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // 21, not 17: sqlcipher-room-jvm and sqlcipher-driver publish Java 21 bytecode only.
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Not commonMain: the kit is JVM code shared by the two JVM-backed targets only.
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Public API exposes okhttp3.EventListener.Factory (TonKit.getInstance).
                api(libs.okhttp)
                implementation(libs.okhttp.sse)
                implementation(libs.logging.interceptor)
                implementation(libs.moshi.kotlin)
                implementation(libs.moshi.adapters)
                // Moshi's KotlinJsonAdapterFactory must read metadata of this Kotlin version.
                implementation(libs.kotlin.reflect)
                implementation(libs.ton.kotlin.contract)
                implementation(libs.ton.kotlin.crypto)
                implementation(libs.kermit)
                implementation(libs.androidx.room.runtime)
                implementation(project(":tonkit-tweetnacl"))
            }
        }
        androidMain {
            // Sources come from AGP's own `main` source set; adding them here too would
            // list the same file in two fragments.
            dependsOn(jvmCommonMain)
            dependencies {
                implementation(libs.androidx.room.ktx)
                // Public API exposes its exceptions and DatabaseMigrationResult.
                api(libs.sqlcipher.room)
            }
        }
        val desktopMain by getting {
            kotlin.srcDir("src/main/java")
            dependsOn(jvmCommonMain)
            dependencies {
                api(libs.sqlcipher.room)
                // Android ships org.json in the platform.
                implementation(libs.org.json)
            }
        }

        val androidUnitTest by getting {
            kotlin.srcDir(sharedFixtureDir)
            dependencies {
                implementation(libs.junit)
                implementation(libs.kermit.test)
                implementation(libs.okhttp.mockwebserver)
                // The real implementation instead of android.jar's stubs.
                implementation(libs.org.json)
                implementation(libs.androidx.test.core)
                implementation(libs.robolectric)
            }
        }
        // Runs on a device only; never part of the published AAR.
        val androidInstrumentedTest by getting {
            kotlin.srcDir(sharedFixtureDir)
            dependencies {
                implementation(libs.androidx.junit)
                implementation(libs.androidx.espresso.core)
                // Builds an interrupted-migration staging file directly.
                implementation(libs.sqlcipher.android)
            }
        }
        val desktopTest by getting {
            // The host tests are pure JVM; they run on both targets.
            kotlin.srcDir("src/test/java")
            kotlin.srcDir(sharedFixtureDir)
            resources.srcDir("src/test/resources")
            dependencies {
                implementation(libs.junit)
                implementation(libs.kermit.test)
                implementation(libs.okhttp.mockwebserver)
                implementation(libs.kotlinx.coroutines.test)
                // Plaintext fixtures only; the kit itself opens databases through SQLCipher.
                implementation(libs.sqlite.bundled)
                // Reads and stages encrypted files directly; aligned with sqlcipher-room.
                implementation(libs.sqlcipher.driver)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    // KMP naming trap: kspAndroidTest is the JVM unit-test source set.
    add("kspAndroidTest", libs.androidx.room.compiler)
}

android {
    namespace = "io.horizontalsystems.tonkit"
    compileSdk = 35

    sourceSets {
        // The Room 2.6.1 fixtures; read-only, shared with the host tests.
        getByName("androidTest").resources.srcDir("src/test/resources")
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP's "test" lifecycle task only aggregates Android unit tests; wire in the desktop target too.
afterEvaluate {
    tasks.named("test") {
        dependsOn("desktopTest")
    }
}
