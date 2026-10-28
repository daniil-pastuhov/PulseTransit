import XCTest

/// Journey alerts in the UI, on the debug app launched with fixed data (-PTUITest YES): a board at
/// Oslo S with one route to Majorstuen. No network is needed.
///
/// Only what comes before the system's notification prompt is tested here. On a freshly created
/// simulator (CI's), `requestAuthorization` never answers, so following a journey cannot be driven
/// end to end; the shared rules behind it are covered by `JourneyAlertsViewModelTest` and friends.
final class JourneyAlertsUITests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        app.terminate()
    }

    // MARK: Paths

    func testRationaleExplainsWhyAndCanBeDismissed() {
        launch()
        openRoute()
        element("journey.notify").tap()

        XCTAssertTrue(element("journey.rationale.privacy").waitForExistence(timeout: 5))
        XCTAssertTrue(element("journey.rationale.privacy").label.contains("never sent anywhere"))
        XCTAssertTrue(element("journey.mode.location").exists)
        XCTAssertTrue(element("journey.mode.time").exists)

        element("journey.rationale.cancel").tap()
        XCTAssertTrue(element("journey.notify").waitForExistence(timeout: 5), "nothing is followed")
        closeDetails()
        XCTAssertFalse(element("journey.bar").exists)
    }

    // MARK: Helpers

    private func launch() {
        app.launchArguments = ["-PTUITest", "YES", "-PTUITestReset", "YES"]
        app.launch()
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func openRoute() {
        let banner = element("fastestTrip")
        XCTAssertTrue(banner.waitForExistence(timeout: 15), "the fixture route is on the board")
        banner.tap()
        XCTAssertTrue(element("journey.notify").waitForExistence(timeout: 5), "the details offer journey alerts")
    }

    private func closeDetails() {
        let close = element("tripDetails.close")
        if close.waitForExistence(timeout: 5) { close.tap() }
    }
}
