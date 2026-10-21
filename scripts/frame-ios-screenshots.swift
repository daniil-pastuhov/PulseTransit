// Frames raw App Store screenshots (from scripts/capture-ios-screenshots.sh): each goes on the app
// icon's blue gradient with a caption above, at the size of the original, which is already one
// App Store Connect accepts. Output lands in deliver's layout for `fastlane deliver`.
//
//   swift scripts/frame-ios-screenshots.swift <raw-dir> iosApp/fastlane/screenshots
import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Captions per screenshot scene and App Store locale.
let captions: [String: [String: String]] = [
    "0_on_spot": ["en-GB": "Routes and departures from where you are", "no": "Reiser og avganger der du er"],
    "1_board_light": ["en-GB": "Live departures for your stops", "no": "Sanntidsavganger for holdeplassene dine"],
    "2_board_dark": ["en-GB": "Made for the wall, day and night", "no": "Laget for veggen, dag og natt"],
    "3_settings": ["en-GB": "Pick your stops, lines and directions", "no": "Velg holdeplasser, linjer og retninger"],
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
    // JPEG at high quality: a quarter of the PNG size, and App Store Connect accepts either.
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.9] as CFDictionary)
    precondition(CGImageDestinationFinalize(destination), "could not write \(url.path)")
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

func framed(_ shot: CGImage, caption: String) -> CGImage {
    let width = shot.width, height = shot.height
    let w = CGFloat(width), h = CGFloat(height)
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    // Background: the icon's diagonal gradient (CoreGraphics' origin is bottom left).
    let gradient = CGGradient(colorsSpace: space, colors: [color(0x3949AB), color(0x1A237E)] as CFArray, locations: [0, 1])!
    context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: h), end: CGPoint(x: w, y: 0), options: [])

    // Caption band at the top, screenshot below it, scaled to leave a margin around it.
    let captionHeight = h * 0.16
    let margin = w * 0.07
    let scale = min((w - 2 * margin) / w, (h - captionHeight - margin) / h)
    let shotSize = CGSize(width: w * scale, height: h * scale)
    let shotRect = CGRect(x: (w - shotSize.width) / 2, y: margin * 0.6, width: shotSize.width, height: shotSize.height)
    drawCaption(caption, in: CGRect(x: margin, y: h - captionHeight, width: w - 2 * margin, height: captionHeight * 0.9),
                fontSize: w * 0.062, context: context)

    let corner = shotSize.width * 0.06
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

let rawDir = URL(fileURLWithPath: CommandLine.arguments[1])
let outDir = URL(fileURLWithPath: CommandLine.arguments[2])
for locale in ["en-GB", "no"] {
    let files = (try? FileManager.default.contentsOfDirectory(at: rawDir.appendingPathComponent(locale), includingPropertiesForKeys: nil)) ?? []
    for file in files.filter({ $0.pathExtension == "png" }).sorted(by: { $0.path < $1.path }) {
        // "iphone_1_board_light.png" -> device "iphone", scene "1_board_light"
        let name = file.deletingPathExtension().lastPathComponent
        let scene = String(name.drop { $0 != "_" }.dropFirst())
        guard let caption = captions[scene]?[locale] else { continue }
        let out = outDir.appendingPathComponent(locale).appendingPathComponent(name + ".jpg")
        save(framed(load(file), caption: caption), to: out)
        print("Wrote \(out.path)")
    }
}
