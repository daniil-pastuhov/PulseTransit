import XCTest

/// Journey alerts end to end, on the debug app launched with fixed data (-PTUITest YES): a board at
/// Oslo S with one route to Majorstuen whose first alert is due at once. No network is needed.
///
/// The system's permission prompts are answered "Allow" whenever they appear; once granted on a
/// simulator they no longer appear, so each test works whether or not it is the first to run.
final class JourneyAlertsUITests: XCTestCase {
    private let app = XCUIApplication()
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

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

    func testTimeOnlyAlertsFollowTheJourneyUntilStopped() {
        launch()
        follow(mode: "journey.mode.time")

        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10), "the details offer to stop")
        closeDetails()
        let bar = element("journey.bar")
        XCTAssertTrue(bar.waitForExistence(timeout: 5))
        XCTAssertTrue(bar.label.contains("Majorstuen"))

        element("journey.bar.stop").tap()
        XCTAssertTrue(waitForDisappearance(of: bar), "stopping takes the bar down")
    }

    func testNoticingAMissedRideFollowsWithLocation() {
        // Asked afresh, so the prompt is part of the test whatever earlier tests answered.
        app.resetAuthorizationStatus(for: .location)
        launch()
        follow(mode: "journey.mode.location")

        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10))
        closeDetails()
        XCTAssertTrue(element("journey.bar").waitForExistence(timeout: 5))
    }

    /// With both permissions granted, the next journey starts in the mode chosen before. (After
    /// "live times" location is still never asked, so the rationale would rightly come back.)
    func testTheChosenModeIsNotAskedForAgain() {
        launch()
        follow(mode: "journey.mode.location")
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10))
        element("journey.stop").tap()

        element("journey.notify").tap()
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 5), "followed at once")
        XCTAssertFalse(element("journey.rationale.privacy").exists, "without the rationale")
    }

    func testTheFollowedJourneyIsRestoredAfterRelaunch() {
        launch()
        follow(mode: "journey.mode.time")
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10))

        app.terminate()
        launch(reset: false)

        let bar = element("journey.bar")
        XCTAssertTrue(bar.waitForExistence(timeout: 10), "the saved journey is followed again")
        bar.tap()
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 5), "and its details open from the bar")
    }

    /// The fixture's walk to the first stop is due at once, so its alert comes as soon as the
    /// journey is followed. Tapping it in Notification Center opens the journey, as "Show journey" does.
    func testTheFirstAlertArrivesAndOpensTheJourney() {
        launch()
        follow(mode: "journey.mode.time")
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10))

        XCUIDevice.shared.press(.home)
        // The Live Activity's first start asks whether to allow them; it is part of the journey too.
        let allowActivities = springboard.buttons["Allow"].firstMatch
        if allowActivities.waitForExistence(timeout: 2) { allowActivities.tap() }
        // Notification Center, pulled down from the top edge.
        let top = springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
        top.press(forDuration: 0.1, thenDragTo: springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)))
        let delivered = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Walk to Jernbanetorget"))
            .firstMatch
        XCTAssertTrue(delivered.waitForExistence(timeout: 15), "the walk alert is delivered")
        delivered.tap()
        // On the Lock Screen a tap first offers "Open" (or expands the app's group of alerts).
        let open = springboard.buttons["Open"].firstMatch
        if open.waitForExistence(timeout: 3) { open.tap() } else if app.state != .runningForeground { delivered.tap() }

        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
        XCTAssertTrue(element("journey.stop").waitForExistence(timeout: 10), "the journey's details are open")
    }

    // MARK: Helpers

    private func launch(reset: Bool = true) {
        app.launchArguments = ["-PTUITest", "YES", "-PTUITestReset", reset ? "YES" : "NO"]
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

    /// Opens the route, taps the button, picks `mode` in the rationale and allows what it asks for.
    private func follow(mode: String) {
        openRoute()
        element("journey.notify").tap()
        let choice = element(mode)
        XCTAssertTrue(choice.waitForExistence(timeout: 5))
        choice.tap()
        allowSystemPrompts()
    }

    /// Answers the notification and location prompts, one after the other, if iOS shows them. The
    /// buttons are searched for anywhere in the system UI: iOS does not always present them as alerts.
    private func allowSystemPrompts() {
        let allow = springboard.descendants(matching: .button)
            .matching(NSPredicate(format: "label IN %@", ["Allow", "Allow While Using App"]))
            .firstMatch
        for _ in 0 ..< 2 {
            guard allow.waitForExistence(timeout: 6) else { return }
            allow.tap()
        }
    }

    /// Closes the route's details. An alert's banner may cover the close button at first, so it is
    /// pushed away before the tap.
    private func closeDetails() {
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS[c] %@", "Walk to Jernbanetorget"))
            .firstMatch
        if banner.exists, banner.isHittable { banner.swipeUp() }
        let close = element("tripDetails.close")
        if close.waitForExistence(timeout: 5) { close.tap() }
    }

    private func waitForDisappearance(of element: XCUIElement, timeout: TimeInterval = 5) -> Bool {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        return XCTWaiter.wait(for: [gone], timeout: timeout) == .completed
    }
}
