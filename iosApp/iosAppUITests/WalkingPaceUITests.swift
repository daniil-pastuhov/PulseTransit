import CoreLocation
import XCTest

/// The walking speed proposal, on the debug app's on-spot fixtures with a measured pace
/// (-PTUITestScenario onspotpace: about 5.6 km/h on three days, Normal set, so Fast is proposed).
/// The shared rules behind it are covered by WalkingPaceTest and the ViewModel tests.
final class WalkingPaceUITests: XCTestCase {
    private let app = XCUIApplication()

    override func setUpWithError() throws {
        continueAfterFailure = false
        // On-spot mode needs a location; Oslo S finds the fixtures' stop.
        guard #available(iOS 16.4, *) else { throw XCTSkip("simulating a location needs iOS 16.4") }
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 59.9111, longitude: 10.7528))
    }

    override func tearDown() {
        app.terminate()
    }

    /// "Not now" takes the proposal away, and it stays away after a relaunch.
    func testNotNowDeclinesTheProposal() {
        launch(reset: true)
        XCTAssertTrue(element("walkingPace.proposal").waitForExistence(timeout: 20), "the measured pace proposes Fast")
        XCTAssertTrue(element("walkingPace.proposal").label.contains("5.6 km/h"))
        element("walkingPace.dismiss").tap()
        XCTAssertTrue(waitForAbsence(element("walkingPace.proposal")), "declined")

        app.terminate()
        launch(reset: false)
        XCTAssertTrue(app.staticTexts["Oslo S"].waitForExistence(timeout: 20), "the board is up")
        XCTAssertFalse(element("walkingPace.proposal").exists, "a declined level is not proposed again")
    }

    /// "Use Fast" sets the walking speed, which Settings then shows, with the pace measured.
    func testUseFastSetsTheWalkingSpeed() {
        launch(reset: true)
        XCTAssertTrue(element("walkingPace.proposal").waitForExistence(timeout: 20), "the measured pace proposes Fast")
        element("walkingPace.accept").tap()
        XCTAssertTrue(waitForAbsence(element("walkingPace.proposal")), "accepted")

        app.buttons["Settings"].tap()
        let measured = element("settings.walkingPace.measured")
        for _ in 0 ..< 6 where !measured.exists { app.swipeUp() }
        XCTAssertTrue(measured.waitForExistence(timeout: 5))
        XCTAssertTrue(measured.label.contains("5.6 km/h"))
        XCTAssertTrue(app.segmentedControls.buttons["Fast"].isSelected, "Settings shows the accepted speed")
        XCTAssertFalse(element("settings.walkingPace.accept").exists, "nothing left to propose")
        XCTAssertTrue(element("settings.walkingPace.forget").exists)
    }

    /// Settings offers the proposal too, and forgetting the pace takes the measurement away.
    func testSettingsOffersTheProposalAndForgets() {
        launch(reset: true, settings: true)
        let accept = element("settings.walkingPace.accept")
        let forget = element("settings.walkingPace.forget")
        for _ in 0 ..< 6 where !forget.exists { app.swipeUp() }
        XCTAssertTrue(accept.waitForExistence(timeout: 5), "Settings proposes Fast too")
        XCTAssertEqual(accept.label, "Use Fast")
        forget.tap()
        XCTAssertTrue(waitForAbsence(accept), "nothing measured, nothing proposed")
        XCTAssertFalse(forget.exists)
        XCTAssertTrue(element("settings.walkingPace.measured").label.contains("Not measured yet"))
    }

    // MARK: Helpers

    private func launch(reset: Bool, settings: Bool = false) {
        app.launchArguments = ["-PTUITest", "YES", "-PTUITestScenario", "onspotpace"]
        if reset { app.launchArguments += ["-PTUITestReset", "YES"] }
        if settings { app.launchArguments += ["-PTOpenSettings", "YES"] }
        app.launch()
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func waitForAbsence(_ element: XCUIElement, timeout: TimeInterval = 5) -> Bool {
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: element)
        return XCTWaiter().wait(for: [gone], timeout: timeout) == .completed
    }
}
