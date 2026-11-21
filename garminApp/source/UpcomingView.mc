import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! What comes after the current step: the next frames' headlines with the times they start.
class UpcomingView extends WatchUi.View {
    private const MAX_ROWS = 4;

    public function initialize() {
        View.initialize();
    }

    public function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var plan = getApp().plan();
        if (plan == null) {
            return;
        }
        var h = dc.getHeight();
        var round = Draw.isRound();
        var sub = Draw.subscreen();
        var margin = dc.getWidth() / 20;
        var lineHeight = dc.getFontHeight(Graphics.FONT_XTINY);
        var y = round ? h * 0.12 : h * 0.05;
        var bottom = round ? h * 0.9 : h * 0.97;
        dc.setColor(Draw.muted(), Graphics.COLOR_TRANSPARENT);
        var title = WatchUi.loadResource(Rez.Strings.Upcoming) as String;
        if (sub != null) {
            // The Instincts: the title beside their window, the rows on the full lines below it.
            Draw.text(dc, title, [Graphics.FONT_XTINY], sub[1] + sub[3] / 2 - lineHeight, 2, margin, false);
            y = sub[1] + sub[3] + dc.getWidth() / 22;
        } else {
            y += Draw.text(dc, title, [Graphics.FONT_XTINY], y, 1, margin, false) + h * 0.02;
        }
        var frames = plan.framesAfter(PulseTransitApp.now());
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        // Each row on up to two lines, the last on what is left.
        for (var i = 0; i < frames.size() && i < MAX_ROWS && y + lineHeight <= bottom; i++) {
            var frame = frames[i] as Array;
            var row = Draw.clock(frame[0] as Number) + "  " + (frame[2] as String);
            var lines = y + 2 * lineHeight <= bottom ? 2 : 1;
            y += Draw.text(dc, row, [Graphics.FONT_XTINY], y, lines, margin, false) + h * 0.02;
        }
    }
}

class UpcomingDelegate extends WatchUi.BehaviorDelegate {
    public function initialize() {
        BehaviorDelegate.initialize();
    }

    public function onPreviousPage() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }

    public function onBack() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }
}
