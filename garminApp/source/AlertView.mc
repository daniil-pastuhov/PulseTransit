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
        var h = dc.getHeight();
        var margin = dc.getWidth() / 30 + dc.getWidth() / 25;
        dc.setColor(Draw.RIDE, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(dc.getWidth() / 30);
        dc.drawCircle(dc.getWidth() / 2, h / 2, dc.getWidth() / 2 - dc.getWidth() / 60 - 1);
        dc.setPenWidth(1);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var y = h * 0.2;
        y += Draw.text(dc, _title, [Graphics.FONT_MEDIUM, Graphics.FONT_SMALL, Graphics.FONT_TINY], y, 3, margin, false);
        dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
        y += h * 0.03;
        var room = ((h * 0.86 - y) / dc.getFontHeight(Graphics.FONT_XTINY)).toNumber();
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
