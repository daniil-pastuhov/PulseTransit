import Toybox.Lang;
import Toybox.Test;

//! JourneyPlan against a small plan timed from 1000: frames at 1000, 1120 and 1600, alerts at
//! 1060 and 1500. Run with scripts/build-garmin-app.sh <device> --unit-test, then monkeydo -t.
(:test)
module JourneyPlanTest {
    function plan() as JourneyPlan {
        return new JourneyPlan({
            "t" => "plan",
            "v" => 1,
            "id" => "j1",
            "title" => "Journey to Home",
            "arr" => 1600,
            "f" => [[1000, 2, "Walking", "", 1120], [1120, 1, "On 5", "2 stops left", 1600], [1600, 3, "Arrived", "", 0]],
            "s" => [[0, 1000, 1120], [2, 1120, 1600]],
            "a" => [[1060, "depart-1", "5 leaves", "Platform 2"], [1500, "next-stop-1", "Next stop is yours", ""]],
        });
    }

    (:test)
    function theFrameShownIsTheLastThatHasStarted(logger as Logger) as Boolean {
        var p = plan();
        return p.frameIndexAt(900) == 0 && p.frameIndexAt(1000) == 0 && p.frameIndexAt(1119) == 0 &&
            p.frameIndexAt(1120) == 1 && p.frameIndexAt(1599) == 1 && p.frameIndexAt(5000) == 2;
    }

    (:test)
    function framesAfterAreThoseStillToStart(logger as Logger) as Boolean {
        var p = plan();
        return p.framesAfter(1000).size() == 2 && p.framesAfter(1200).size() == 1 && p.framesAfter(1600).size() == 0;
    }

    (:test)
    function anAlertIsDueFromItsTimeUntilItIsShown(logger as Logger) as Boolean {
        var p = plan();
        var none = p.dueAlerts(1059, {});
        var one = p.dueAlerts(1060, {});
        var shown = p.dueAlerts(1100, { "depart-1" => true });
        return none.size() == 0 && one.size() == 1 && ((one[0] as Array)[1] as String).equals("depart-1") && shown.size() == 0;
    }

    (:test)
    function anAlertTooLateIsNotDueButCountsAsPast(logger as Logger) as Boolean {
        var p = plan();
        var late = 1060 + PlanFormat.STALE_ALERT_SECONDS + 1;
        var due = p.dueAlerts(late, {});
        // Only the second alert, itself due by then, is shown; both ids are past.
        return due.size() == 1 && ((due[0] as Array)[1] as String).equals("next-stop-1") && p.dueAlertIds(late).size() == 2;
    }

    (:test)
    function aPlanWithoutListsIsEmptyNotAnError(logger as Logger) as Boolean {
        var p = new JourneyPlan({ "id" => "j2" });
        return p.frameAt(1000) == null && p.segments().size() == 0 && p.dueAlerts(1000, {}).size() == 0 && p.title().equals("");
    }

    (:test)
    function textSplitsIntoItsWords(logger as Logger) as Boolean {
        var words = Draw.split("Then 5 at  09:06 ");
        return words.size() == 4 && (words[0] as String).equals("Then") && (words[3] as String).equals("09:06") && Draw.split("").size() == 0;
    }

    (:test)
    function aJourneyIsOverOnceItsArrivalIsLongPast(logger as Logger) as Boolean {
        var p = plan();
        return !p.isOver(1600 + 600, 600) && p.isOver(1600 + 601, 600);
    }
}
