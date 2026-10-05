// Renders the iOS app icon (1024x1024, opaque) from the same shapes as the Android adaptive icon in
// app/src/main/res/drawable/ic_launcher_{background,foreground}.xml, framed like the Play icon.
//
//   swift scripts/render-ios-icon.swift iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

let size = 1024
// The adaptive icon is drawn on a 108-unit canvas; launchers show roughly its central 80 units.
let visible: CGFloat = 80
let origin = (108 - visible) / 2

func color(_ hex: UInt32, alpha: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

let space = CGColorSpace(name: CGColorSpace.sRGB)!
let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
// Android's y axis points down; flip so the shapes can use its coordinates as written.
context.translateBy(x: 0, y: CGFloat(size))
context.scaleBy(x: CGFloat(size) / visible, y: -CGFloat(size) / visible)
context.translateBy(x: -origin, y: -origin)

// Background: diagonal gradient, then the faint diagonal accent band.
let gradient = CGGradient(colorsSpace: space, colors: [color(0x3949AB), color(0x1A237E)] as CFArray, locations: [0, 1])!
context.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 0), end: CGPoint(x: 108, y: 108), options: [])
context.setFillColor(color(0xFFFFFF, alpha: 0x14 / 255))
context.addLines(between: [CGPoint(x: 0, y: 108), CGPoint(x: 108, y: 0), CGPoint(x: 108, y: 40), CGPoint(x: 40, y: 108)])
context.fillPath()

func roundedRect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, radius: CGFloat, fill: CGColor) {
    context.setFillColor(fill)
    context.addPath(CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: radius, cornerHeight: radius, transform: nil))
    context.fillPath()
}

// Foreground: the board, then three rows of a line chip and a destination bar.
roundedRect(28, 32, 52, 44, radius: 6, fill: color(0xFFFFFF))
for (y, barEnd) in [(CGFloat(38), CGFloat(74)), (50, 68), (62, 71)] {
    roundedRect(34, y, 12, 8, radius: 2, fill: color(0xE53935))
    roundedRect(50, y + 1.5, barEnd - 50, 5, radius: 2.5, fill: color(0xB0BEC5))
}

let url = URL(fileURLWithPath: CommandLine.arguments[1])
let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, context.makeImage()!, nil)
precondition(CGImageDestinationFinalize(destination), "could not write \(url.path)")
print("Wrote \(url.path)")
