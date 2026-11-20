import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

//! PulseTransit on the watch: shows the journey followed on the phone. The phone sends the whole
//! journey as a plan and this app walks through it by its own clock, so it keeps going when the
//! phone is out of reach. While it is open the journey's alerts come from here and not the phone,
//! which is why it tells the phone when it starts, that it is still open, and when it closes.
class PulseTransitApp extends Application.AppBase {
    //! How often the phone hears "alive": its WatchMessages.ALIVE_EVERY_SECONDS.
    private const ALIVE_EVERY_SECONDS = 60;

    //! A stored journey this long past its arrival is forgotten.
    private const LINGER_SECONDS = 600;

    private const KEY_PLAN = "plan";
    private const KEY_SHOWN = "shown";

    private var _plan as JourneyPlan?;
    //! The ids of the alerts already shown, as keys.
    private var _shown as Dictionary = {};
    //! The phone app's messages are newer than this app reads.
    private var _outdated as Boolean = false;
    private var _timer as Timer.Timer?;
    private var _ticks as Number = 0;
    private var _alertOpen as Boolean = false;

    public function initialize() {
        AppBase.initialize();
    }

    public function onStart(state as Dictionary?) as Void {
        restore();
        var demo = demoPlan(now());
        if (demo != null) {
            _plan = new JourneyPlan(demo);
            _shown = {};
        }
        Communications.registerForPhoneAppMessages(method(:onPhone));
        say("start");
        var timer = new Timer.Timer();
        timer.start(method(:onTick), 1000, true);
        _timer = timer;
    }

    public function onStop(state as Dictionary?) as Void {
        if (_timer != null) {
            _timer.stop();
        }
        say("bye");
    }

    public function getInitialView() as [Views] or [Views, InputDelegates] {
        return [new JourneyView(), new JourneyDelegate()];
    }

    public function plan() as JourneyPlan? {
        return _plan;
    }

    public function isOutdated() as Boolean {
        return _outdated;
    }

    public static function now() as Number {
        return Time.now().value();
    }

    //! The journey kept from the last time the app was open, unless it is long over.
    private function restore() as Void {
        var stored = Application.Storage.getValue(KEY_PLAN);
        if (stored instanceof Dictionary) {
            var plan = new JourneyPlan(stored as Dictionary);
            if (plan.isOver(now(), LINGER_SECONDS)) {
                forget();
            } else {
                _plan = plan;
                var shown = Application.Storage.getValue(KEY_SHOWN);
                if (shown instanceof Dictionary) {
                    _shown = shown;
                }
            }
        }
    }

    private function forget() as Void {
        _plan = null;
        _shown = {};
        Application.Storage.deleteValue(KEY_PLAN);
        Application.Storage.deleteValue(KEY_SHOWN);
    }

    private function say(type as String) as Void {
        Communications.transmit({ "t" => type }, null, new QuietListener());
    }

    public function onTick() as Void {
        _ticks++;
        if (_ticks % ALIVE_EVERY_SECONDS == 0) {
            say("alive");
        }
        showDueAlerts();
        WatchUi.requestUpdate();
    }

    public function onPhone(message as Communications.PhoneAppMessage) as Void {
        var data = message.data;
        if (!(data instanceof Dictionary)) {
            return;
        }
        var version = data["v"];
        if (version instanceof Number && version > PlanFormat.VERSION) {
            _outdated = true;
            WatchUi.requestUpdate();
            return;
        }
        _outdated = false;
        var type = data["t"];
        if (type instanceof String) {
            if (type.equals("plan")) {
                onPlan(data);
            } else if (type.equals("alert")) {
                onAlert(data);
            } else if (type.equals("end")) {
                onEnd(data);
            } else if (type.equals("none")) {
                forget();
            }
        }
        WatchUi.requestUpdate();
    }

    private function onPlan(data as Dictionary) as Void {
        var plan = new JourneyPlan(data);
        if (_plan == null || !_plan.journeyId().equals(plan.journeyId())) {
            _shown = {};
        }
        _plan = plan;
        Application.Storage.setValue(KEY_PLAN, data as Application.Storage.ValueType);
        Application.Storage.setValue(KEY_SHOWN, _shown as Application.Storage.ValueType);
        showDueAlerts();
    }

    //! An alert the phone says is due now: one the plan does not hold, or one this app showed
    //! itself already, which is then not shown twice.
    private function onAlert(data as Dictionary) as Void {
        var id = data["alert"];
        var title = data["title"];
        if (_plan == null || !_plan.journeyId().equals(data["id"] as Object?) || !(id instanceof String) || !(title instanceof String)) {
            return;
        }
        if (_shown.hasKey(id)) {
            return;
        }
        markShown([id]);
        var text = data["text"];
        showAlert(title, text instanceof String ? text : "", true);
    }

    //! The journey is no longer followed. One that arrived stays to say so; one that was stopped goes.
    private function onEnd(data as Dictionary) as Void {
        if (_plan == null || !_plan.journeyId().equals(data["id"] as Object?)) {
            return;
        }
        var arrived = data["arrived"];
        if (!(arrived instanceof Number) || arrived != 1) {
            forget();
        }
    }

    private function markShown(ids as Array) as Void {
        for (var i = 0; i < ids.size(); i++) {
            _shown.put(ids[i], true);
        }
        Application.Storage.setValue(KEY_SHOWN, _shown as Application.Storage.ValueType);
    }

    private function showDueAlerts() as Void {
        var plan = _plan;
        if (plan == null) {
            return;
        }
        var now = now();
        var due = plan.dueAlerts(now, _shown);
        var ids = plan.dueAlertIds(now);
        var isNew = false;
        for (var i = 0; i < ids.size(); i++) {
            isNew = isNew || !_shown.hasKey(ids[i]);
        }
        if (isNew) {
            markShown(ids);
        }
        if (due.size() > 0) {
            // Several at once (the app was opened late): the newest is the one that still matters.
            var alert = due[due.size() - 1] as Array;
            showAlert(alert[2] as String, alert[3] as String, true);
        }
    }

    //! Takes the screen over with [title] and [text] until a key or a while has passed.
    public function showAlert(title as String, text as String, vibrate as Boolean) as Void {
        var view = new AlertView(title, text);
        if (_alertOpen) {
            WatchUi.switchToView(view, new AlertDelegate(), WatchUi.SLIDE_IMMEDIATE);
        } else {
            WatchUi.pushView(view, new AlertDelegate(), WatchUi.SLIDE_UP);
        }
        _alertOpen = true;
        if (vibrate && Attention has :vibrate) {
            Attention.vibrate([
                new Attention.VibeProfile(100, 400),
                new Attention.VibeProfile(0, 200),
                new Attention.VibeProfile(100, 400),
            ]);
        }
    }

    public function closeAlert() as Void {
        if (_alertOpen) {
            _alertOpen = false;
            WatchUi.popView(WatchUi.SLIDE_DOWN);
        }
    }

    //! "Stop following": asks the phone, which stops the journey and answers with its end.
    public function stopFollowing() as Void {
        var plan = _plan;
        if (plan != null) {
            Communications.transmit({ "t" => "stop", "id" => plan.journeyId() }, null, new StopListener());
        }
    }

    public function onStopSent(reached as Boolean) as Void {
        if (reached) {
            forget();
            WatchUi.requestUpdate();
        } else {
            showAlert(WatchUi.loadResource(Rez.Strings.PhoneUnreachable) as String, "", false);
        }
    }
}

function getApp() as PulseTransitApp {
    return Application.getApp() as PulseTransitApp;
}

//! For messages whose fate changes nothing: the phone notices a silent watch by itself.
class QuietListener extends Communications.ConnectionListener {
    public function initialize() {
        ConnectionListener.initialize();
    }

    public function onComplete() as Void {
    }

    public function onError() as Void {
    }
}

class StopListener extends Communications.ConnectionListener {
    public function initialize() {
        ConnectionListener.initialize();
    }

    public function onComplete() as Void {
        getApp().onStopSent(true);
    }

    public function onError() as Void {
        getApp().onStopSent(false);
    }
}
