import SwiftUI

/// Why a fetch failed, as a symbol: no connection on the device, or no answer from Entur.
func loadFailureSymbol(isOnline: Bool) -> String {
    isOnline ? "icloud.slash" : "wifi.slash"
}
