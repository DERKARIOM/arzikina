import Foundation

/// Session persistée sur l'appareil. Contient le TOKEN : ne doit être stockée que dans le Keychain
/// (voir `KeychainSessionStore`), jamais dans UserDefaults ni dans un fichier.
public struct StoredSession: Codable, Equatable, Sendable {
    public let token: String
    public let userId: String
    public let fullName: String
    public let expiresAt: Int64

    public init(token: String, userId: String, fullName: String, expiresAt: Int64) {
        self.token = token
        self.userId = userId
        self.fullName = fullName
        self.expiresAt = expiresAt
    }
}

/// Stockage de la session courante (une seule par appareil).
public protocol SessionStore: Sendable {
    func load() -> StoredSession?
    func save(_ session: StoredSession) throws
    func clear()
}

/// Stockage en mémoire : tests unitaires et environnements sans Keychain.
public final class InMemorySessionStore: SessionStore, @unchecked Sendable {
    private let lock = NSLock()
    private var session: StoredSession?

    public init(session: StoredSession? = nil) {
        self.session = session
    }

    public func load() -> StoredSession? {
        lock.lock()
        defer { lock.unlock() }
        return session
    }

    public func save(_ session: StoredSession) throws {
        lock.lock()
        defer { lock.unlock() }
        self.session = session
    }

    public func clear() {
        lock.lock()
        defer { lock.unlock() }
        session = nil
    }
}
