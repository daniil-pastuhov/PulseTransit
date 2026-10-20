import Shared
import SwiftUI

/// Edits when a saved destination's route is shown, as Android's DestinationScheduleEditor does.
/// Saving with "Always" on passes nil. Presented as a sheet: full height on iPhone (a medium
/// detent from iOS 16) and a form sheet on iPad, so it suits both without a separate layout.
struct DestinationScheduleView: View {
    let destination: StopPlace
    let onSave: (DestinationSchedule?) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var always: Bool
    @State private var days: Set<Int32>
    @State private var startMinute: Int32
    @State private var endMinute: Int32

    init(destination: StopPlace, schedule: DestinationSchedule?, onSave: @escaping (DestinationSchedule?) -> Void) {
        self.destination = destination
        self.onSave = onSave
        _always = State(initialValue: schedule == nil)
        _days = State(initialValue: schedule.map { isoDays($0.isoDays) } ?? isoDays(DestinationSchedule.companion.WEEKDAYS))
        _startMinute = State(initialValue: schedule?.startMinute ?? 7 * 60)
        _endMinute = State(initialValue: schedule?.endMinute ?? 9 * 60)
    }

    private var edited: DestinationSchedule {
        DestinationSchedule(isoDays: Set(days.map { KotlinInt(value: $0) }), startMinute: startMinute, endMinute: endMinute)
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    Toggle(L("schedule_always"), isOn: $always)
                } header: {
                    Text(destination.name)
                }
                if !always {
                    Section {
                        HStack {
                            presetButton("schedule_every_day", DestinationSchedule.companion.ALL_DAYS)
                            presetButton("schedule_weekdays", DestinationSchedule.companion.WEEKDAYS)
                            presetButton("schedule_weekend", DestinationSchedule.companion.WEEKEND)
                        }
                        // Adaptive columns: one row of seven on a wide screen, two rows on a small phone.
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 44), spacing: 6)], spacing: 6) {
                            ForEach(Array(1...7), id: \.self) { (day: Int) in
                                dayToggle(Int32(day))
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    Section {
                        DatePicker(L("schedule_from"), selection: timeBinding($startMinute, isEnd: false), displayedComponents: .hourAndMinute)
                        DatePicker(L("schedule_to"), selection: timeBinding($endMinute, isEnd: true), displayedComponents: .hourAndMinute)
                    } footer: {
                        VStack(alignment: .leading, spacing: 4) {
                            if days.isEmpty {
                                Text(L("schedule_no_days")).foregroundColor(.red)
                            } else {
                                Text(scheduleSummary(edited)).foregroundColor(.accentColor)
                            }
                            Text(L("schedule_hint"))
                        }
                    }
                }
            }
            .navigationTitle(L("schedule_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L("cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(L("save")) {
                        onSave(always ? nil : edited)
                        dismiss()
                    }
                    .disabled(!always && days.isEmpty)
                }
            }
        }
        .navigationViewStyle(.stack)
        .mediumDetent()
    }

    private func presetButton(_ key: String, _ preset: Set<KotlinInt>) -> some View {
        Button(L(key)) { days = isoDays(preset) }
            .buttonStyle(.bordered)
            .font(.footnote)
            .frame(maxWidth: .infinity)
    }

    private func dayToggle(_ day: Int32) -> some View {
        let selected = days.contains(day)
        return Button {
            if selected { days.remove(day) } else { days.insert(day) }
        } label: {
            Text(shortDayName(day))
                .font(.subheadline.weight(selected ? .semibold : .regular))
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(RoundedRectangle(cornerRadius: 8).fill(selected ? Color.accentColor : Color.secondary.opacity(0.15)))
                .foregroundColor(selected ? .white : .primary)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// A time of day for the picker. Midnight as an end means "until the end of the day", which the
    /// schedule stores as 24:00.
    private func timeBinding(_ minute: Binding<Int32>, isEnd: Bool) -> Binding<Date> {
        Binding(
            get: { dateAt(minute: minute.wrappedValue) },
            set: { date in
                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                let picked = Int32((parts.hour ?? 0) * 60 + (parts.minute ?? 0))
                minute.wrappedValue = isEnd && picked == 0 ? DestinationSchedule.companion.MINUTES_PER_DAY : picked
            }
        )
    }
}

private extension View {
    @ViewBuilder
    func mediumDetent() -> some View {
        if #available(iOS 16, *) {
            presentationDetents([.medium, .large])
        } else {
            self
        }
    }
}

private func isoDays(_ days: Set<KotlinInt>) -> Set<Int32> {
    Set(days.map { $0.int32Value })
}

/// The ISO day's short name in the user's language; ISO counts from Monday, Calendar from Sunday.
private func shortDayName(_ isoDay: Int32) -> String {
    Calendar.current.shortWeekdaySymbols[Int(isoDay) % 7]
}

private func dateAt(minute: Int32) -> Date {
    let wrapped = Int(minute) % Int(DestinationSchedule.companion.MINUTES_PER_DAY)
    return Calendar.current.date(bySettingHour: wrapped / 60, minute: wrapped % 60, second: 0, of: Date()) ?? Date()
}

private func timeText(_ minute: Int32) -> String {
    let formatter = DateFormatter()
    formatter.dateStyle = .none
    formatter.timeStyle = .short
    return formatter.string(from: dateAt(minute: minute))
}

/// The schedule as one line, e.g. "Mon–Fri · 08:00–09:00", or "Always" when there is none.
func scheduleSummary(_ schedule: DestinationSchedule?) -> String {
    guard let schedule else { return L("schedule_always") }
    let days = schedule.isoDays == DestinationSchedule.companion.ALL_DAYS
        ? L("schedule_every_day")
        : schedule.daysText { shortDayName($0.int32Value) }
    let start = timeText(schedule.startMinute)
    let end = timeText(schedule.endMinute)
    if schedule.startMinute == 0 && schedule.endMinute == DestinationSchedule.companion.MINUTES_PER_DAY {
        return L("schedule_summary_all_day", days)
    }
    return L(schedule.endsNextDay ? "schedule_summary_next_day" : "schedule_summary", days, start, end)
}
