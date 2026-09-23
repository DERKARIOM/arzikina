plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.naniger.arzikina"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.naniger.arzikina"
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

    // Internationalisation : ne conserve dans l'APK que les langues réellement supportées par
    // Arzikina (voir domain/model/AppLanguage.kt et res/xml/locales_config.xml). Les bibliothèques
    // (Material, AppCompat…) embarquent sinon des dizaines de langues que l'interface n'utilise
    // jamais : APK plus léger, et textes des composants limités au français et à l'anglais.
    androidResources {
        localeFilters += listOf("fr", "en")
    }
}

// `flatMapLatest` (Flow) est encore marqué @ExperimentalCoroutinesApi, alors qu'il est utilisé
// partout dans les dépôts et ViewModels (changement d'utilisateur, filtres…). Opt-in au niveau du
// module plutôt que ~25 annotations @OptIn identiques. À retirer quand kotlinx.coroutines le
// stabilisera.
kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

// Room : conserve l'historique des schémas pour sécuriser les futures migrations
// (voir instructions projet : "Prévois les migrations de base de données dès le début").
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    // Désactivé temporairement (était "true") — diagnostic de l'erreur KSP
    // `[MissingType]` sur UserEntity apparue lors de la migration 22→23 (voir
    // docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, chantier synchronisation, étape 1).
    // La génération de code Kotlin par Room (plutôt que Java) est plus récente et
    // moins éprouvée avec KSP2 (Kotlin 2.4.0 / KSP 2.3.9 ici) — hypothèse la plus
    // probable vu que l'erreur est isolée à la SEULE entité utilisant
    // `@ColumnInfo(collate = ...)`, sans aucune ligne d'erreur Kotlin sous-jacente
    // (les 14 entités modifiées de façon identique n'ont pas ce problème). À
    // confirmer par un rebuild ; si ça règle le problème, décider ensemble si on
    // repasse en Kotlin plus tard (nouvelle version de Room/KSP) ou si on reste en
    // Java définitivement — pas une décision à prendre seul de façon permanente.
    arg("room.generateKotlin", "false")
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

    // Réseau (client de l'API de synchronisation, voir data/remote/ et
    // docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md) : OkHttp seul, pas de Retrofit — voir
    // gradle/libs.versions.toml pour le diagnostic complet.
    implementation(libs.okhttp)

    // Chargement d'images (photo de reçu)
    implementation(libs.coil)

    // Recadrage de la photo de profil (voir gradle/libs.versions.toml pour le détail des
    // coordonnées Maven Central)
    implementation(libs.image.cropper)

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
