#!/usr/bin/env python3
"""Generates docs/design/src/pages/journeys.html. Run from docs/design."""
import os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")  # run: python3 tools/gen_journeys.py
board_line = open(f"{ROOT}/src/pages/board.html").read().split("\n")[6]
assert board_line.startswith('<div class="app"><div class="board">')
BOARD = board_line[len('<div class="app">'):-len("</div>")]
# backdrop must not be interactive
BOARD = re.sub(r' data-goto="[^"]*"', "", BOARD)
BOARD = BOARD.replace('<div class="board">', '<div class="board jr-backdrop" aria-hidden="true" inert>', 1)


def state(sid, label, trigger, body, note=None, extra=""):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trigger}"{n}>\n<div class="app jr-app"{extra}>{body}</div>\n</div>\n'


def over(inner, label):
    return (f'{BOARD}<div class="overlay sheet-overlay jr-over" data-overlay data-static role="presentation">'
            f'<div class="sheet jr-sheet" role="dialog" aria-modal="true" aria-labelledby="{label}">{inner}</div></div>')


# ---------------------------------------------------------------- rationale
def choice(name, ic, title, detail, checked, to):
    sel = " is-selected" if checked else ""
    chk = " checked" if checked else ""
    return (f'<label class="mode-card jr-choice{sel}" data-state-to="{to}"><span class="mc-ic"><i data-icon="{ic}"></i></span>'
            f'<b>{title}</b><span>{detail}</span><input type="radio" name="{name}" value="{ic}"{chk}></label>')


def rationale(platform, location, tid):
    name = f"jr-mode-{tid}"
    time_detail = ("Departures, your stop and changes. No location." if platform == "ios"
                   else "Departures, your stop and changes, following delays. No location.")
    other = "ios" if platform == "ios" else "android"
    to_time = other if platform == "ios" else "android"
    to_loc = "ios-location" if platform == "ios" else "android-location"
    caveat = ""
    if platform == "ios" and not location:
        caveat = ('<div class="callout info jr-note" role="status"><i data-icon="info"></i><div class="body">'
                  'Without location, iOS pauses PulseTransit soon after you leave it. Alerts then come at the times known when it was last open, so later delays and faster routes are missed.</div></div>')
    nxt = "Continue"
    after = ("Next, your phone asks to allow location, then notifications." if location
             else "Next, your phone asks to allow notifications.")
    return over(f'''<div class="sheet-head"><span class="jr-head-ic"><i data-icon="notifications" class="ic-fill"></i></span><div class="grow"><h2 id="{tid}">Journey alerts</h2><span class="sub">Journey to Oslo lufthavn · leave 19:40</span></div></div>
<p class="jr-lead">PulseTransit can tell you when to leave, when your ride departs and when the next stop is yours. With your location it can also notice if you missed the ride.</p>
<fieldset class="jr-choices"><legend class="jr-sr">How to follow this journey</legend>
{choice(name, "schedule", "Alerts from live times", time_detail, not location, to_time)}
{choice(name, "my_location", "Also notice if I miss my ride", "Uses your location during the journey, also with the screen off.", location, to_loc)}
</fieldset>
{caveat}
<p class="jr-privacy"><i data-icon="lock" class="ic-sm"></i>Your location stays on this device and is never sent anywhere.</p>
<p class="jr-after">{after}</p>
<div class="jr-actions"><button class="btn text" data-goto="sheets/trip/loaded">Not now</button><button class="btn" data-state-to="{"location-denied" if location and platform == "android" else "system-prompt"}">{nxt}</button></div>''', tid)


TRIP_TL = '''<ol class="timeline jr-tl">
<li class="tl walk first"><span class="tm">19:40</span><span class="rail"><span class="dot"></span></span><span class="what"><b>Walk to Oslo S</b><span>9 min · 650 m</span></span><span></span></li>
<li class="tl rail-express"><span class="tm">19:52</span><span class="rail"><span class="dot"></span></span><span class="what"><span class="row"><span class="pill sm line-express">FLY2</span><b>Oslo lufthavn</b></span><span>From Oslo S · Platform 13</span></span><span></span></li>
<li class="tl last rail-express"><span class="tm">20:08</span><span class="rail"><span class="dot"></span></span><span class="what"><b>Oslo lufthavn</b><span>Arrive</span></span><span></span></li>
</ol>'''


def trip_sheet(tid, middle, following=False):
    sub = "Leave 19:40 · 28 min · arr. 20:08"
    status = ('<p class="status-line jr-following"><i data-icon="notifications_active" class="ic-done"></i>Following your journey to Oslo lufthavn</p>'
              if following else "")
    return over(f'''<div class="sheet-head"><div class="grow"><h2 id="{tid}">Oslo lufthavn</h2><span class="sub num">{sub}</span></div><button class="icon-btn" aria-label="Close" data-goto="board/main/loaded"><i data-icon="close"></i></button></div>
<p class="leg-summary"><i data-icon="directions_walk"></i>9 min<i data-icon="arrow_forward"></i><span class="pill sm line-express">FLY2</span>from Oslo S · direct</p>
{status}
{middle}
{TRIP_TL}''', tid)


SYS_PROMPT = BOARD + '''<div class="overlay jr-sys" data-overlay data-static role="presentation"><div class="dialog jr-sysdlg" role="dialog" aria-modal="true" aria-labelledby="jr-sys-t">
<span class="jr-sys-ic"><i data-icon="notifications" class="ic-lg"></i></span>
<h2 id="jr-sys-t">Allow <b>PulseTransit</b> to send you notifications?</h2>
<div class="jr-sys-btns"><button class="btn secondary block" data-goto="journeys/progress/waiting">Allow</button><button class="btn secondary block" data-state-to="denied">Don’t allow</button></div>
</div></div>'''

DENIED = trip_sheet("jr-den-t", '''<div class="callout warning" role="status"><i data-icon="notifications_off"></i><div class="body"><strong>Notifications are off for PulseTransit</strong>Turn them on in your phone’s settings to get alerts for this journey. The trip below still updates here.</div></div>
<div class="jr-actions start"><button class="btn">Open settings</button></div>''')

LOC_DENIED = trip_sheet("jr-locden-t", '''<div class="callout info" role="status"><i data-icon="location_off"></i><div class="body"><strong>Following live times only</strong>Location is off for PulseTransit, so it can’t notice a missed ride. You still get alerts for departures, your stop and changes.</div><button class="btn text">Open settings</button></div>
<div class="jr-actions start"><button class="btn outline" data-goto="sheets/trip/loaded">Stop following</button></div>''', following=True)

rationale_states = (
    state("android", "Android", "Tapped “Notify me about this journey” for the first time (Android)",
          rationale("android", False, "jr-ra-t"),
          "The choice is asked once, then lives in Settings › Journey alerts. Live times is preselected: it needs no extra permission.")
    + state("android-location", "Location chosen", "Picked “Also notice if I miss my ride”",
            rationale("android", True, "jr-ral-t"),
            "Continue asks for location first, then notifications: one system prompt at a time. Tap Continue to see location being refused.")
    + state("ios", "iOS", "Tapped “Notify me about this journey” for the first time (iOS)",
            rationale("ios", False, "jr-ri-t"),
            "iOS detail drops “following delays”; the time-limit caveat shows only while live times is chosen, as an info note (not a warning: nothing is wrong yet).")
    + state("ios-location", "iOS, location chosen", "Picked “Also notice if I miss my ride” on iOS",
            rationale("ios", True, "jr-ril-t"),
            "With location, iOS keeps the app running during the journey, so the caveat goes away.")
    + state("system-prompt", "System prompt", "Continue on the rationale; notification permission not yet asked",
            SYS_PROMPT, "The system’s own dialog (Android 13+), shown right after our rationale so the request has context. Allow starts following.")
    + state("denied", "Notifications denied", "Notification permission refused, now or earlier",
            DENIED, "T4: persistent callout in the trip sheet, role=status. Nothing is lost and the trip still works in the app; only the system can undo it, so the action goes to settings.")
    + state("location-denied", "Location denied", "Chose the location option but location was refused",
            LOC_DENIED, "T4: info, not error. Following starts anyway with time alerts (the graceful fallback); the callout says what is missing and how to get it back.")
)

# ---------------------------------------------------------------- notifications (lock screen)
APP_IC = '<span class="app-ic" aria-hidden="true"><i data-icon="departure_board"></i></span>'


def notif(title, body, pill=None, actions="", meta="now", goto="sheets/trip/loaded", extra_cls=""):
    end = f'<span class="jr-n-end">{pill}</span>' if pill else ""
    acts = f'<div class="n-actions">{actions}</div>' if actions else ""
    return (f'<article class="notif jr-notif{extra_cls}" aria-label="PulseTransit: {title}">{APP_IC}'
            f'<span class="n-meta">PulseTransit · {meta}</span>{end}'
            f'<a class="n-title jr-n-link" href="#" data-goto="{goto}">{title}</a><span class="n-body">{body}</span>{acts}</article>')


def lock(time, date, cards, island=""):
    return (f'<div class="lock jr-lock">{island}<div class="jr-lock-top"><i data-icon="lock" class="ic-sm"></i></div>'
            f'<div class="lock-time">{time}</div><div class="lock-date">{date}</div>'
            f'<div class="jr-stack">{cards}</div></div>')


FRI = "Friday 2 October"
SAT = "Saturday 3 October"
P = lambda cls, t, lab: f'<span class="pill line-{cls}" aria-label="{lab}">{t}</span>'
FLY2 = P("express", "FLY2", "Airport express train FLY2")
R14 = P("train", "R14", "Train R14")
B31 = P("bus", "31", "Bus 31")
WALK = '<span class="jr-n-walk" aria-hidden="true"><i data-icon="directions_walk"></i></span>'
DONE = '<span class="jr-n-walk" aria-hidden="true"><i data-icon="sports_score"></i></span>'
MISS = '<span class="jr-n-walk warn" aria-hidden="true"><i data-icon="running_with_errors"></i></span>'

notif_states = (
    state("departing", "Departing", "Your ride is about to leave (a few minutes before departure)",
          lock("19:41", FRI, notif("FLY2 leaves at 19:52", "From Oslo S · Platform 13", FLY2)),
          "Channel “Journey alerts” (high importance, sound). The line pill carries the mode colour so the alert reads before the text. Tap opens the trip sheet.")
    + state("next-stop", "Next stop", "The stop before yours was left",
            lock("20:05", FRI, notif("Next stop Oslo lufthavn is yours", "Get ready to get off.", FLY2)))
    + state("transfer", "Change", "Arriving at the stop where you change",
            lock("08:06", SAT, notif("Change to 31 at Lysaker", "Leaves at 08:09 · Towards Fornebu · Platform B", B31, meta="Journey to Fornebu · now")),
            "A different journey (Nationaltheatret → Fornebu) so a change exists. Body adds the platform when Entur knows it.")
    + state("walk", "Walk", "Time to start walking to the first stop",
            lock("19:40", FRI, notif("Walk to Oslo S", "9 min on foot", WALK,
                                     '<button class="btn secondary">Open map</button>')),
            "“Open map” opens the walk in the system maps app; tapping the alert opens the trip.")
    + state("faster", "Faster route", "A faster connection appeared before a change or departure",
            lock("19:41", FRI, notif("Faster: R14 at 19:47", "Arrives 20:01, 7 min sooner", R14,
                                     '<button class="btn secondary" data-goto="sheets/trip/loaded">Switch to it</button><button class="btn text" data-goto="journeys/progress/muted">Skip next alerts</button>')),
            "Only offered while switching is still possible. “Switch to it” replaces the followed journey; “Skip next alerts” keeps following silently.")
    + state("missed", "Missed ride", "Location shows you are not on the ride after it left (location option only)",
            lock("19:54", FRI, notif("Looks like you missed your journey?", "Open PulseTransit to find another route.", MISS, goto="board/main/loaded")),
            "Phrased as a question: location can be wrong. Tap opens the board with fresh routes. Never sent with time-only alerts.")
    + state("arrived", "Arrived", "Approaching the destination stop",
            lock("20:07", FRI, notif("Arriving at Oslo lufthavn", "Journey to Oslo lufthavn · arr. 20:08", DONE)),
            "Last alert; following stops by itself after arrival.")
)

STATUS = lambda t: (f'<div class="jr-sbar"><span class="num">{t}</span><span class="jr-sbar-ic" aria-hidden="true">'
                    '<i data-icon="signal_cellular_alt" class="ic-sm"></i><i data-icon="wifi" class="ic-sm"></i><i data-icon="battery_5_bar" class="ic-sm"></i></span></div>')


def tiles():
    t = [("wifi", "Fjord-5G", True), ("bluetooth", "Bluetooth", False), ("do_not_disturb_on", "Do not disturb", False), ("flashlight_on", "Torch", False)]
    return '<div class="jr-tiles" aria-hidden="true">' + "".join(
        f'<span class="jr-tile{" on" if on else ""}"><i data-icon="{i}"></i><span class="truncate">{l}</span></span>' for i, l, on in t) + "</div>"


# ---------------------------------------------------------------- progress
def seg(kind, w, f, cls=""):
    return f'<span class="jr-seg {kind} {cls}" style="--w:{w};--f:{f}%"></span>'


def steps(segs, marker, keys, aria):
    """segs: list of (kind, weight, fill%, lineclass). marker: (at%, icon, lineclass) or None. keys: list of (weight, icon, text) + end text."""
    m = ""
    if marker:
        at, ic, mc = marker
        m = f'<span class="jr-pos {mc}" style="--at:{at}%"><i data-icon="{ic}" class="ic-sm"></i></span>'
    bar = "".join(seg(k, w, f, c) for k, w, f, c in segs)
    end_ic = "check_circle" if marker is None else "flag"
    kk = "".join(f'<span style="--w:{w}"><i data-icon="{ic}" class="ic-sm"></i>{t}</span>' for w, ic, t in keys[:-1])
    return (f'<div class="jr-steps" role="img" aria-label="{aria}"><span class="jr-track">{bar}{m}</span>'
            f'<span class="jr-end{" done" if marker is None else ""}"><i data-icon="{end_ic}" class="ic-sm ic-fill"></i></span></div>'
            f'<div class="jr-keys" aria-hidden="true"><span class="jr-keys-run">{kk}</span><span class="jr-keys-end">{keys[-1]}</span></div>')


def progress_state(sid, label, trigger, note, *, island_l, island_r, title, line_pill, sub, big, big_k, bar,
                   android_meta="Journey to Oslo lufthavn", flag="", lock_time="19:40", status_time=None,
                   actions=True, mute_label=False):
    island = (f'<div class="jr-island" role="img" aria-label="Dynamic Island: {re.sub("<[^>]+>", "", island_l)} {re.sub("<[^>]+>", "", island_r)}">'
              f'<span class="jr-is-l">{island_l}</span><span class="jr-is-r num">{island_r}</span></div>')
    flagrow = f'<p class="jr-flag">{flag}</p>' if flag else ""
    acts = ('<div class="n-actions"><button class="btn text" data-goto="sheets/trip/loaded">Stop following</button>'
            + ('' if mute_label else '<button class="btn text" data-state-to="muted">Skip next alerts</button>') + '</div>') if actions else ""
    la = (f'<section class="jr-la" aria-label="Live Activity">'
          f'<div class="jr-la-head">{line_pill}<div class="jr-la-t"><b>{title}</b><span>{sub}</span></div>'
          f'<div class="jr-big"><span class="num">{big}</span><small>{big_k}</small></div></div>'
          f'{bar}{flagrow}</section>')
    andr = (f'<article class="notif jr-ongoing" aria-label="Ongoing: {re.sub("<[^>]+>", "", title)}">{APP_IC}'
            f'<span class="n-meta">PulseTransit · {android_meta}</span><span class="jr-n-end jr-big sm"><span class="num">{big}</span></span>'
            f'<span class="n-title">{title}</span><span class="n-body">{sub}</span>'
            f'<div class="jr-n-bar">{bar}</div>{flagrow.replace("jr-flag", "jr-flag n")}{acts}</article>')
    body = (f'<div class="jr-duo">'
            f'<div class="jr-panel"><p class="jr-plat"><i data-icon="phone_iphone" class="ic-sm"></i>iOS · Live Activity and Dynamic Island</p>'
            f'<div class="lock jr-lock jr-lock-ios">{island}<div class="lock-time">{lock_time}</div><div class="lock-date">{FRI if not android_meta.endswith("Fornebu") else SAT}</div>{la}</div></div>'
            f'<div class="jr-panel"><p class="jr-plat"><i data-icon="android" class="ic-sm"></i>Android · ongoing notification, expanded</p>'
            f'<div class="jr-shade">{STATUS(status_time or lock_time)}{andr}</div></div>'
            f'</div>')
    return state(sid, label, trigger, body, note)


PS = lambda c, t: f'<span class="pill sm line-{c}">{t}</span>'
# Oslo lufthavn journey: walk 19:40–19:49 (+3 min wait) = 12, FLY2 19:52–20:08 = 16
K_AIR = [(12, "directions_walk", "19:40"), (16, "train", "19:52"), "20:08"]

progress_states = (
    progress_state("waiting", "Waiting", "At the stop, ride not yet left", "Channel “Journey in progress”: silent, ongoing, updates in place. The countdown is the largest thing because it is the question at a stop.",
                   island_l=PS("express", "FLY2"), island_r="3 min", title="FLY2 from Oslo S", line_pill=PS("express", "FLY2"),
                   sub="Leaves 19:52 · Platform 13", big="3 min", big_k="to departure", lock_time="19:49",
                   bar=steps([("walk", 12, 100, ""), ("ride", 16, 0, "express")], (42, "hail", "walk"), K_AIR, "Walk done, waiting for FLY2, then arrive 20:08"))
    + progress_state("waiting-late", "Waiting, late", "Ride is delayed", "Delay goes into the text and the time turns late-red; the bar does not move, so nothing looks like progress.",
                     island_l=PS("express", "FLY2"), island_r='<span class="jr-late">6 min</span>', title="FLY2 from Oslo S", line_pill=PS("express", "FLY2"),
                     sub='<span class="jr-late">Leaves 19:55, 3 min late</span> · Platform 13', big='<span class="jr-late">6 min</span>', big_k="to departure", lock_time="19:49",
                     bar=steps([("walk", 12, 100, ""), ("ride", 16, 0, "express")], (42, "hail", "walk"), [(12, "directions_walk", "19:40"), (16, "train", "19:55"), "20:11"], "Walk done, FLY2 3 minutes late, then arrive 20:11"))
    + progress_state("walking", "Walking", "Walking to the first stop", "Walk segment is dashed (no line colour): it is the one part not run by a line.",
                     island_l='<i data-icon="directions_walk" class="ic-sm"></i>', island_r="5 min", title="Walking to Oslo S", line_pill='<span class="jr-walk-ic"><i data-icon="directions_walk"></i></span>',
                     sub="Then FLY2 at 19:52 · Platform 13", big="5 min", big_k="walk left", lock_time="19:44",
                     bar=steps([("walk", 12, 35, ""), ("ride", 16, 0, "express")], (15, "directions_walk", "walk"), K_AIR, "Walking to Oslo S, then FLY2 at 19:52, arrive 20:08"))
    + progress_state("riding", "Riding", "On board, moving between stops", "Marker sits on the FLY2 segment in the line colour; stops before yours show under the countdown (FLY2 has one, Lillestrøm).",
                     island_l=PS("express", "FLY2"), island_r="9 min", title="On FLY2 to Oslo lufthavn", line_pill=PS("express", "FLY2"),
                     sub="Next stop: Lillestrøm · Arrives 20:08", big="9 min", big_k="1 stop left", lock_time="19:59",
                     bar=steps([("walk", 12, 100, ""), ("ride", 16, 45, "express")], (68, "train", "express"), K_AIR, "On FLY2, 1 stop left, arrive 20:08"))
    + progress_state("change", "Change ahead", "Riding the first leg of a journey with a change", "Two coloured segments show the change at a glance: train blue, then bus red. The change platform rides along in the subtitle.",
                     island_l=PS("train", "L1"), island_r="2 min", title="On L1 to Lysaker", line_pill=PS("train", "L1"),
                     sub="Then 31 at Lysaker, platform B", big="2 min", big_k="to Lysaker", android_meta="Journey to Fornebu", lock_time="08:05",
                     bar=steps([("ride", 6, 60, "train"), ("walk", 3, 0, ""), ("ride", 12, 0, "bus")], (17, "train", "train"),
                               [(6, "train", "08:02"), (3, "directions_walk", ""), (12, "directions_bus", "08:09"), "08:21"], "On L1, change to bus 31 at Lysaker, arrive Fornebu 08:21"))
    + progress_state("arrived", "Arrived", "Reached the destination", "Bar full, end marker checked; the activity ends itself a few minutes later.",
                     island_l='<i data-icon="check_circle" class="ic-sm ic-fill"></i>', island_r="20:08", title="Arrived at Oslo lufthavn", line_pill='<span class="jr-walk-ic done"><i data-icon="sports_score"></i></span>',
                     sub="Arrived 20:08", big="20:08", big_k="arrived", lock_time="20:09", actions=False,
                     bar=steps([("walk", 12, 100, ""), ("ride", 16, 100, "express")], None, K_AIR, "Journey complete, arrived 20:08"))
    + progress_state("location-lost", "Location lost", "Location option chosen, but location went off during the journey",
                     "T4: a persistent line inside the progress itself, not a separate alert. Following carries on from live times; the bar keeps moving by the timetable.",
                     island_l=PS("express", "FLY2"), island_r="9 min", title="On FLY2 to Oslo lufthavn", line_pill=PS("express", "FLY2"),
                     sub="Next stop: Lillestrøm · Arrives 20:08", big="9 min", big_k="1 stop left", lock_time="19:59",
                     flag='<i data-icon="location_off" class="ic-sm"></i>Location off, following times only',
                     bar=steps([("walk", 12, 100, ""), ("ride", 16, 45, "express")], (68, "train", "express"), K_AIR, "On FLY2, 1 stop left, arrive 20:08"))
    + progress_state("muted", "Alerts skipped", "Tapped “Skip next alerts”", "Progress keeps updating silently; the line says why no sound will come. “Skip” is gone because it is done.",
                     island_l=PS("express", "FLY2"), island_r="3 min", title="FLY2 from Oslo S", line_pill=PS("express", "FLY2"),
                     sub="Leaves 19:52 · Platform 13", big="3 min", big_k="to departure", lock_time="19:49", mute_label=True,
                     flag='<i data-icon="notifications_off" class="ic-sm"></i>Alerts skipped',
                     bar=steps([("walk", 12, 100, ""), ("ride", 16, 0, "express")], (42, "hail", "walk"), K_AIR, "Walk done, waiting for FLY2, alerts skipped"))
)

SHADE = ('<div class="jr-shade jr-shade-full">' + STATUS("19:47") +
         '<div class="jr-shade-date"><b class="num">19:47</b><span>Fri 2 Oct</span></div>' + tiles() +
         '<div class="jr-stack">'
         + notif("FLY2 leaves at 19:52", "From Oslo S · Platform 13", FLY2)
         + notif("Faster: R14 at 19:47", "Arrives 20:01, 7 min sooner", R14,
                 '<button class="btn secondary" data-goto="sheets/trip/loaded">Switch to it</button><button class="btn text" data-goto="journeys/progress/muted">Skip next alerts</button>', meta="2 min ago")
         + '<p class="jr-group-k">Journey in progress</p>'
         + '<a class="jr-mini" href="#" data-goto="journeys/progress/waiting"><span class="app-ic" aria-hidden="true"><i data-icon="departure_board"></i></span><span class="pill sm line-express">FLY2</span><span class="truncate">FLY2 from Oslo S · Leaves 19:52</span><b class="num">5 min</b></a>'
         + '</div></div>')
notif_states += state("android-shade", "Android shade", "Pulled down the notification shade (Android) during a journey", SHADE,
                      "Android style: alerts on the “Journey alerts” channel above, the silent progress collapsed at the bottom (tap to expand: see Progress). Switch the theme to see the dark shade.")

PAGE = f'''<!-- Generated by a scratchpad script (gen_journeys.py). -->
<section class="v-page" data-page="journeys" data-title="Journey alerts" data-group="Journeys">
  <h1>Journey alerts</h1>
  <p class="v-lead">Following a trip after “Notify me about this journey”: the one-time choice, the alerts that answer <b>“When do I leave, get off or change?”</b>, and the silent progress that stays on the lock screen. Line colours carry the meaning; text stays short enough to read in a glance at a platform.</p>

  <article class="v-screen" data-screen="rationale" data-title="Rationale" data-purpose="Choose how the journey is followed (live times, or also location), then grant notifications. Primary action: Continue.">
{rationale_states}  </article>

  <article class="v-screen" data-screen="notifications" data-title="Alerts" data-purpose="Know when to leave, get off or change, without opening the app. Each alert is one decision; tap opens the trip.">
{notif_states}  </article>

  <article class="v-screen" data-screen="progress" data-title="Journey in progress" data-purpose="Where am I in the journey and how long until the next thing? Glance only: line colour and the walk → ride → arrive bar do the work.">
{progress_states}  </article>
</section>
'''
open(f"{ROOT}/src/pages/journeys.html", "w").write(PAGE)
print("ok", len(PAGE))
