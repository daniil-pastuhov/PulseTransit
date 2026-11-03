import UIKit

/// Walking directions to a point in the user's map app: Google Maps when it is installed, else
/// Apple Maps, as Android prefers Google Maps too. Used by the trip details' "Show on map" and the
/// walk alert's "Open map". Looking for Google Maps needs `comgooglemaps` in the Info.plist's
/// `LSApplicationQueriesSchemes`; without it `canOpenURL` is always false.
///
/// Neither app may be installed: Apple Maps can be deleted too. Its link is a web link for that
/// reason, which then opens Apple's web map in the browser; should even that fail, Google Maps on
/// the web is tried.
enum WalkingDirections {
    static func open(latitude: Double, longitude: Double) {
        let destination = "\(latitude),\(longitude)"
        let application = UIApplication.shared
        if let google = URL(string: "comgooglemaps://?daddr=\(destination)&directionsmode=walking"),
           application.canOpenURL(google) {
            application.open(google)
            return
        }
        guard let apple = URL(string: "https://maps.apple.com/?daddr=\(destination)&dirflg=w") else { return }
        application.open(apple) { opened in
            guard !opened,
                  let web = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(destination)&travelmode=walking")
            else { return }
            application.open(web)
        }
    }
}
