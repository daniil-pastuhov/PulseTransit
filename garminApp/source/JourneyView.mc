import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

//! The followed journey now: what the phone's progress notification says. The title, the minutes
//! to the step's next moment, the headline ("5 towards Vestli") and its facts, inside the ring.
class JourneyView extends WatchUi.View {
    public function initialize() {
        View.initialize();
    }

    public function onUpdate(dc as Dc) as Void {
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var app = getApp();
        var plan = app.plan();
        var h = dc.getHeight();
        // Clear of the ring and no more: a round screen has little width to give away.
        var margin = dc.getWidth() / 30 + dc.getWidth() / 50;
        if (app.isOutdated()) {
            Draw.text(dc, WatchUi.loadResource(Rez.Strings.UpdateNeeded) as String, [Graphics.FONT_SMALL, Graphics.FONT_TINY], h * 0.36, 3, margin, false);
            return;
        }
        var now = PulseTransitApp.now();
        var frame = plan == null ? null : plan.frameAt(now);
        if (plan == null || frame == null) {
            var used = Draw.text(dc, WatchUi.loadResource(Rez.Strings.NoJourney) as String, [Graphics.FONT_SMALL, Graphics.FONT_TINY], h * 0.26, 2, margin, false);
            dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
            Draw.text(dc, WatchUi.loadResource(Rez.Strings.NoJourneyHint) as String, [Graphics.FONT_XTINY], h * 0.26 + used + h * 0.03, 4, margin, false);
            return;
        }
        Draw.ring(dc, plan.segments(), now);

        var y = h * 0.12;
        dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
        y += Draw.text(dc, plan.title(), [Graphics.FONT_XTINY], y, 1, margin, false);

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        y += drawCountdown(dc, frame, now, y);

        // The headline on one line as large as it goes, else on two lines smaller, so the facts keep their room.
        var headline = frame[2] as String;
        var oneLine = Draw.text(dc, headline, [Graphics.FONT_SMALL], y, 1, margin, true);
        if (oneLine == 0) {
            oneLine = Draw.text(dc, headline, [Graphics.FONT_TINY, Graphics.FONT_XTINY], y, 2, margin, false);
        }
        y += oneLine;
        dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
        var room = ((h * 0.9 - y) / dc.getFontHeight(Graphics.FONT_XTINY)).toNumber();
        Draw.text(dc, frame[3] as String, [Graphics.FONT_XTINY], y, room, margin, false);
    }

    //! The whole minutes left to the frame's next moment, rounded up as the phone does, so "1 min"
    //! lasts until the moment itself; "Now" once it has come. Nothing for a frame with no moment.
    //! Returns the height used.
    private function drawCountdown(dc as Dc, frame as Array, now as Number, y as Numeric) as Number {
        var nextAt = frame[4] as Number;
        if (nextAt == 0 || (frame[1] as Number) == PlanFormat.PHASE_ARRIVED) {
            return dc.getHeight() / 10;
        }
        var cx = dc.getWidth() / 2;
        var left = nextAt - now;
        if (left <= 0) {
            dc.drawText(cx, y, Graphics.FONT_LARGE, WatchUi.loadResource(Rez.Strings.Now) as String, Graphics.TEXT_JUSTIFY_CENTER);
            return dc.getFontHeight(Graphics.FONT_LARGE);
        }
        var minutes = (left + 59) / 60;
        var number = minutes < 100 ? minutes.format("%d") : (minutes / 60).format("%d") + ":" + (minutes % 60).format("%02d");
        var font = Graphics.FONT_NUMBER_MEDIUM;
        var unit = minutes < 100 ? WatchUi.loadResource(Rez.Strings.Minutes) as String : "";
        var numberWidth = dc.getTextWidthInPixels(number, font);
        var unitWidth = dc.getTextWidthInPixels(unit, Graphics.FONT_XTINY);
        var space = unit.length() == 0 ? 0 : dc.getWidth() / 60;
        var x = cx - (numberWidth + space + unitWidth) / 2;
        // Number fonts carry space above their digits; the title sits close above instead.
        y -= dc.getFontHeight(font) / 8;
        dc.drawText(x, y, font, number, Graphics.TEXT_JUSTIFY_LEFT);
        // The unit sits on the number's baseline.
        var baseline = y + dc.getFontHeight(font) - Graphics.getFontDescent(font);
        var unitY = baseline - dc.getFontHeight(Graphics.FONT_XTINY) + Graphics.getFontDescent(Graphics.FONT_XTINY);
        dc.setColor(Draw.MUTED_TEXT, Graphics.COLOR_TRANSPARENT);
        dc.drawText(x + numberWidth + space, unitY, Graphics.FONT_XTINY, unit, Graphics.TEXT_JUSTIFY_LEFT);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        return dc.getFontHeight(font) * 7 / 10;
    }
}

//! The journey's keys: the menu (or select) offers "Stop following", down shows what comes next.
class JourneyDelegate extends WatchUi.BehaviorDelegate {
    public function initialize() {
        BehaviorDelegate.initialize();
    }

    public function onMenu() as Boolean {
        if (getApp().plan() == null) {
            return false;
        }
        var menu = new WatchUi.Menu2({ :title => WatchUi.loadResource(Rez.Strings.AppName) as String });
        menu.addItem(new WatchUi.MenuItem(WatchUi.loadResource(Rez.Strings.StopFollowing) as String, null, :stop, null));
        WatchUi.pushView(menu, new JourneyMenuDelegate(), WatchUi.SLIDE_UP);
        return true;
    }

    public function onSelect() as Boolean {
        return onMenu();
    }

    public function onNextPage() as Boolean {
        var plan = getApp().plan();
        if (plan == null || plan.framesAfter(PulseTransitApp.now()).size() == 0) {
            return false;
        }
        WatchUi.pushView(new UpcomingView(), new UpcomingDelegate(), WatchUi.SLIDE_UP);
        return true;
    }
}

class JourneyMenuDelegate extends WatchUi.Menu2InputDelegate {
    public function initialize() {
        Menu2InputDelegate.initialize();
    }

    public function onSelect(item as WatchUi.MenuItem) as Void {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        if (item.getId() == :stop) {
            getApp().stopFollowing();
        }
    }
}
