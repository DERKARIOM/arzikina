import Foundation
import LocalAuthentication

/// [DeviceAuthenticator] adossé à `LocalAuthentication` : Face ID / Touch ID / Optic ID, avec
/// repli sur le code de l'iPhone (`.deviceOwnerAuthentication`, convention iOS — Android se limite
/// à l'empreinte).
struct LocalDeviceAuthenticator: DeviceAuthenticator {

    func availableMethod() -> DeviceAuthentication? {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { return nil }
        // Biométrie présente mais non configurée (aucun visage / doigt enregistré) : seul le code
        // servira, le libellé doit donc dire « code » et non « Face ID ».
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else { return .passcode }
        // `biometryType` n'est renseigné qu'après un appel à `canEvaluatePolicy`.
        switch context.biometryType {
        case .faceID: return .faceID
        case .touchID: return .touchID
        case .opticID: return .opticID
        case .none: return .passcode
        @unknown default: return .passcode
        }
    }

    func authenticate(reason: String) async -> Bool {
        let context = LAContext()
        return (try? await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }
}
