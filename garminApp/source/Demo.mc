import Toybox.Lang;

//! A made-up journey starting now, in the phone's message format, for the demo build: a walk, a
//! wait, metro 5, a change to bus 20 and the walk home, with an alert twenty seconds in.
(:demo)
function demoPlan(now as Number) as Dictionary? {
    var m = 60;
    return {
        "t" => "plan",
        "v" => 1,
        "id" => "demo",
        "title" => "Journey to Home",
        "arr" => now + 29 * m,
        "f" => [
            [now, 2, "Walking to Jernbanetorget", "Then 5 at 09:06, platform 2 · Arrives 09:31", now + 4 * m],
            [now + 2 * m, 0, "5 towards Vestli", "Leaves 09:06, 2 min late · Platform 2 · From Jernbanetorget · Arrives 09:31", now + 4 * m],
            [now + 4 * m, 1, "On 5 to Majorstuen", "Next stop: Stortinget · 3 stops left · Then 20 at 09:18, platform B", now + 12 * m],
            [now + 12 * m, 0, "20 towards Skøyen", "Leaves 09:18 · Platform B · Arrives 09:31", now + 16 * m],
            [now + 16 * m, 1, "On 20 to Skøyen", "2 stops left · Arrives 09:31", now + 24 * m],
            [now + 24 * m, 2, "Walking to Home", "Arrives 09:31", now + 29 * m],
            [now + 29 * m, 3, "Arrived at Home", "Arrived 09:31", 0],
        ],
        "s" => [[0, now - 2 * m, now + 2 * m], [1, now + 2 * m, now + 4 * m], [2, now + 4 * m, now + 12 * m], [1, now + 12 * m, now + 16 * m], [2, now + 16 * m, now + 24 * m], [0, now + 24 * m, now + 29 * m]],
        "a" => [[now + 20, "depart-1", "5 leaves at 09:06", "From Jernbanetorget · Platform 2 · Towards Vestli"]],
    };
}

(:nodemo)
function demoPlan(now as Number) as Dictionary? {
    return null;
}
