import Shared
import SwiftUI

/// A drawn, made-up piece of the board for an intro page, the counterpart of Android's
/// IntroIllustration: the element the page talks about is outlined and the rest is dimmed, so a
/// new user, whose board is still empty, sees what to look for. Decorative only; the page's text
/// says the same to VoiceOver.
struct IntroIllustration: View {
    let page: IntroPage

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            content
        }
        .padding(12)
        .frame(maxWidth: 340)
        .background(RoundedRectangle(cornerRadius: 20).fill(Palette.background))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(Palette.textSecondary.opacity(0.3), lineWidth: 1))
        .foregroundColor(Palette.textPrimary)
        .accessibilityHidden(true)
    }

    @ViewBuilder private var content: some View {
        switch page {
        case .nearestStop:
            MockHeader(spot: .dim)
            MockStopCard(name: "Stortorvet", subtitle: "46 m · Oslo", nameSpot: .focus, rowsSpot: .dim, pickable: true, tapHint: true)
        case .routesFromHere:
            MockHeader(spot: .dim, focus: .search)
            MockRouteCard().spot(.focus)
            MockStopCard(name: "Stortorvet", subtitle: "46 m · Oslo", nameSpot: .dim, rowsSpot: .dim, rows: 1)
        case .resize:
            MockRouteCard().spot(.dim)
            MockResizeHandle()
            MockStopCard(name: "Stortorvet", subtitle: "46 m · Oslo", nameSpot: .dim, rowsSpot: .dim, rows: 1)
        case .chooseStops:
            MockHeader(spot: .dim, focus: .settings)
            MockStopCard(name: "Jernbanetorget", subtitle: "Oslo", nameSpot: .focus, rowsSpot: .plain, rows: 1)
            MockStopCard(name: "Oslo S", subtitle: "Oslo", nameSpot: .focus, rowsSpot: .plain, rows: 1, train: true)
            Text(L("board_page_stops", 1, 2, 5))
                .font(.pulse(.caption2))
                .foregroundColor(Palette.textSecondary)
                .frame(maxWidth: .infinity)
        case .fastestRoutes:
            MockHeader(spot: .dim)
            MockFastestBanner()
            MockStopCard(name: "Jernbanetorget", subtitle: "Oslo", nameSpot: .dim, rowsSpot: .dim, rows: 1)
        case .tapForDetails:
            MockStopCard(name: "Jernbanetorget", subtitle: "Oslo", nameSpot: .dim, rowsSpot: .dim, focusRow: 0)
            MockDetailsSheet()
        case .screenCare:
            MockScreenCareSettings()
        }
    }
}

/// How an element of a mock is drawn: dimmed, as is, or outlined as the thing the page is about.
private enum Spot { case dim, plain, focus }

private enum HeaderButton { case search, settings }

private extension View {
    @ViewBuilder func spot(_ spot: Spot) -> some View {
        switch spot {
        case .dim: opacity(0.3)
        case .plain: self
        case .focus: overlay(RoundedRectangle(cornerRadius: 10).stroke(Palette.primary, lineWidth: 2))
        }
    }

    func mockCard() -> some View {
        frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 12).fill(Palette.card))
    }
}

private struct TapHint: View {
    var body: some View {
        Image(systemName: "hand.point.up.left.fill")
            .font(.system(size: 18))
            .foregroundColor(Palette.primary)
    }
}

private struct MockHeader: View {
    let spot: Spot
    var focus: HeaderButton?

    var body: some View {
        HStack(spacing: 4) {
            Text("08:12").font(.pulse(.title2, weight: .bold)).foregroundColor(Palette.primary).spot(spot)
            Spacer()
            icon("magnifyingglass", focus == .search ? .focus : spot)
            icon("arrow.clockwise", spot)
            icon("gearshape.fill", focus == .settings ? .focus : spot)
        }
    }

    private func icon(_ symbol: String, _ spot: Spot) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 16))
            .frame(width: 34, height: 34)
            .spot(spot)
            .overlay(alignment: .bottomTrailing) {
                if spot == .focus { TapHint().offset(x: 10, y: 14) }
            }
    }
}

private struct MockStopCard: View {
    let name: String
    let subtitle: String
    let nameSpot: Spot
    let rowsSpot: Spot
    var rows = 2
    var pickable = false
    var tapHint = false
    var focusRow: Int?
    var train = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 2) {
                    Text(name).font(.pulse(.headline))
                    if pickable { Image(systemName: "chevron.down").font(.pulse(.caption, weight: .bold)) }
                }
                Text(subtitle).font(.pulse(.caption2)).foregroundColor(Palette.textSecondary)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .spot(nameSpot)
            .overlay(alignment: .bottomTrailing) {
                if tapHint { TapHint().offset(x: 18, y: 10) }
            }
            ForEach(Array(departures.prefix(rows).enumerated()), id: \.offset) { index, departure in
                MockDepartureRow(line: departure.0, mode: departure.1, destination: departure.2, time: "\(3 + index * 4)m")
                    .spot(rowSpot(index))
                    .overlay(alignment: .bottomTrailing) {
                        if focusRow == index { TapHint().offset(x: -24, y: 14) }
                    }
            }
        }
        .mockCard()
    }

    private var departures: [(String, String, String)] {
        train ? [("R13", "rail", "Dal"), ("RE20", "rail", "Halden")] : [("19", "tram", "Majorstuen"), ("17", "tram", "Sinsen")]
    }

    private func rowSpot(_ index: Int) -> Spot {
        if focusRow == index { return .focus }
        return focusRow != nil ? .dim : rowsSpot
    }
}

private struct MockDepartureRow: View {
    let line: String
    let mode: String
    let destination: String
    let time: String

    var body: some View {
        HStack(spacing: 8) {
            LinePill(line: line, mode: mode, minWidth: 40)
            TransportIcon(mode: mode).font(.pulse(.subheadline))
            Text(destination).font(.pulse(.subheadline)).lineLimit(1)
            Spacer()
            Text(time).font(.pulse(.headline))
        }
        .padding(6)
        .background(RoundedRectangle(cornerRadius: 8).fill(Palette.textSecondary.opacity(0.12)))
    }
}

private struct MockRouteCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L("route_to", "Oslo S")).font(.pulse(.subheadline, weight: .bold))
            HStack(spacing: 10) {
                Text("08:14").font(.pulse(.headline))
                VStack(alignment: .leading, spacing: 0) {
                    (Text(Image(systemName: "figure.walk")) + Text(" \(L("leg_walk", 2))  →  ")
                        + Text(Image(systemName: Palette.transportSymbol("tram"))) + Text(" 19"))
                        .font(.pulse(.caption))
                    Text(L("route_arrival", "08:29")).font(.pulse(.caption2)).foregroundColor(Palette.textSecondary)
                }
                Spacer()
                Text(L("duration_min", 15)).font(.pulse(.subheadline, weight: .bold))
            }
        }
        .mockCard()
    }
}

private struct MockResizeHandle: View {
    var body: some View {
        ZStack {
            Capsule().fill(Palette.textSecondary).frame(width: 40, height: 4)
            HStack {
                Spacer()
                Image(systemName: "chevron.up.chevron.down")
                    .font(.pulse(.caption, weight: .bold))
                    .foregroundColor(Palette.primary)
                    .padding(.trailing, 12)
            }
        }
        .frame(height: 36)
        .frame(maxWidth: .infinity)
        .spot(.focus)
    }
}

private struct MockFastestBanner: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("fastest_route_to", "Lillestrøm")).font(.pulse(.subheadline, weight: .bold))
                HStack(spacing: 10) {
                    Text("08:23").font(.pulse(.headline))
                    Text(L("fastest_route_info", "RE10", "Oslo S")).font(.pulse(.caption))
                }
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 8).fill(Palette.primary))
            Text(L("schedule_summary", L("schedule_weekdays"), "07:00", "09:00"))
                .font(.pulse(.caption, weight: .medium))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.card))
        }
        .padding(4)
        .spot(.focus)
    }
}

private struct MockDetailsSheet: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Capsule().fill(Palette.textSecondary).frame(width: 32, height: 4).frame(maxWidth: .infinity)
            Text(L("details_departures")).font(.pulse(.subheadline, weight: .bold))
            Text("08:15 · 08:25 · 08:35").font(.pulse(.caption)).foregroundColor(Palette.textSecondary)
            Text(L("filter_out_line", "19")).font(.pulse(.subheadline)).foregroundColor(Palette.primary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 16).fill(Palette.card))
    }
}

private struct MockScreenCareSettings: View {
    var body: some View {
        VStack(spacing: 6) {
            row(L("pixel_shift_level"), L("burn_in_medium"))
            row(L("auto_dim_delay"), L("dim_30s"))
            row(L("wake_camera"), nil)
            row(L("day_night_mode"), L("theme_auto"))
        }
        .mockCard()
    }

    private func row(_ label: String, _ value: String?) -> some View {
        HStack(spacing: 8) {
            Text(label).font(.pulse(.caption)).frame(maxWidth: .infinity, alignment: .leading)
            if let value {
                Text(value).font(.pulse(.caption, weight: .bold)).foregroundColor(Palette.primary)
            } else {
                Capsule().fill(Palette.primary).frame(width: 36, height: 22)
                    .overlay(Circle().fill(.white).padding(3), alignment: .trailing)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .spot(.focus)
    }
}
