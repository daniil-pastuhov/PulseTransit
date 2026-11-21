import Toybox.Lang;

//! Numbers both ends of the message format agree on.
module PlanFormat {
    //! The message format this app reads; the phone's WatchMessages.VERSION.
    const VERSION = 1;

    //! An alert this late is not shown: the phone's JourneyAlertPlanner.STALE_AFTER_MILLIS.
    const STALE_ALERT_SECONDS = 600;

    const PHASE_ARRIVED = 3;
    const SEGMENT_RIDE = 2;
}

//! The followed journey as the phone sent it: a "plan" message of the format the phone app's
//! WatchMessages (shared/.../data/journey/watch/WatchMessages.kt) writes, kept as it came so it can
//! be stored and read back unchanged. Times are seconds since 1970.
//!
//! - "f": frames [from, phase, headline, detail, nextAt], in time order: what to show from when.
//! - "s": segments [kind, start, end] for the progress ring; kind 2 is a ride.
//! - "a": alerts [at, id, title, text] still to come.
class JourneyPlan {
    private var _data as Dictionary;

    public function initialize(data as Dictionary) {
        _data = data;
    }

    public function data() as Dictionary {
        return _data;
    }

    public function journeyId() as String {
        var id = _data["id"];
        return id instanceof String ? id : "";
    }

    public function title() as String {
        var title = _data["title"];
        return title instanceof String ? title : "";
    }

    //! When the journey arrives; 0 when not known.
    public function arrivalAt() as Number {
        var at = _data["arr"];
        return at instanceof Number ? at : 0;
    }

    private function list(key as String) as Array {
        var value = _data[key];
        return value instanceof Array ? value : [];
    }

    public function segments() as Array {
        return list("s");
    }

    //! The index of the frame to show at [now]: the last that has started, the first before any has.
    public function frameIndexAt(now as Number) as Number {
        var frames = list("f");
        var current = 0;
        for (var i = 1; i < frames.size(); i++) {
            var frame = frames[i] as Array;
            if ((frame[0] as Number) > now) {
                break;
            }
            current = i;
        }
        return current;
    }

    //! The frame to show at [now]; null for a plan without frames.
    public function frameAt(now as Number) as Array? {
        var frames = list("f");
        return frames.size() == 0 ? null : frames[frameIndexAt(now)] as Array;
    }

    //! The frames after the one shown at [now].
    public function framesAfter(now as Number) as Array {
        var frames = list("f");
        var next = frameIndexAt(now) + 1;
        return next >= frames.size() ? [] : frames.slice(next, null);
    }

    //! The alerts due at [now] and not in [shown] (by id), oldest first. One too late to matter is
    //! left out; the caller marks every id up to now as shown either way (see dueAlertIds).
    public function dueAlerts(now as Number, shown as Dictionary) as Array {
        var due = [];
        var alerts = list("a");
        for (var i = 0; i < alerts.size(); i++) {
            var alert = alerts[i] as Array;
            var at = alert[0] as Number;
            if (at <= now && now - at <= PlanFormat.STALE_ALERT_SECONDS && !shown.hasKey(alert[1])) {
                due.add(alert);
            }
        }
        return due;
    }

    //! The ids of every alert whose time has come at [now], shown or too late to show.
    public function dueAlertIds(now as Number) as Array {
        var ids = [];
        var alerts = list("a");
        for (var i = 0; i < alerts.size(); i++) {
            var alert = alerts[i] as Array;
            if ((alert[0] as Number) <= now) {
                ids.add(alert[1]);
            }
        }
        return ids;
    }

    //! Whether the journey is over at [now] for long enough that it is no longer worth showing.
    public function isOver(now as Number, lingerSeconds as Number) as Boolean {
        var end = arrivalAt();
        var frames = list("f");
        if (end == 0 && frames.size() > 0) {
            end = (frames[frames.size() - 1] as Array)[0] as Number;
        }
        return now > end + lingerSeconds;
    }
}
