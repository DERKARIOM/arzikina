package com.naniger.arzikina.domain.versioning

/** Version de l'application actuellement installée (source : le système, jamais une constante recopiée). */
interface InstalledVersionProvider {
    fun installedVersion(): InstalledVersion
}
