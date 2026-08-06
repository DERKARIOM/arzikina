package com.arzikina.ne.domain.model

/**
 * Préférence d'apparence de l'utilisateur. [SYSTEM] suit le thème du
 * téléphone (comportement par défaut avant l'introduction de ce réglage) ;
 * [LIGHT] et [DARK] forcent un mode indépendamment du système.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
