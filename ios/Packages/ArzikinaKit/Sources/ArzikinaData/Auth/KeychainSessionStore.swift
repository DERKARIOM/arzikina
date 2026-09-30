#if canImport(Security)
import Foundation
import Security

/// Session stockée dans le Keychain iOS (chiffré par le système).
///
/// - `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` : lisible après le premier déverrouillage
///   (nécessaire à la future synchronisation en arrière-plan), jamais sauvegardée dans iCloud ni
///   restaurée sur un autre appareil ;
/// - un seul élément (service + compte fixes), réécrit à chaque connexion.
public final class KeychainSessionStore: SessionStore, @unchecked Sendable {

    private let service: String
    private let account = "current-session"

    public init(service: String = "com.naniger.arzikina.session") {
        self.service = service
    }

    public func load() -> StoredSession? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return try? JSONDecoder().decode(StoredSession.self, from: data)
    }

    public func save(_ session: StoredSession) throws {
        let data = try JSONEncoder().encode(session)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let updateStatus = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if updateStatus == errSecItemNotFound {
            var addQuery = baseQuery
            attributes.forEach { addQuery[$0.key] = $0.value }
            let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
            guard addStatus == errSecSuccess else { throw KeychainError(status: addStatus) }
        } else if updateStatus != errSecSuccess {
            throw KeychainError(status: updateStatus)
        }
    }

    public func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
    }

    /// Échec d'écriture dans le Keychain (code `OSStatus`).
    public struct KeychainError: Error, Equatable {
        public let status: OSStatus
    }
}
#endif
