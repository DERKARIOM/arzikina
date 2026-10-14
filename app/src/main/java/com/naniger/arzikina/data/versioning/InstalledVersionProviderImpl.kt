package com.naniger.arzikina.data.versioning

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.naniger.arzikina.domain.versioning.InstalledVersion
import com.naniger.arzikina.domain.versioning.InstalledVersionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lit la version dans le `PackageManager` (donc toujours celle de l'APK réellement installé). */
@Singleton
internal class InstalledVersionProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : InstalledVersionProvider {

    private val version: InstalledVersion by lazy {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            InstalledVersion(
                versionCode = PackageInfoCompat.getLongVersionCode(info).toInt(),
                versionName = info.versionName.orEmpty()
            )
        }.getOrDefault(InstalledVersion(versionCode = 0, versionName = ""))
    }

    override fun installedVersion(): InstalledVersion = version
}
