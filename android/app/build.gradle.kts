import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val dromProfitApiBaseUrl = providers.gradleProperty("dromProfitApiBaseUrl").orElse("").get().trim()
val demoScenarioProperty = providers.gradleProperty("dromProfitDemoScenario").orNull
val dromProfitDemoScenario = demoScenarioProperty ?: "empty"
val dromProfitDemoEmpty = demoScenarioProperty == null &&
    providers.gradleProperty("dromProfitDemoEmpty").orElse("false").get().toBooleanStrict()
require(dromProfitDemoScenario in setOf("empty", "seeded", "showcase", "list-error")) {
    "dromProfitDemoScenario должен быть empty, seeded, showcase или list-error."
}
if (dromProfitApiBaseUrl.isNotBlank()) {
    val endpoint = runCatching { URI(dromProfitApiBaseUrl) }.getOrNull()
    require(
        endpoint != null &&
            endpoint.scheme in listOf("http", "https") &&
            endpoint.host != null &&
            endpoint.rawUserInfo == null &&
            endpoint.rawQuery == null &&
            endpoint.rawFragment == null
    ) {
        "dromProfitApiBaseUrl должен содержать базовый HTTP(S)-адрес без логина, запроса и фрагмента."
    }
}

android {
    namespace = "ru.dedmos.dromprofit"
    compileSdk = 37
    defaultConfig {
        applicationId = "ru.dedmos.dromprofit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "DROM_PROFIT_API_BASE_URL", "\"$dromProfitApiBaseUrl\"")
        buildConfigField("String", "DROM_PROFIT_DEMO_SCENARIO", "\"$dromProfitDemoScenario\"")
        buildConfigField("boolean", "DROM_PROFIT_DEMO_EMPTY", dromProfitDemoEmpty.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val sharedFixtures = rootProject.file("../shared/fixtures")
    sourceSets.getByName("debug").assets.srcDir(sharedFixtures)
    testOptions.unitTests.all { test ->
        val calculationVectors = sharedFixtures.resolve("calculation-tests.json")
        test.inputs.file(calculationVectors)
        test.systemProperty("dromProfitCalculationVectors", calculationVectors.absolutePath)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.maplibre.compose)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.retrofit)
    implementation(libs.gson)
}
