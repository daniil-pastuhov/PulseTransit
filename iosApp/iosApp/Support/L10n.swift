import Foundation

/// Looks up a string from Localizable.xcstrings, which scripts/sync-ios-strings.py generates from the
/// Android string resources; keys are the Android resource names. Arguments fill the format
/// specifiers, and an integer argument also picks the plural form where the string has one.
func L(_ key: String, _ arguments: CVarArg...) -> String {
    let format = NSLocalizedString(key, comment: "")
    return arguments.isEmpty ? format : String(format: format, locale: Locale.current, arguments: arguments)
}
