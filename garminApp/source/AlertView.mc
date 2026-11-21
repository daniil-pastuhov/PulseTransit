import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Timer;
import Toybox.WatchUi;

//! One alert over the whole screen ("Change to 20", "Leaves at 09:18 · Platform B"), as the phone
//! words its notification. Any key closes it, and so does a while without one.
class AlertView extends WatchUi.View {
    private const CLOSE_AFTER_MS = 20000;

    private var _title as String;
    private var _text as String;
    private var _timer as Timer.Timer?;

    public function initialize(title as String, text as String) {
        View.initialize();
        _title = title;
        _text = text;
    }

    public function onShow() as Void {
        var timer = new Timer.Timer();
        timer.start(method(:onTimeout), CLOSE_AFTER_MS, false);
        _timer = timer;
    }

    public function onHide() as Void {
        if (_timer != null) {
            _timer.stop();
            _timer = null;
        }
    }

    public function onTimeout() as Void {
        getApp().closeAlert();
    }

    public function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var w = dc.getWidth();
        var h = dc.getHeight();
        var pen = w / 30;
        var margin = pen + w / 25;
        var sub = Draw.subscreen();
        var y = h * 0.2;
        var bottom = h * 0.86;
        // A blue frame says "alert": a ring on a round screen, the screen's outline on a square one.
        dc.setColor(Draw.RIDE, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(pen);
        if (Draw.isRound()) {
            dc.drawCircle(w / 2, h / 2, w / 2 - pen / 2 - 1);
        } else if (sub == null) {
            dc.drawRoundedRectangle(pen / 2 + 1, pen / 2 + 1, w - pen - 2, h - pen - 2, w / 7);
            y = h * 0.12;
            bottom = h * 0.9;
        }
        dc.setPenWidth(1);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        if (sub != null) {
            // The Instincts have no room for a frame: their window is filled instead, and the text
            // starts beside it, where its lines are shorter.
            dc.fillCircle(sub[0] + sub[2] / 2, sub[1] + sub[3] / 2, sub[2] / 2 - 2);
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
            dc.drawText(sub[0] + sub[2] / 2, sub[1] + sub[3] / 2, Graphics.FONT_LARGE, "!", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            margin = w / 30;
            y = h * 0.1;
            bottom = h * 0.97;
        }
        y += Draw.text(dc, _title, [Graphics.FONT_MEDIUM, Graphics.FONT_SMALL, Graphics.FONT_TINY], y, 3, margin, false);
        dc.setColor(Draw.muted(), Graphics.COLOR_TRANSPARENT);
        y += h * 0.03;
        var room = ((bottom - y) / dc.getFontHeight(Graphics.FONT_XTINY)).toNumber();
        Draw.text(dc, _text, [Graphics.FONT_XTINY], y, room, margin, false);
    }
}

class AlertDelegate extends WatchUi.BehaviorDelegate {
    public function initialize() {
        BehaviorDelegate.initialize();
    }

    public function onSelect() as Boolean {
        getApp().closeAlert();
        return true;
    }

    public function onBack() as Boolean {
        getApp().closeAlert();
        return true;
    }
}
