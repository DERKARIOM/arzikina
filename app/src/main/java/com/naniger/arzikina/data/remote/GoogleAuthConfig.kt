package com.naniger.arzikina.data.remote

/**
 * Configuration publique Google Sign-In côté Android — voir
 * [com.naniger.arzikina.presentation.auth.GoogleSignInLauncher].
 *
 * [WEB_CLIENT_ID] : le Client ID OAuth de type **Web** (PAS le Client ID "Android", bien que cet
 * écran tourne sur Android) — contre-intuitif mais exigé par Credential Manager
 * (`GetGoogleIdOption.setServerClientId`, voir la documentation officielle Google « Server's
 * client ID ») : c'est cette valeur qui devient l'audience (`aud`) du jeton d'identité émis, et
 * c'est elle que le BACKEND doit reconnaître (voir `GOOGLE_CLIENT_ID_WEB` dans
 * `server/api/config/secrets.example.php` et `server/api/utils/google_token_verifier.php`). Le
 * Client ID "Android" (lié au package + empreintes SHA-1/SHA-256) sert uniquement à autoriser CET
 * APK à déclencher le sélecteur de compte Google, jamais utilisé directement dans le code.
 *
 * PUBLIC, jamais un secret : cette valeur apparaît en clair dans tout APK utilisant Google
 * Sign-In — Google le documente explicitement ainsi. Même raisonnement que
 * [com.naniger.arzikina.data.remote.RemoteConfig.BASE_URL] : une seule constante à modifier, isolée
 * ici plutôt que codée en dur ailleurs.
 *
 * À REMPLACER après création du Client ID Web dans Google Cloud Console (projet Firebase existant
 * `arzikina-8d4b7`, voir la KDoc de `GOOGLE_CLIENT_ID_WEB` côté serveur).
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "720142055727-m2hr8fl3uag9vqth6og8fjj34t4oocht.apps.googleusercontent.com"
}
