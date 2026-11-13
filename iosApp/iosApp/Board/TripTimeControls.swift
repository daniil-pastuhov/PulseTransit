import Shared
import SwiftUI

/// The chosen moment of a `TripTime`, or nil for now.
func tripTimeMillis(_ time: TripTime) -> Int64? {
    switch onEnum(of: time) {
    case .now: return nil
    case let .departAt(departAt): return departAt.atMillis
    case let .arriveBy(arriveBy): return arriveBy.atMillis
    }
}

private func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/// "08:30", or "08:30 tomorrow" when it is not today. As Android's tripClockText.
func tripClockText(_ atMillis: Int64) -> String {
    let clock = TripTimeKt.clockTimeText(atMillis: atMillis)
    return TripTimeKt.calendarDaysFrom(nowMillis: nowMillis(), atMillis: atMillis) >= 1 ? L("trip_time_tomorrow", clock) : clock
}

/// "Leave now", "Leave 08:30" or "Arrive by 08:30 tomorrow". As Android's tripTimeLabel.
func tripTimeLabel(_ time: TripTime) -> String {
    switch onEnum(of: time) {
    case .now: return L("trip_time_now")
    case let .departAt(departAt): return L("trip_time_leave_at", tripClockText(departAt.atMillis))
    case let .arriveBy(arriveBy): return L("trip_time_arrive_by", tripClockText(arriveBy.atMillis))
    }
}

/// Leave now, depart at or arrive by a time of day, applied as it changes. A time earlier than
/// now means tomorrow, which the line under the picker says. As Android's TripTimeDialog.
struct TripTimeControls: View {
    let onChange: (TripTime) -> Void

    private enum Kind: Hashable { case now, depart, arrive }

    @State private var kind: Kind
    @State private var time: Date
    @State private var chosen: TripTime

    init(time initial: TripTime, onChange: @escaping (TripTime) -> Void) {
        self.onChange = onChange
        switch onEnum(of: initial) {
        case .now: _kind = State(initialValue: .now)
        case .departAt: _kind = State(initialValue: .depart)
        case .arriveBy: _kind = State(initialValue: .arrive)
        }
        // With no time yet, start a little ahead, on a round five minutes: worth choosing.
        let start = tripTimeMillis(initial).map { Date(timeIntervalSince1970: Double($0) / 1000) }
            ?? Date(timeIntervalSince1970: (Date().timeIntervalSince1970 / 300).rounded(.up) * 300 + 15 * 60)
        _time = State(initialValue: start)
        _chosen = State(initialValue: initial)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker(L("trip_time_title"), selection: $kind) {
                Text(L("trip_time_now")).tag(Kind.now)
                Text(L("trip_time_depart")).tag(Kind.depart)
                Text(L("trip_time_arrive")).tag(Kind.arrive)
            }
            .pickerStyle(.segmented)
            if kind != .now {
                HStack {
                    DatePicker(L("trip_time_title"), selection: $time, displayedComponents: .hourAndMinute)
                        .labelsHidden()
                    Spacer()
                    // Says what will be searched for, so "tomorrow" is never a surprise.
                    Text(tripTimeLabel(chosen))
                        .font(.pulse(.subheadline))
                        .foregroundColor(Palette.primary)
                }
            }
        }
        .padding(.vertical, 4)
        .onChange(of: kind) { _ in apply() }
        .onChange(of: time) { _ in apply() }
    }

    private func apply() {
        let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
        let at = TripTimeKt.nextTimeOfDay(hour: Int32(parts.hour ?? 0), minute: Int32(parts.minute ?? 0), nowMillis: nowMillis())
        switch kind {
        case .now: chosen = TripTimeNow.shared
        case .depart: chosen = TripTimeDepartAt(atMillis: at)
        case .arrive: chosen = TripTimeArriveBy(atMillis: at)
        }
        onChange(chosen)
    }
}

/// The temporary route's time beside its banner: a clock to set one, or the time it is set to
/// ("Arrive by" over "08:30"), either opening the controls to change it. As Android's TripTimeButton.
struct TripTimeButton: View {
    let time: TripTime
    let onChange: (TripTime) -> Void

    @State private var editing = false

    var body: some View {
        Button { editing = true } label: {
            if let atMillis = tripTimeMillis(time) {
                VStack(spacing: 0) {
                    Text(L(time is TripTimeArriveBy ? "trip_time_arrive" : "trip_time_depart"))
                        .font(.pulse(.caption2))
                        .opacity(0.8)
                    HStack(alignment: .firstTextBaseline, spacing: 1) {
                        Text(TripTimeKt.clockTimeText(atMillis: atMillis)).font(.pulse(.subheadline, weight: .bold))
                        let days = TripTimeKt.calendarDaysFrom(nowMillis: nowMillis(), atMillis: atMillis)
                        // The board's next-day marker, narrower than "tomorrow".
                        if days >= 1 {
                            Text("+\(days)").font(.pulse(.caption2)).baselineOffset(6)
                        }
                    }
                }
                .padding(.horizontal, 4)
            } else {
                Image(systemName: "clock")
                    .frame(width: 36, height: 36)
            }
        }
        .accessibilityLabel(L("trip_time_change"))
        .accessibilityValue(tripTimeLabel(time))
        .sheet(isPresented: $editing) {
            NavigationView {
                Form {
                    TripTimeControls(time: time, onChange: onChange)
                }
                .navigationTitle(L("trip_time_title"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button(L("trip_time_done")) { editing = false }
                    }
                }
            }
            .navigationViewStyle(.stack)
        }
    }
}

/// Says that Entur found no route to a destination, e.g. none arriving by the time asked for,
/// rather than leaving a placeholder that never fills. As Android's FastestTripNone.
struct FastestTripNone: View {
    let destinationName: String
    let time: TripTime

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "magnifyingglass")
                .opacity(0.8)
                .accessibilityHidden(true)
            Text(
                (time as? TripTimeArriveBy).map { L("no_route_to_arriving", destinationName, tripClockText($0.atMillis)) }
                    ?? L("no_route_to", destinationName)
            )
            .font(.pulse(.subheadline, weight: .bold))
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .foregroundColor(Palette.textPrimary)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Palette.primary.opacity(0.18), ignoresSafeAreaEdges: .horizontal)
    }
}
