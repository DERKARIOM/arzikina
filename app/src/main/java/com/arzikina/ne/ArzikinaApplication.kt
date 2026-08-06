package com.arzikina.ne

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Classe [Application] annotée pour Hilt : c'est le point d'ancrage du graphe
 * de dépendances racine de toute l'application (SingletonComponent).
 *
 * Aucune logique métier ne doit être ajoutée ici : cette classe ne fait
 * qu'activer l'injection de dépendances au démarrage du processus.
 */
@HiltAndroidApp
class ArzikinaApplication : Application()
