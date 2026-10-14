package com.naniger.arzikina.domain.versioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionGateTest {

    private val installed = InstalledVersion(versionCode = 7, versionName = "1.2.0")

    private fun policy(
        min: Int = 0,
        force: Boolean = false,
        maintenance: Boolean = false,
        message: String? = null
    ) = AppVersionPolicy(
        minVersionCode = min,
        forceUpdate = force,
        minVersionName = "1.3.0",
        updateUrl = "https://play.google.com/store/apps/details?id=com.naniger.arzikina",
        latestVersionName = "1.3.0",
        releaseNotes = "Notes",
        maintenanceEnabled = maintenance,
        maintenanceMessage = message
    )

    @Test
    fun `sans politique l app reste utilisable`() {
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(installed, null, includeMaintenance = true))
    }

    @Test
    fun `version inferieure au minimum avec obligation est bloquee`() {
        val result = VersionGate.evaluate(installed, policy(min = 8, force = true), includeMaintenance = true)
        assertTrue(result is VersionGateResult.UpdateRequired)
        result as VersionGateResult.UpdateRequired
        assertEquals(8, result.minVersionCode)
        assertEquals(installed, result.installed)
    }

    @Test
    fun `version egale ou superieure au minimum est autorisee`() {
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(installed, policy(min = 7, force = true), true))
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(installed, policy(min = 3, force = true), true))
    }

    @Test
    fun `sans obligation une version ancienne n est jamais bloquee`() {
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(installed, policy(min = 99, force = false), true))
    }

    @Test
    fun `la comparaison est numerique et non textuelle`() {
        // « 10 » < « 9 » en texte : seul l entier compte.
        val old = InstalledVersion(versionCode = 9, versionName = "1.9.0")
        assertTrue(VersionGate.evaluate(old, policy(min = 10, force = true), true) is VersionGateResult.UpdateRequired)
        val recent = InstalledVersion(versionCode = 10, versionName = "1.10.0")
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(recent, policy(min = 9, force = true), true))
    }

    @Test
    fun `maintenance retenue seulement pour une reponse fraiche`() {
        val p = policy(maintenance = true, message = "Retour à 18h")
        assertEquals(VersionGateResult.Maintenance("Retour à 18h"), VersionGate.evaluate(installed, p, includeMaintenance = true))
        assertEquals(VersionGateResult.Allowed, VersionGate.evaluate(installed, p, includeMaintenance = false))
    }

    @Test
    fun `la mise a jour obligatoire prime sur la maintenance`() {
        val result = VersionGate.evaluate(installed, policy(min = 8, force = true, maintenance = true), true)
        assertTrue(result is VersionGateResult.UpdateRequired)
    }
}
