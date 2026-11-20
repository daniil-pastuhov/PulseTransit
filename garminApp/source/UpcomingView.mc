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
        var margin = dc.getWidth() / 20;
        var y = h * 0.12;
        dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
        y += Draw.text(dc, WatchUi.loadResource(Rez.Strings.Upcoming) as String, [Graphics.FONT_XTINY], y, 1, margin, false);
        y += h * 0.02;
        var frames = plan.framesAfter(PulseTransitApp.now());
        var rowHeight = dc.getFontHeight(Graphics.FONT_XTINY) * 2;
        for (var i = 0; i < frames.size() && i < MAX_ROWS && y + rowHeight < h * 0.9; i++) {
            var frame = frames[i] as Array;
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            var row = Draw.clock(frame[0] as Number) + "  " + (frame[2] as String);
            y += Draw.text(dc, row, [Graphics.FONT_XTINY], y, 2, margin, false) + h * 0.02;
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
