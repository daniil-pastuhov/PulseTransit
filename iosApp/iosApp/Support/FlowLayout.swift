import Shared
import SwiftUI

/// Lays its first `itemCount` subviews out in rows and wraps them when the width runs out, like
/// Compose's `FlowRow`, at most `maxLines` rows. Where rows break and how many items are left out is
/// the shared `WrappedRows`, as on Android. The subviews after the items are the markers that say
/// how many are hidden, one for each count from 1: only the one needed is placed, at the end of the
/// last row; the others, and the hidden items, are given no size, so make them `collapsible()`.
/// An item keeps its ideal size, except that one wider than a row is narrowed to it.
@available(iOS 16, *)
struct FlowLayout: Layout {
    var itemCount: Int
    var maxLines = Int.max
    var spacing: CGFloat
    var lineSpacing: CGFloat

    struct Rows {
        var rows: [[(index: Int, size: CGSize)]] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: rows.width, height: rows.height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(width: bounds.width, subviews: subviews)
        var placed = Set<Int>()
        var y = bounds.minY
        for row in rows.rows {
            let height = row.map(\.size.height).max() ?? 0
            var x = bounds.minX
            for item in row {
                subviews[item.index].place(at: CGPoint(x: x, y: y + height / 2), anchor: .leading, proposal: ProposedViewSize(item.size))
                placed.insert(item.index)
                x += item.size.width + spacing
            }
            y += height + lineSpacing
        }
        for index in subviews.indices where !placed.contains(index) {
            subviews[index].place(at: bounds.origin, proposal: .zero)
        }
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> Rows {
        let count = min(itemCount, subviews.count)
        let sizes = (0 ..< count).map { size(of: subviews[$0], width: width) }
        let markers = (count ..< subviews.count).map { size(of: subviews[$0], width: width) }
        let widths = KotlinDoubleArray(size: Int32(count))
        for (index, size) in sizes.enumerated() { widths.set(index: Int32(index), value: Double(size.width)) }
        let arrangement = WrappedRows.shared.arrange(
            widths: widths,
            maxWidth: width.isFinite ? Double(width) : .greatestFiniteMagnitude,
            spacing: Double(spacing),
            maxLines: Int32(clamping: maxLines),
            markerWidth: Double(markers.map(\.width).max() ?? 0)
        )
        var result = Rows()
        var start = 0
        let ends = arrangement.rowEnds.map { Int(truncating: $0) }
        for (row, end) in ends.enumerated() {
            var items = (start ..< end).map { (index: $0, size: sizes[$0]) }
            let hidden = Int(arrangement.hidden)
            if hidden > 0, row == ends.count - 1, hidden - 1 < markers.count {
                items.append((index: count + hidden - 1, size: markers[hidden - 1]))
            }
            result.rows.append(items)
            start = end
        }
        result.width = result.rows.map { row in
            row.reduce(0) { $0 + $1.size.width } + spacing * CGFloat(max(row.count - 1, 0))
        }.max() ?? 0
        result.height = result.rows.reduce(0) { $0 + ($1.map(\.size.height).max() ?? 0) }
            + lineSpacing * CGFloat(max(result.rows.count - 1, 0))
        return result
    }

    private func size(of subview: LayoutSubview, width: CGFloat) -> CGSize {
        let ideal = subview.sizeThatFits(.unspecified)
        return ideal.width > width ? subview.sizeThatFits(ProposedViewSize(width: width, height: nil)) : ideal
    }
}

extension View {
    /// Draws nothing when given no size, so `FlowLayout` can leave a subview out; given any other
    /// size, or none proposed, it is as the view itself.
    func collapsible() -> some View {
        frame(minWidth: 0, maxWidth: .infinity, minHeight: 0, maxHeight: .infinity, alignment: .leading).clipped()
    }
}
