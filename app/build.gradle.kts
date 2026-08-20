plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.arzikina.ne"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.arzikina.ne"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        viewBinding = true
    }
}

// Room : conserve l'historique des schémas pour sécuriser les futures migrations
// (voir instructions projet : "Prévois les migrations de base de données dès le début").
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Interface utilisateur : Views + Material Components (Material Design 3),
    // pas de Jetpack Compose — voir instructions projet.
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)

    // Navigation (Fragments)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Injection de dépendances (Hilt)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.fragment)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Persistance locale (Room / SQLite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Préférences utilisateur (thème, devise, langue)
    implementation(libs.androidx.datastore.preferences)

    // Authentification biométrique (verrouillage à l'ouverture, actions sensibles — voir
    // domain/repository/BiometricAuthenticator.kt et data/security/BiometricAuthenticatorImpl.kt)
    implementation(libs.androidx.biometric)

    // Tâches d'arrière-plan (rappels, sauvegardes automatiques futures)
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines / Flow
    implementation(libs.kotlinx.coroutines.android)

    // Sauvegarde et restauration (export/import JSON)
    implementation(libs.kotlinx.serialization.json)

    // Chargement d'images (photo de reçu)
    implementation(libs.coil)

    // Graphiques (statistiques : camembert, barres, évolution)
    implementation(libs.vico.views)

    // Extraction de texte des reçus PDF (voir data/receipts/ReceiptTextExtractor.kt) — utilisée
    // pour SUGGÉRER un montant à l'utilisateur, jamais pour le renseigner automatiquement (voir
    // cahier des charges "Gestion des reçus" et la doc de ReceiptDetailViewModel).
    implementation(libs.pdfbox.android)

    // Tests unitaires JVM (src/test) : ViewModels des prêts/emprunts — voir le détail de chaque
    // dépendance dans gradle/libs.versions.toml.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Tests instrumentés (src/androidTest) : LoanRepositoryImpl avec une base Room en mémoire
    // réelle (voir gradle/libs.versions.toml) — Hilt est déjà en dépendance `implementation`
    // ci-dessus, pas besoin de le redéclarer ici (même module Gradle).
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.mockk.android)
}
