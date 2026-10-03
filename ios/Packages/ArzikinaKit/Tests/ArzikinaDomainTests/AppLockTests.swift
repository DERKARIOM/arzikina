import XCTest
@testable import ArzikinaDomain

final class AppLockTests: XCTestCase {

    func testLaunchLocksOnlyWhenEnabledAndAvailable() {
        XCTAssertTrue(AppLockPolicy.locksAtLaunch(isEnabled: true, isAvailable: true))
        XCTAssertFalse(AppLockPolicy.locksAtLaunch(isEnabled: true, isAvailable: false), "Jamais bloqué hors de ses données")
        XCTAssertFalse(AppLockPolicy.locksAtLaunch(isEnabled: false, isAvailable: true))
    }

    func testReturnLocksAfterTheGracePeriod() {
        let left: EpochMillis = 1_000_000
        XCTAssertFalse(AppLockPolicy.locksOnReturn(isEnabled: true, isAvailable: true, backgroundedAt: left, now: left + 29_999))
        XCTAssertTrue(AppLockPolicy.locksOnReturn(isEnabled: true, isAvailable: true, backgroundedAt: left, now: left + 30_000))
        XCTAssertFalse(AppLockPolicy.locksOnReturn(isEnabled: true, isAvailable: true, backgroundedAt: nil, now: left), "Jamais passée en arrière-plan")
        XCTAssertFalse(AppLockPolicy.locksOnReturn(isEnabled: false, isAvailable: true, backgroundedAt: left, now: left + 60_000))
        XCTAssertFalse(AppLockPolicy.locksOnReturn(isEnabled: true, isAvailable: false, backgroundedAt: left, now: left + 60_000))
        XCTAssertTrue(AppLockPolicy.locksOnReturn(isEnabled: true, isAvailable: true, backgroundedAt: left, now: left - 1), "Horloge reculée : prudence")
    }
}
