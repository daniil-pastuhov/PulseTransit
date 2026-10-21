// Renders the App Store "Header and Search Results" creative asset: one universal 16:9 PNG per
// App Store locale (5244 x 2950, no alpha), which App Store Connect crops to 21:9 for the product
// page header and to 3:2 for search results. Everything that matters stays inside the area both
// crops keep, the central ~84% of the width and ~76% of the height. It uses the raw On the spot
// iPhone and wall-board iPad screenshots from scripts/capture-ios-screenshots.sh.
//
//   swift scripts/render-ios-store-header.swift <raw-dir> iosApp/fastlane/creative_assets
import CoreGraphics
import CoreText
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// Headline and the line under it, per App Store locale.
let texts: [String: (headline: String, subline: String)] = [
    "en-GB": ("Live departures and routes", "On the go, or on the wall"),
    "no": ("Sanntidsavganger og reiser", "På farten eller på veggen"),
]

let width = 5244, height = 2950
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
    let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    precondition(CGImageDestinationFinalize(destination), "could not write \(url.path)")
}

/// Draws left-aligned wrapped text with its top at `top` (top-left coordinates); returns its height.
@discardableResult
func drawText(_ text: String, x: CGFloat, top: CGFloat, width: CGFloat, size: CGFloat, weight: CTFontUIFontType,
              color textColor: CGColor, context: CGContext) -> CGFloat {
    let font = CTFontCreateUIFontForLanguage(weight, size, nil)!
    let attributed = NSAttributedString(string: text, attributes: [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTForegroundColorAttributeName as String): textColor,
    ])
    let framesetter = CTFramesetterCreateWithAttributedString(attributed)
    let fitted = CTFramesetterSuggestFrameSizeWithConstraints(framesetter, CFRange(), nil, CGSize(width: width, height: .greatestFiniteMagnitude), nil)
    let rect = CGRect(x: x, y: CGFloat(height) - top - fitted.height, width: width, height: fitted.height)
    CTFrameDraw(CTFramesetterCreateFrame(framesetter, CFRange(), CGPath(rect: rect, transform: nil), nil), context)
    return fitted.height
}

/// Draws a screenshot in a plain black device bezel. `rect` is the screen, in top-left coordinates.
func drawDevice(_ shot: CGImage, screen rect: CGRect, bezel: CGFloat, corner: CGFloat, context: CGContext) {
    let screen = CGRect(x: rect.minX, y: CGFloat(height) - rect.maxY, width: rect.width, height: rect.height)
    let body = screen.insetBy(dx: -bezel, dy: -bezel)
    let bodyPath = CGPath(roundedRect: body, cornerWidth: corner + bezel, cornerHeight: corner + bezel, transform: nil)
    context.saveGState()
    context.setShadow(offset: CGSize(width: 0, height: -40), blur: 160, color: color(0x000000, alpha: 0.5))
    context.addPath(bodyPath)
    context.setFillColor(color(0x111111))
    context.fillPath()
    context.restoreGState()
    context.saveGState()
    context.addPath(CGPath(roundedRect: screen, cornerWidth: corner, cornerHeight: corner, transform: nil))
    context.clip()
    context.draw(shot, in: screen)
    context.restoreGState()
}

func render(phone: CGImage, tablet: CGImage, headline: String, subline: String) -> CGImage {
    let w = CGFloat(width), h = CGFloat(height)
    let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    // The icon's diagonal gradient, as on the screenshots.
    let gradient = CGGradient(colorsSpace: space, colors: [color(0x3949AB), color(0x1A237E)] as CFArray, locations: [0, 1])!
    context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: h), end: CGPoint(x: w, y: 0), options: [])

    // The iPad with the wall board behind, the iPhone on the spot in front of its lower left.
    let tabletHeight: CGFloat = 1960
    let tabletScreen = CGRect(x: 2960, y: 500, width: tabletHeight * CGFloat(tablet.width) / CGFloat(tablet.height), height: tabletHeight)
    drawDevice(tablet, screen: tabletScreen, bezel: 56, corner: 70, context: context)
    let phoneHeight: CGFloat = 1640
    let phoneScreen = CGRect(x: 2560, y: 2460 - phoneHeight, width: phoneHeight * CGFloat(phone.width) / CGFloat(phone.height), height: phoneHeight)
    drawDevice(phone, screen: phoneScreen, bezel: 34, corner: 120, context: context)

    // Text on the left, vertically centred on the devices.
    let textX: CGFloat = 620, textWidth: CGFloat = 1800
    let sublineSize: CGFloat = 130, gap: CGFloat = 70
    // Smaller type when a word would not fit on a line (Norwegian compounds), so none is split.
    let longestWord = headline.split(separator: " ").map { word in
        CTLineGetTypographicBounds(CTLineCreateWithAttributedString(NSAttributedString(string: String(word), attributes: [
            NSAttributedString.Key(kCTFontAttributeName as String): CTFontCreateUIFontForLanguage(.emphasizedSystem, 250, nil)!,
        ])), nil, nil, nil)
    }.max() ?? 0
    let headlineSize = min(250, 250 * textWidth * 0.97 / CGFloat(longestWord))
    let font = CTFontCreateUIFontForLanguage(.emphasizedSystem, headlineSize, nil)!
    let measured = CTFramesetterSuggestFrameSizeWithConstraints(
        CTFramesetterCreateWithAttributedString(NSAttributedString(string: headline, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font])),
        CFRange(), nil, CGSize(width: textWidth, height: .greatestFiniteMagnitude), nil).height
    let top = 1480 - (measured + gap + sublineSize * 1.2) / 2
    drawText(headline, x: textX, top: top, width: textWidth, size: headlineSize, weight: .emphasizedSystem, color: color(0xFFFFFF), context: context)
    drawText(subline, x: textX, top: top + measured + gap, width: textWidth, size: sublineSize, weight: .system, color: color(0xFFFFFF, alpha: 0.82), context: context)
    return context.makeImage()!
}

let rawDir = URL(fileURLWithPath: CommandLine.arguments[1])
let outDir = URL(fileURLWithPath: CommandLine.arguments[2])
for (locale, text) in texts.sorted(by: { $0.key < $1.key }) {
    let shots = rawDir.appendingPathComponent(locale)
    let image = render(phone: load(shots.appendingPathComponent("iphone_0_on_spot.png")),
                       tablet: load(shots.appendingPathComponent("ipad_1_board_light.png")),
                       headline: text.headline, subline: text.subline)
    let out = outDir.appendingPathComponent(locale).appendingPathComponent("header_search.png")
    save(image, to: out)
    print("Wrote \(out.path)")
}
