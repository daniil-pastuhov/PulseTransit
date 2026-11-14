// Frames raw store screenshots (from scripts/capture-ios-screenshots.sh and
// scripts/capture-android-screenshots.sh): each goes on the app icon's blue gradient with a caption
// above. App Store shots keep the size of the original, which is already one App Store Connect
// accepts, and land in deliver's layout for `fastlane deliver`. With --play, the Android shots land
// in Gradle Play Publisher's listing folders instead, on a canvas no more than twice as long as it
// is wide, as Play requires.
//
//   swift scripts/frame-ios-screenshots.swift <raw-dir> iosApp/fastlane/screenshots
//   swift scripts/frame-ios-screenshots.swift --play <raw-dir> app/src/main/play/listings
//
// Scene 3, the board on a wall, is drawn here: the same device's dark board (7_board_dark) on a
// tablet hanging on a wall, for every device. On a phone it borrows the tablet's board: the iPad's
// for the App Store, the Android tablet's for Play (or, without one, the phone's own).
import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Captions per screenshot scene and App Store locale, in store order.
let captions: [String: [String: String]] = [
    "1_board_light": ["en-GB": "Live departures for all of Norway", "no": "Sanntid for hele Norge"],
    "2_widgets": ["en-GB": "Your next departure on the Home Screen", "no": "Neste avgang på hjemskjermen"],
    "3_wall": ["en-GB": "An always-on board for your wall", "no": "Avgangstavle på veggen, alltid på"],
    "4_on_spot": ["en-GB": "Follows you to the nearest stop", "no": "Følger deg til nærmeste holdeplass"],
    "5_trip": ["en-GB": "Tells you when to leave", "no": "Varsler deg når du må gå"],
    "6_route_search": ["en-GB": "The fastest route to anywhere", "no": "Raskeste reise dit du skal"],
    "7_board_dark": ["en-GB": "Weather and departures together", "no": "Vær og avganger på ett sted"],
    "8_departure": ["en-GB": "Only the lines you care about", "no": "Bare linjene du bryr deg om"],
]

/// Play's names: Android's home screen is not the "Home Screen" Apple capitalises.
let playCaptions: [String: [String: String]] = [
    "2_widgets": ["en-GB": "Your next departure on the home screen", "no": "Neste avgang på startskjermen"],
]

let space = CGColorSpace(name: CGColorSpace.sRGB)!
func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

func load(_ url: URL) -> CGImage {
    let source = CGImageSourceCreateWithURL(url as CFURL, nil)!
    return CGImageSourceCreateImageAtIndex(source, 0, nil)!
}

func save(_ image: CGImage, to url: URL) {
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    // JPEG at high quality: a quarter of the PNG size, and both stores accept it.
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
    precondition(CGImageDestinationFinalize(destination), "could not write \(url.path)")
}

func makeContext(_ size: CGSize) -> CGContext {
    CGContext(data: nil, width: Int(size.width), height: Int(size.height), bitsPerComponent: 8, bytesPerRow: 0,
              space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
}

/// Draws `text` centred and wrapped inside `rect`, in white bold system type.
func drawCaption(_ text: String, in rect: CGRect, fontSize: CGFloat, context: CGContext) {
    let font = CTFontCreateUIFontForLanguage(.emphasizedSystem, fontSize, nil)!
    var alignment = CTTextAlignment.center
    let style = withUnsafeBytes(of: &alignment) { pointer in
        CTParagraphStyleCreate([CTParagraphStyleSetting(spec: .alignment, valueSize: MemoryLayout<CTTextAlignment>.size, value: pointer.baseAddress!)], 1)
    }
    let attributed = NSAttributedString(string: text, attributes: [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): color(0xFFFFFF),
        NSAttributedString.Key(kCTParagraphStyleAttributeName as String): style,
    ])
    let framesetter = CTFramesetterCreateWithAttributedString(attributed)
    // Vertically centre the wrapped text in the caption band.
    let fitted = CTFramesetterSuggestFrameSizeWithConstraints(framesetter, CFRange(), nil, rect.size, nil)
    let textRect = CGRect(x: rect.minX, y: rect.midY - fitted.height / 2, width: rect.width, height: fitted.height)
    let frame = CTFramesetterCreateFrame(framesetter, CFRange(), CGPath(rect: textRect, transform: nil), nil)
    CTFrameDraw(frame, context)
}

/// `image` drawn into `rect` filling it, cropped at the bottom (or the right) where the aspect ratios
/// differ, so the top of a screen, its clock and first rows, always shows.
func drawFilling(_ image: CGImage, in rect: CGRect, context: CGContext) {
    let scale = max(rect.width / CGFloat(image.width), rect.height / CGFloat(image.height))
    let size = CGSize(width: CGFloat(image.width) * scale, height: CGFloat(image.height) * scale)
    context.saveGState()
    context.clip(to: rect)
    // Pinned to the top left (CoreGraphics' origin is bottom left).
    context.draw(image, in: CGRect(x: rect.minX, y: rect.maxY - size.height, width: size.width, height: size.height))
    context.restoreGState()
}

/// The shot on the gradient with its caption above, on a `canvas`-sized image.
func framed(_ shot: CGImage, caption: String, canvas: CGSize) -> CGImage {
    let w = canvas.width, h = canvas.height
    let context = makeContext(canvas)
    // Background: the icon's diagonal gradient.
    let gradient = CGGradient(colorsSpace: space, colors: [color(0x3949AB), color(0x1A237E)] as CFArray, locations: [0, 1])!
    context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: h), end: CGPoint(x: w, y: 0), options: [])

    // Caption band at the top, screenshot below it, scaled to leave a margin around it. The shot
    // keeps its own aspect ratio, so on a shorter canvas (Play's) it is narrower.
    let captionHeight = h * 0.16
    let margin = w * 0.07
    let sw = CGFloat(shot.width), sh = CGFloat(shot.height)
    let scale = min((w - 2 * margin) / sw, (h - captionHeight - margin) / sh)
    let shotSize = CGSize(width: sw * scale, height: sh * scale)
    let shotRect = CGRect(x: (w - shotSize.width) / 2, y: margin * 0.6, width: shotSize.width, height: shotSize.height)
    // Sized by the shorter side, so a two-line caption still fits the band on a landscape shot.
    drawCaption(caption, in: CGRect(x: margin, y: h - captionHeight, width: w - 2 * margin, height: captionHeight * 0.9),
                fontSize: min(w, h) * 0.062, context: context)

    let corner = min(shotSize.width, shotSize.height) * 0.06
    let rounded = CGPath(roundedRect: shotRect, cornerWidth: corner, cornerHeight: corner, transform: nil)
    context.saveGState()
    context.setShadow(offset: CGSize(width: 0, height: -w * 0.01), blur: w * 0.04, color: color(0x000000, alpha: 0.45))
    context.addPath(rounded)
    context.setFillColor(color(0x000000))
    context.fillPath()
    context.restoreGState()
    context.saveGState()
    context.addPath(rounded)
    context.clip()
    context.draw(shot, in: shotRect)
    context.restoreGState()
    return context.makeImage()!
}

/// A hallway wall with a tablet on it showing `board`, as a `size` "screenshot" for `framed`.
func wallScene(board: CGImage, size: CGSize) -> CGImage {
    let w = size.width, h = size.height
    let context = makeContext(size)
    // A warm, softly lit wall, and a floor strip with a skirting board at the bottom.
    let wall = CGGradient(colorsSpace: space, colors: [color(0xF1ECE4), color(0xDDD5C9)] as CFArray, locations: [0, 1])!
    let extend: CGGradientDrawingOptions = [.drawsBeforeStartLocation, .drawsAfterEndLocation]
    context.drawLinearGradient(wall, start: CGPoint(x: w * 0.3, y: h), end: CGPoint(x: w * 0.7, y: 0), options: extend)
    let light = CGGradient(colorsSpace: space, colors: [color(0xFFFFFF, alpha: 0.55), color(0xFFFFFF, alpha: 0)] as CFArray, locations: [0, 1])!
    context.drawRadialGradient(light, startCenter: CGPoint(x: w * 0.5, y: h * 0.62), startRadius: 0,
                               endCenter: CGPoint(x: w * 0.5, y: h * 0.62), endRadius: max(w, h) * 0.6, options: [])
    let floorHeight = h * 0.09
    let floor = CGGradient(colorsSpace: space, colors: [color(0xB08A64), color(0x8C6A4A)] as CFArray, locations: [0, 1])!
    context.saveGState()
    context.clip(to: CGRect(x: 0, y: 0, width: w, height: floorHeight))
    context.drawLinearGradient(floor, start: CGPoint(x: 0, y: floorHeight), end: CGPoint(x: 0, y: 0), options: extend)
    context.restoreGState()
    context.setFillColor(color(0xFAF8F4))
    context.fill(CGRect(x: 0, y: floorHeight, width: w, height: h * 0.022))

    // The tablet, in the board's own orientation, as large as the wall comfortably holds.
    let aspect = CGFloat(board.width) / CGFloat(board.height)
    let maxWidth = w * (aspect > 1 ? 0.86 : 0.8), maxHeight = h * 0.7
    var tabletWidth = maxWidth
    var screenWidth = tabletWidth * 0.94
    if screenWidth / aspect > maxHeight { screenWidth = maxHeight * aspect; tabletWidth = screenWidth / 0.94 }
    let bezel = (tabletWidth - screenWidth) / 2
    let screen = CGSize(width: screenWidth, height: screenWidth / aspect)
    let tablet = CGRect(x: (w - tabletWidth) / 2, y: floorHeight + (h - floorHeight) * 0.56 - (screen.height + 2 * bezel) / 2,
                        width: tabletWidth, height: screen.height + 2 * bezel)
    let tabletCorner = min(tablet.width, tablet.height) * 0.05
    let body = CGPath(roundedRect: tablet, cornerWidth: tabletCorner, cornerHeight: tabletCorner, transform: nil)
    context.saveGState()
    context.setShadow(offset: CGSize(width: w * 0.012, height: -w * 0.03), blur: w * 0.06, color: color(0x3A2E22, alpha: 0.38))
    context.addPath(body)
    context.setFillColor(color(0x18181B))
    context.fillPath()
    context.restoreGState()
    let screenRect = tablet.insetBy(dx: bezel, dy: bezel)
    let screenCorner = tabletCorner * 0.6
    context.saveGState()
    context.addPath(CGPath(roundedRect: screenRect, cornerWidth: screenCorner, cornerHeight: screenCorner, transform: nil))
    context.clip()
    drawFilling(board, in: screenRect, context: context)
    // A faint sheen across the glass.
    let sheen = CGGradient(colorsSpace: space, colors: [color(0xFFFFFF, alpha: 0.08), color(0xFFFFFF, alpha: 0)] as CFArray, locations: [0, 1])!
    context.drawLinearGradient(sheen, start: CGPoint(x: screenRect.minX, y: screenRect.maxY), end: CGPoint(x: screenRect.midX, y: screenRect.midY), options: [])
    context.restoreGState()
    return context.makeImage()!
}

var arguments = Array(CommandLine.arguments.dropFirst())
let play = arguments.first == "--play"
if play { arguments.removeFirst() }
let rawDir = URL(fileURLWithPath: arguments[0])
let outDir = URL(fileURLWithPath: arguments[1])
/// The devices whose dark board the wall shows, per store, the first one captured: a tablet, or
/// else (Play, without tablet shots) an old phone, as the listing says either will do.
let wallDevices = play ? ["tablet", "phone"] : ["ipad"]

for locale in ["en-GB", "no"] {
    let localeDir = rawDir.appendingPathComponent(locale)
    var files = ((try? FileManager.default.contentsOfDirectory(at: localeDir, includingPropertiesForKeys: nil)) ?? [])
        .filter { $0.pathExtension == "png" }
    if play { files = files.filter { $0.lastPathComponent.hasPrefix("phone_") || $0.lastPathComponent.hasPrefix("tablet_") } } else {
        files = files.filter { !$0.lastPathComponent.hasPrefix("phone_") && !$0.lastPathComponent.hasPrefix("tablet_") }
    }
    let wallBoard = wallDevices.map { localeDir.appendingPathComponent("\($0)_7_board_dark.png") }
        .first { FileManager.default.fileExists(atPath: $0.path) } ?? localeDir.appendingPathComponent("none")
    // name (without extension) -> image, with each device's wall scene added beside its shots.
    var shots: [(name: String, image: () -> CGImage)] = files.map { file in (file.deletingPathExtension().lastPathComponent, { load(file) }) }
    if FileManager.default.fileExists(atPath: wallBoard.path) {
        let devices = Set(files.compactMap { file -> String? in
            let name = file.deletingPathExtension().lastPathComponent
            return name.contains("-") ? nil : String(name.prefix { $0 != "_" })
        })
        for device in devices.sorted() {
            guard let sample = files.first(where: { $0.lastPathComponent.hasPrefix(device + "_") && !$0.lastPathComponent.contains("-") }) else { continue }
            let reference = load(sample)
            let size = CGSize(width: reference.width, height: reference.height)
            shots.append(("\(device)_3_wall", { wallScene(board: load(wallBoard), size: size) }))
        }
    }
    for (name, image) in shots.sorted(by: { $0.name < $1.name }) {
        // "iphone_1_board_light" -> device "iphone", scene "1_board_light"; a pose after the scene
        // ("duo_1_board_light-outer") shares the scene's caption.
        let device = String(name.prefix { $0 != "_" })
        let scene = String(name.drop { $0 != "_" }.dropFirst().prefix { $0 != "-" })
        guard let caption = (play ? playCaptions[scene]?[locale] : nil) ?? captions[scene]?[locale] else { continue }
        let shot = image()
        var canvas = CGSize(width: shot.width, height: shot.height)
        let out: URL
        if play {
            // Play: neither side more than twice the other.
            canvas.height = min(canvas.height, canvas.width * 2)
            canvas.width = min(canvas.width, canvas.height * 2)
            let listing = locale == "no" ? "no-NO" : "en-US"
            let folder = device == "tablet" ? "tablet-screenshots" : "phone-screenshots"
            out = outDir.appendingPathComponent("\(listing)/graphics/\(folder)/\(scene).jpg")
        } else {
            out = outDir.appendingPathComponent(locale).appendingPathComponent(name + ".jpg")
        }
        save(framed(shot, caption: caption, canvas: canvas), to: out)
        print("Wrote \(out.path)")
    }
}
