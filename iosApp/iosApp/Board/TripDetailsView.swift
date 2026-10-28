import Shared
import SwiftUI

/// A route whose details are open, captured at the tap, with its destination's id. The details
/// follow refreshes through `currentTrip`, and keep this trip once a refresh no longer has it.
struct TripSelection: Identifiable {
    let destinationId: String
    let trip: TripInfo
    var id: String { destinationId }
}

/// A route's every leg, opened by tapping a fastest route: the board fits it on one line. Each ride
/// has its line, which way it heads, the platform, live times and delays; walks have their time and
/// distance, and waits between rides are shown. The stops each ride passes are fetched with
/// `loadLegStops` while the sheet is open, and listed on request. Under the header is the journey
/// alerts button, and the rationale it opens. As on Android.
struct TripDetailsView: View {
    let trip: TripInfo
    let showDelays: Bool
    let showMessages: Bool
    let loadLegStops: (TripLeg) async -> [JourneyStop]
    @ObservedObject var journey: JourneyAlertsModel

    @Environment(\.dismiss) private var dismiss
    /// By leg index; a leg missing from it is still loading.
    @State private var legStops: [Int: [JourneyStop]] = [:]
    /// Bumped by every tap inside, so only an untouched sheet closes itself.
    @State private var interactions = 0

    /// How long the details stay open untouched, as for a departure's.
    private static let autoCloseNanoseconds: UInt64 = 120 * 1_000_000_000

    var body: some View {
        NavigationView {
            TimelineView(.periodic(from: .now, by: 1)) { timeline in
                let nowMillis = Int64(timeline.date.timeIntervalSince1970 * 1000)
                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        TripHeader(trip: trip, nowMillis: nowMillis)
                            .padding(.bottom, 8)
                        JourneyNotifyButton(model: journey, trip: trip)
                            .padding(.bottom, 8)
                        ForEach(shownLegs, id: \.self) { index in
                            leg(at: index, nowMillis: nowMillis)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .foregroundColor(Palette.textPrimary)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("back")) { dismiss() }
                        .accessibilityIdentifier("tripDetails.close")
                }
            }
        }
        .navigationViewStyle(.stack)
        .sheet(isPresented: rationaleShown) {
            JourneyRationaleView(
                onChoice: { journey.choose($0) },
                onDismiss: { journey.viewModel.onRationaleDismiss() }
            )
        }
        .onAppear { journey.refreshPermissions() }
        // Keyed by the journeys ridden, so a refresh of the same route keeps its stops.
        .task(id: TripDetailsKt.tripKey(trip: trip)) {
            legStops = [:]
            await withTaskGroup(of: (Int, [JourneyStop]).self) { group in
                for (index, leg) in trip.legs.enumerated() where leg.serviceJourneyId != nil {
                    group.addTask { (index, await loadLegStops(leg)) }
                }
                for await (index, stops) in group where !Task.isCancelled {
                    legStops[index] = stops
                }
            }
        }
        // The rationale counts as using the details, so they do not close under it.
        .task(id: interactions + (journey.state.rationaleFor == nil ? 0 : 1_000_000)) {
            try? await Task.sleep(nanoseconds: Self.autoCloseNanoseconds)
            if !Task.isCancelled { dismiss() }
        }
    }

    private var rationaleShown: Binding<Bool> {
        Binding(
            get: { journey.state.rationaleFor != nil },
            set: { shown in if !shown { journey.viewModel.onRationaleDismiss() } }
        )
    }

    private var shownLegs: [Int] {
        trip.legs.indices.filter { trip.legs[$0].isShown }
    }

    @ViewBuilder
    private func leg(at index: Int, nowMillis: Int64) -> some View {
        let leg = trip.legs[index]
        let endName = TripDetailsKt.legEndName(trip: trip, index: Int32(index))
        VStack(alignment: .leading, spacing: 8) {
            if let wait = TripDetailsKt.waitMinutesBefore(trip: trip, index: Int32(index)) {
                Text("⏱  \(L("trip_details_wait", wait.intValue))")
                    .font(.subheadline)
                    .foregroundColor(Palette.textSecondary)
                    .padding(.leading, 12)
            }
            if leg.isTransit {
                RideCard(
                    leg: leg,
                    endName: endName,
                    stops: legStops[index],
                    nowMillis: nowMillis,
                    showDelays: showDelays,
                    showMessages: showMessages,
                    onInteract: { interactions += 1 }
                )
            } else {
                WalkRow(leg: leg, endName: endName)
            }
        }
    }
}

/// Where to, when it leaves and arrives, how long it takes, how often it changes and how far it walks.
private struct TripHeader: View {
    let trip: TripInfo
    let nowMillis: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L("trip_details_title", trip.destinationName))
                .font(.title3.bold())
                .lineLimit(1)
            Text(times)
                .font(.headline)
                .monospacedDigit()
            Text(facts)
                .font(.subheadline)
                .foregroundColor(Palette.textSecondary)
        }
    }

    private var times: String {
        let start = clockText(trip.expectedStartTime, nowMillis: nowMillis)
        let span = trip.expectedEndTime.map { "\(start) → \(clockText($0, nowMillis: nowMillis))" } ?? start
        return "\(span) · \(L("duration_min", Int(trip.durationMinutes)))"
    }

    private var facts: String {
        let changes = Int(TripDetailsKt.transferCount(trip: trip))
        let walked = Int(TripDetailsKt.walkDistanceMeters(trip: trip))
        let countdown: String?
        if case .now = onEnum(of: DepartureCountdownKt.departureCountdown(expectedTime: trip.expectedStartTime, nowMillis: nowMillis)) {
            countdown = L("now")
        } else {
            countdown = DepartureDetailsKt.minutesUntil(time: trip.expectedStartTime, nowMillis: nowMillis).map { inTimeText($0.int64Value) }
        }
        return [
            changes == 0 ? L("trip_details_direct") : L("trip_details_changes", changes),
            walked > 0 ? L("trip_details_walk_total", walked) : nil,
            countdown,
        ]
        .compactMap { $0 }
        .joined(separator: " · ")
    }
}

private struct WalkRow: View {
    let leg: TripLeg
    let endName: String?

    var body: some View {
        HStack(spacing: 12) {
            Text(verbatim: "🚶").font(.headline)
            VStack(alignment: .leading, spacing: 2) {
                Text(
                    [L("trip_details_walk", Int(leg.durationMinutes)), leg.distanceMeters > 0 ? "\(leg.distanceMeters) m" : nil]
                        .compactMap { $0 }
                        .joined(separator: " · ")
                )
                if let endName {
                    Text(L("trip_details_to_place", endName))
                        .font(.subheadline)
                        .foregroundColor(Palette.textSecondary)
                        .lineLimit(1)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}

/// One ride: line badge and heading, who runs it, then where it boards and gets off. Once its stops
/// have loaded, a toggle swaps the two ends for every stop it passes.
private struct RideCard: View {
    let leg: TripLeg
    let endName: String?
    let stops: [JourneyStop]?
    let nowMillis: Int64
    let showDelays: Bool
    let showMessages: Bool
    let onInteract: () -> Void
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text(leg.lineName ?? "")
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Palette.transport(leg.mode)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Palette.transportEmoji(leg.mode))  \(leg.destination ?? leg.lineFullName ?? leg.lineName ?? "")")
                        .font(.headline)
                        .lineLimit(1)
                    let line = [leg.lineFullName, leg.authority].compactMap { $0 }.uniqued().joined(separator: " · ")
                    if !line.isEmpty {
                        Text(line).font(.caption).foregroundColor(Palette.textSecondary).lineLimit(1)
                    }
                }
            }
            facts
            Spacer().frame(height: 8)
            if expanded, let stops, !stops.isEmpty {
                ForEach(Array(stops.enumerated()), id: \.offset) { index, stop in
                    RouteStopRow(stop: stop, nowMillis: nowMillis, isFirst: index == 0, isLast: index == stops.count - 1)
                }
            } else {
                let isDelayed = showDelays && TripDetailsKt.legDelayMinutes(leg: leg) > 0
                EndpointRow(time: leg.expectedStartTime, name: leg.fromName, nowMillis: nowMillis, delayFrom: isDelayed ? leg.aimedStartTime : nil)
                EndpointRow(time: leg.expectedEndTime, name: endName, nowMillis: nowMillis, delayFrom: nil)
            }
            stopsToggle
            if showMessages {
                ForEach(leg.notices, id: \.self) { notice in
                    Text("⚠️ \(notice)").font(.footnote).foregroundColor(Palette.error)
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 12).fill(Palette.primary.opacity(0.08)))
    }

    /// "● Live · Platform 2", or whichever of the two the leg has.
    @ViewBuilder
    private var facts: some View {
        let platform = leg.platform.map { L("details_platform", $0) }
        if platform != nil || leg.isRealtime {
            let live = leg.isRealtime ? Text("● \(L("details_live"))").bold().foregroundColor(Palette.primary) : Text(verbatim: "")
            let separator = leg.isRealtime && platform != nil ? Text(verbatim: " · ") : Text(verbatim: "")
            (live + separator + Text(platform ?? ""))
                .font(.caption)
                .foregroundColor(Palette.textSecondary)
                .padding(.top, 4)
        }
    }

    /// "6 min · 5 stops", which shows or hides them once loaded; a spinner while they load.
    private var stopsToggle: some View {
        let ridden = stops.map { $0.count - 1 }.flatMap { $0 > 0 ? $0 : nil }
        let canExpand = (stops?.count ?? 0) > 1
        return Button {
            withAnimation { expanded.toggle() }
            onInteract()
        } label: {
            HStack(spacing: 4) {
                Text(
                    [L("duration_min", Int(leg.durationMinutes)), ridden.map { L("trip_details_stops", $0) }]
                        .compactMap { $0 }
                        .joined(separator: " · ")
                )
                if stops == nil && leg.serviceJourneyId != nil {
                    ProgressView().scaleEffect(0.6)
                } else if canExpand {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .accessibilityLabel(L(expanded ? "trip_details_hide_stops" : "trip_details_show_stops"))
                }
            }
            .font(.subheadline)
            .foregroundColor(Palette.textSecondary)
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .disabled(!canExpand)
    }
}

/// A time and a stop name; with `delayFrom`, the timetabled time struck through.
private struct EndpointRow: View {
    let time: String?
    let name: String?
    let nowMillis: Int64
    let delayFrom: String?

    var body: some View {
        HStack(spacing: 8) {
            Text(time.map { clockText($0, nowMillis: nowMillis) } ?? "--:--")
                .monospacedDigit()
                .frame(width: 52, alignment: .leading)
            Text(name ?? "").lineLimit(1)
            if let delayFrom {
                Text(clockText(delayFrom, nowMillis: nowMillis))
                    .font(.caption)
                    .monospacedDigit()
                    .strikethrough()
                    .foregroundColor(Palette.textSecondary)
            }
        }
        .font(.body.bold())
        .frame(height: 32)
    }
}
