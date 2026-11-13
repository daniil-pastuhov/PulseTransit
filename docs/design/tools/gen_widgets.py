#!/usr/bin/env python3
"""Generates docs/design/src/pages/widgets.html."""
import os; OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "pages", "widgets.html")  # run: python3 tools/gen_widgets.py

MODE = {"metro": ("line-metro", "Metro"), "bus": ("line-bus", "Bus"), "tram": ("line-tram", "Tram"),
        "train": ("line-train", "Train"), "express": ("line-express", "Airport express"), "ferry": ("line-ferry", "Ferry")}


def pill(line, mode, cancelled=False):
    cls, name = MODE[mode]
    if cancelled:
        cls = "line-other"
    return f'<span class="pill sm {cls}" aria-label="{name} {line}">{line}</span>'


def t(text, rt=True, first=False, x=False):
    c = ["wt"] + (["first"] if first else []) + (["rt"] if rt and not x else []) + (["x"] if x else [])
    lab = " (realtime)" if rt and not x else (" (cancelled)" if x else "")
    return f'<span class="{" ".join(c)}" aria-label="{text}{lab}">{text}</span>'


def status_bar(offline=False):
    net = '<i data-icon="wifi_off"></i>' if offline else '<i data-icon="wifi"></i>'
    return (f'<div class="w-sbar" aria-hidden="true"><span>19:37</span><span class="grow"></span>'
            f'<i data-icon="signal_cellular_alt"></i>{net}<i data-icon="battery_5_bar"></i></div>')


def home(widget, offline=False, apps=True):
    apps_html = ('<div class="w-apps" aria-hidden="true"><span class="w-app"><i data-icon="mail"></i></span>'
                 '<span class="w-app"><i data-icon="photo_camera"></i></span><span class="w-app"><i data-icon="map"></i></span>'
                 '<span class="w-app is-pt"><i data-icon="directions_bus"></i></span></div>') if apps else ""
    return (f'<div class="app"><div class="home w-home">{status_bar(offline)}'
            f'<div class="w-area">{widget}</div>{apps_html}'
            f'<div class="w-dock" aria-hidden="true"><div class="w-search"><i data-icon="search"></i>Search</div></div>'
            f'</div></div>')


def card(size, label, body, goto="board/main/loaded"):
    return (f'<div class="widget wg {size}" role="group" aria-label="{label}">'
            f'<a class="wg-open" href="#" data-goto="{goto}" aria-label="Open PulseTransit"></a>{body}</div>')


def head(title, refresh_to=None):
    to = f' data-state-to="{refresh_to}"' if refresh_to else ""
    return (f'<div class="wg-head"><h3>{title}</h3>'
            f'<button class="wg-refresh" aria-label="Refresh"{to}><i data-icon="refresh"></i></button></div>')


def foot(text, warn=False):
    if warn:
        return f'<div class="w-foot warn" role="status"><i data-icon="cloud_off"></i>{text}</div>'
    return f'<div class="w-foot">{text}</div>'


def msg(icon, text, sub=None):
    s = f"<span>{sub}</span>" if sub else ""
    return f'<div class="wg-msg"><i data-icon="{icon}"></i><strong>{text}</strong>{s}</div>'


# ---------- data ----------
ROWS = [  # line, mode, destination, platform, [(time, realtime, cancelled)]
    ("1", "metro", "Frognerseteren", "2", [("19:41", 1, 0), ("19:56", 1, 0), ("20:11", 0, 0)]),
    ("31", "bus", "Snarøya", "D", [("19:43", 1, 0), ("19:53", 1, 0), ("20:03", 0, 0)]),
    ("12", "tram", "Majorstuen", "B", [("19:45", 1, 0), ("19:55", 0, 0), ("20:05", 0, 0)]),
    ("2", "metro", "Østerås", "2", [("19:48", 1, 0), ("20:03", 0, 0), ("20:18", 0, 0)]),
    ("3", "metro", "Kolsås", "2", [("19:50", 0, 1), ("20:05", 1, 0), ("20:20", 0, 0)]),
    ("13", "tram", "Bekkestua", "B", [("19:51", 1, 0), ("20:01", 0, 0), ("20:11", 0, 0)]),
    ("30", "bus", "Bygdøy", "E", [("19:54", 1, 0), ("20:14", 0, 0), ("20:34", 0, 0)]),
    ("19", "tram", "Ljabru", "C", [("19:58", 0, 0), ("20:08", 0, 0), ("20:18", 0, 0)]),
]


def dep_rows(n, ntimes=3, ruled=False, platform=True, stacked=False):
    out = []
    for line, mode, dest, plat, times in ROWS[:n]:
        cancelled_first = times[0][2] == 1
        ts = "".join(t(tt, bool(rt), first=(i == 0), x=bool(x)) for i, (tt, rt, x) in enumerate(times[:ntimes]))
        pl = f'<span class="pl">{plat}</span>' if platform else "<span></span>"
        out.append(f'<li class="wg-row">{pill(line, mode, cancelled_first)}<span class="d">{dest}</span>{pl}<span class="ts">{ts}</span></li>')
    return f'<ul class="wg-rows{" ruled" if ruled else ""}{" stacked" if stacked else ""}">' + "".join(out) + "</ul>"


# ---------- at a glance ----------
def gl_top(next_html="", clock_size=""):
    return (f'<div class="gl-top"><div><div class="gl-clock">19:37</div><div class="gl-date">Fri 2 Oct</div></div>'
            f'<div>{next_html}</div>'
            f'<div class="gl-wx" aria-label="Light rain, 8 degrees"><i data-icon="rainy"></i><b>8°</b></div></div>')


def gl_next():
    return (f'<div class="gl-next">{pill("1", "metro")}<span class="d"><span>Frognerseteren</span><span>Jernbanetorget</span></span>'
            f'{t("19:41", True, True)}</div>')


def gl_later(n):
    out = []
    for line, mode, dest, plat, times in ROWS[1:1 + n]:
        tt, rt, x = times[0]
        out.append(f'<li class="wg-row" style="grid-template-columns:40px minmax(0,1fr) auto">{pill(line, mode, bool(x))}<span class="d">{dest}</span>{t(tt, bool(rt), x=bool(x))}</li>')
    return '<ul class="wg-rows ruled gl-later">' + "".join(out) + "</ul>"


GL_LABEL = "At a glance widget"
glance_medium = card("c4x2", GL_LABEL, gl_top() + gl_next())
glance_small = card("c2x2", GL_LABEL,
                    '<div class="gl-clock">19:37</div>'
                    '<div class="gl-sub" aria-label="Friday 2 October, light rain, 8 degrees">Fri 2 Oct · <i data-icon="rainy"></i><b>8°</b></div>'
                    f'<div class="gl-stack"><div class="row">{pill("1", "metro")}{t("19:41", True, True)}</div>'
                    '<span class="d">Frognerseteren</span></div>')
glance_tall = card("c4x4", GL_LABEL, gl_top() + gl_next() + gl_later(6))
glance_strip = card("c4x1", GL_LABEL,
                    '<div class="gl-top"><div><div class="gl-clock">19:37</div></div>'
                    f'<div class="gl-next">{pill("1", "metro")}<span class="d"><span>Frognerseteren</span><span>Fri 2 Oct</span></span>{t("19:41", True, True)}</div>'
                    '<div class="gl-wx" aria-label="Light rain, 8 degrees"><i data-icon="rainy"></i><b>8°</b></div></div>')
glance_offline = card("c4x2", GL_LABEL,
                      gl_top() + f'<div class="gl-next">{pill("1", "metro")}<span class="d"><span>Frognerseteren</span><span>Jernbanetorget</span></span>{t("19:41", False, True)}</div>'
                      + foot("Offline · data from 19:12", warn=True))
glance_nostops = card("c4x2", GL_LABEL, gl_top() + msg("add_location_alt", "Add a favorite stop in PulseTransit"),
                      goto="settings/add-stop/nearby")

# ---------- departures ----------
DL = "Departures widget, Jernbanetorget"
dep_medium = card("c4x2", DL, head("Jernbanetorget", "medium") + dep_rows(4) + foot("Updated 19:37"))
dep_large = card("c4x4", DL, head("Jernbanetorget", "large") + dep_rows(8, ruled=True) + foot("Updated 19:37"))
dep_small = card("c2x2", DL, head("Jernbanetorget", "small") + dep_rows(3, ntimes=1, platform=False, stacked=True))
dep_offline = card("c4x2", DL, head("Jernbanetorget", "medium") + dep_rows(4) + foot("Offline · data from 19:12", warn=True))
dep_nostops = card("c4x2", "Departures widget", head("PulseTransit") + msg("add_location_alt", "Add a favorite stop in PulseTransit", "Tap to open the app and pick one."),
                   goto="settings/add-stop/nearby")
dep_unreach = card("c4x2", DL, head("Jernbanetorget", "medium") + msg("cloud_off", "Could not reach Entur", "Nothing saved to show yet. Tap refresh to try again."))
dep_none = card("c4x2", "Departures widget, Lysaker brygge", head("Lysaker brygge", "medium") + msg("schedule", "No upcoming departures") + foot("Updated 19:37"))

# ---------- route ----------
RL = "Fastest route widget"


def route_body(small=False):
    legs = (f'<div class="rt-legs" aria-label="Walk 6 minutes, then train R14"><i data-icon="directions_walk"></i>6 min'
            f'<i data-icon="chevron_right" class="sep"></i>{pill("R14", "train")}</div>')
    if small:
        return (f'<div class="rt-leave">Leave 19:46</div>{legs}'
                '<div class="rt-line">Departs <b>19:52</b></div><div class="rt-line">Arrives <b>20:08</b></div>')
    return (f'<div class="rt-leave">Leave 19:46</div>{legs}'
            '<div class="rt-line">Departs <b>19:52</b> · platform 13</div>'
            '<div class="rt-line">Arrives <b>20:08</b> · 22 min</div>')


route_medium = card("c4x2", RL, head("To Lillestrøm stasjon", "medium") + route_body() + foot("Updated 19:37"), goto="sheets/trip/loaded")
route_small = card("c2x2", RL, head("To Lillestrøm stasjon", "small") + route_body(True), goto="sheets/trip/loaded")
route_multi = card("c4x2", RL, head("To Oslo lufthavn", "multi") +
                   '<div class="rt-leave">Leave 19:40</div>'
                   f'<div class="rt-legs" aria-label="Walk 4 minutes, tram 12, then airport express FLY2"><i data-icon="directions_walk"></i>4 min<i data-icon="chevron_right" class="sep"></i>{pill("12", "tram")}<i data-icon="chevron_right" class="sep"></i>{pill("FLY2", "express")}</div>'
                   '<div class="rt-line">Departs <b>19:44</b> · platform B</div><div class="rt-line">Arrives <b>20:31</b> · 51 min</div>'
                   + foot("Updated 19:37"), goto="sheets/trip/loaded")
route_walk = card("c4x2", RL, head("To Nationaltheatret", "walk-only") +
                  '<div class="rt-leave">Leave 19:44</div>'
                  '<div class="rt-legs"><i data-icon="directions_walk"></i>Walk all the way</div>'
                  '<div class="rt-line">Arrives <b>19:58</b> · 14 min</div>' + foot("Updated 19:37"), goto="sheets/trip/loaded")
route_noroute = card("c4x2", RL, head("To Fornebu", "medium") + msg("route", "No route found", "Nothing runs there in the next hours. Check the trip in PulseTransit.") + foot("Updated 19:37"))
route_nodest = card("c4x2", RL, head("PulseTransit") + msg("add_location_alt", "Add a destination in PulseTransit", "Tap to open the app and add one."), goto="settings/directions/all")
route_noorigin = card("c4x2", RL, head("To Majorstuen") + msg("location_off", "Set your location or add a stop to plan routes"), goto="settings/main/board")
route_atdest = card("c4x2", RL, head("To Majorstuen", "at-destination") + msg("where_to_vote", "You are already at Majorstuen"))
route_unreach = card("c4x2", RL, head("To Lillestrøm stasjon", "medium") + msg("cloud_off", "Could not reach Entur", "Tap refresh to try again."))


# ---------- picker ----------
def picker(title, items, selected):
    lis = []
    for name, sub, icon in items:
        cur = name == selected
        ck = '<i data-icon="check" class="ck"></i>' if cur else ""
        lis.append(f'<li><a class="list-item" href="#" data-goto="widgets/{"route" if "Destination" in title else "departures"}/medium"'
                   f'{' aria-current="true"' if cur else ""}><i data-icon="{icon}" class="lead"></i>'
                   f'<span class="text"><span>{name}</span><span>{sub}</span></span>{ck}</a></li>')
    return (f'<div class="app"><div class="wpick"><header class="topbar"><span class="title">{title}</span></header>'
            f'<p class="hint">Tap one to show it on this widget. Until you do, it shows the first one.</p>'
            f'<ul class="list">{"".join(lis)}</ul></div></div>')


pick_stop = picker("Stop to show", [("Jernbanetorget", "Oslo", "subway"), ("Oslo S", "Oslo", "train"),
                                     ("Nationaltheatret", "Oslo", "subway"), ("Lysaker brygge", "Bærum", "directions_boat")], "Jernbanetorget")
pick_dest = picker("Destination to show", [("Lillestrøm stasjon", "Lillestrøm", "location_on"), ("Oslo lufthavn", "Ullensaker", "location_on"),
                                            ("Majorstuen", "Oslo", "location_on"), ("Fornebu", "Bærum", "location_on")], "Lillestrøm stasjon")
pick_none = ('<div class="app"><div class="wpick"><header class="topbar"><span class="title">Stop to show</span></header>'
             '<div class="empty"><svg class="art" viewBox="0 0 160 120" aria-hidden="true"><rect x="28" y="24" width="104" height="72" rx="16" style="fill:var(--color-primary-container)"/>'
             '<rect x="44" y="42" width="24" height="14" rx="4" style="fill:var(--color-primary)"/><rect x="76" y="44" width="40" height="10" rx="5" style="fill:var(--color-surface)"/>'
             '<rect x="44" y="64" width="24" height="14" rx="4" style="fill:var(--color-outline)"/><rect x="76" y="66" width="30" height="10" rx="5" style="fill:var(--color-surface)"/></svg>'
             '<h2>Nothing saved yet</h2><p>Add some in PulseTransit, then come back.</p>'
             '<button class="btn" data-goto="settings/add-stop/nearby">Open PulseTransit</button></div></div></div>')


def state(sid, label, trig, body, note=None):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trig}"{n}>\n{body}\n</div>\n'


def screen(sid, title, purpose, states):
    return f'<article class="v-screen" data-screen="{sid}" data-title="{title}" data-purpose="{purpose}">\n{"".join(states)}</article>\n'


page = ['<!-- Generated by scratchpad gen_widgets.py; edit freely by hand from now on. -->\n'
        '<section class="v-page" data-page="widgets" data-title="Home-screen widgets" data-group="Widgets">\n'
        '<h1>Home-screen widgets</h1>\n'
        '<p class="v-lead">Three Android (Glance) widgets, drawn at their real launcher size on a home screen. Primary question: <b>“When do I need to go?”</b>, answered without opening the app. '
        'A widget is one flat surface (no blur, no shadow: RemoteViews draws neither reliably), line pills as on the board, and one footer line that says how old the data is. '
        'Times are <b>clock times, not countdowns</b>: a widget redraws every 15 min, so “4 min” would be wrong a minute later. Accent colour and a dot mark realtime; plain is timetable; struck through is cancelled. '
        'Tapping anywhere opens the board; only the refresh icon is a separate target (drawn at 20, 48 to touch).</p>\n'
        '<p class="v-lead"><b>For iOS WidgetKit later:</b> 2×2 = small, 4×2 = medium, 4×4 = large; 4×1 and 4×3 have no iOS home-screen size (4×1 maps best to the lock-screen rectangular accessory). '
        'iOS 15–16 widgets cannot hold buttons, so the refresh icon is dropped there (the timeline reloads itself) and the header title gets the full width. The picker is Android only: on iOS the system edit sheet lists the same choices from an intent configuration. '
        'The weather and mode icons are bundled Material Symbols drawables on Android (Glance <code>Image</code>) and SF Symbols or the same assets on iOS, replacing today’s emoji.</p>\n']

page.append(screen("glance", "At a glance", "Primary question: what time is it, what is the weather, and when does my next ride from my first favourite leave? Read only; a tap opens the board.", [
    state("medium", "4×2 (medium)", "Widget placed at 4×2; first favourite stop has departures", home(glance_medium),
          "Next departure sits on a tinted container so it reads as the answer, not as a fourth line of the clock."),
    state("small", "2×2 (small)", "Widget resized to 2×2", home(glance_small),
          "Clock, then date and temperature on one line, then the next departure stacked. Differs from today’s code, whose 150 dp clock column plus weather column overflow 146 dp of content width."),
    state("tall", "4×4 (tall)", "Widget resized to 4×4: room for the departures after the next", home(glance_tall),
          "Later rows show one time each, as many as fit (six here), like LaterDeparture."),
    state("strip", "4×1 (default size)", "Widget just placed: the provider’s target size is 4×1", home(glance_strip),
          "The placement default. Stop name gives way to the date so the strip still says which day it is."),
    state("offline", "Offline (cached)", "No connection; showing the last cached departure", home(glance_offline, offline=True),
          "T4: a lasting condition with cached data on screen, so a persistent footer line, not a replacement. The time loses its realtime accent because the cached prediction is no longer live. GlanceView has no status field today; this needs one."),
    state("no-stops", "No favourite stop", "No favourite stop saved yet", home(glance_nostops),
          "T5 inside the widget: nothing to show, so the message takes the departure’s place; clock and weather still work. Tap goes to adding a stop."),
]))

page.append(screen("departures", "Departures", "Primary question: when do the next few departures from this stop leave? Rows are one line + destination each, with the next times.", [
    state("medium", "4×2 (medium)", "Stop chosen (or first favourite); departures loaded", home(dep_medium),
          "Four rows, three times each, platform shown (width ≥ 240 dp)."),
    state("large", "4×4 (large)", "Widget resized to 4×4", home(dep_large),
          "Eight rows, the provider’s maximum, with rules between them since there is room. Line 3’s first departure is cancelled: grey pill, struck-through time."),
    state("small", "2×2 (small)", "Widget resized to 2×2", home(dep_small),
          "Three rows of two lines (pill and next time, destination under it): on one line the code’s layout leaves the name about 40 dp. One time, no platform; the footer gives way, so an offline 2×2 shows plain (non-accent) times as its only hint."),
    state("offline", "Offline (cached)", "No connection; cached departures from 19:12", home(dep_offline, offline=True),
          "T4: cached rows stay, the footer says how old they are. Times from before 19:37 drop out as they pass."),
    state("no-departures", "No departures", "Stop has nothing in the next hours (a ferry pier at night)", home(dep_none),
          "Empty, not an error: “No upcoming departures” plus the normal Updated line, so it reads as checked."),
    state("no-stops", "No stops", "No favourite stop saved", home(dep_nostops),
          "T5 inside the widget. Title falls back to the app name. Tap opens adding a stop."),
    state("unreachable", "Could not reach Entur", "Refresh failed and nothing is cached", home(dep_unreach, offline=True),
          "T5: nothing cached to show, so the message replaces the rows; refresh retries. The footer is dropped because today it repeats the same sentence."),
]))

page.append(screen("route", "Fastest route", "Primary question: when must I leave to get to my destination, and on what? The leave-by time is the biggest thing on the widget.", [
    state("medium", "4×2 (medium)", "Destination chosen; a route was found from the current location or first stop", home(route_medium),
          "Flat surface, not the board’s route banner: a blue slab next to other apps’ widgets is too loud, the accent leave time carries it. Legs become pills (RouteView needs a list of legs instead of one emoji string)."),
    state("small", "2×2 (small)", "Widget resized to 2×2", home(route_small),
          "The title wraps to two lines instead of cutting the place name; platform and duration drop so each line fits 146 dp without ellipsis; the footer gives way for height. RouteView needs boarding time and platform as separate fields for this."),
    state("multi", "Several legs", "Route with a change", home(route_multi),
          "Two rides: pills and chevrons; if they do not fit, the row clips rather than wraps."),
    state("walk-only", "Walk all the way", "Walking beats every ride", home(route_walk),
          "No pill and no Departs line; arrival keeps the duration."),
    state("no-route", "No route", "Journey planner returned no trip", home(route_noroute),
          "T5 inside the widget: nothing to show; the Updated line confirms it was checked just now."),
    state("unreachable", "Could not reach Entur", "Refresh failed, no cached route", home(route_unreach, offline=True),
          "T5: no cached route, so the message replaces it; refresh retries."),
    state("no-destination", "No destination", "No destination saved", home(route_nodest),
          "T5 inside the widget; tap opens destinations in Settings."),
    state("no-origin", "No origin", "No location permission and no favourite stop to route from", home(route_noorigin),
          "T5 inside the widget; tap opens Settings, where location or a stop can be set."),
    state("at-destination", "Already there", "Current location is at the destination", home(route_atdest),
          "Not an error: a plain statement with a check-place icon, no footer warning."),
]))

page.append(screen("picker", "Choose what to show", "Primary action: pick which favourite stop (or destination) this widget shows. Opens when the widget is placed and on long-press › Reconfigure (Android 12+).", [
    state("stop", "Stop", "Departures widget placed or reconfigured", pick_stop,
          "Android only. One tap picks and closes; the current choice has a check. Backing out keeps the default."),
    state("destination", "Destination", "Fastest route widget placed or reconfigured", pick_dest,
          "Same list for destinations."),
    state("none", "Nothing saved", "No favourites or destinations saved", pick_none,
          "Empty state teaches the next step; adds an Open PulseTransit button (today the screen is text only)."),
]))

page.append("</section>\n")
open(OUT, "w").write("".join(page))
print("ok")
