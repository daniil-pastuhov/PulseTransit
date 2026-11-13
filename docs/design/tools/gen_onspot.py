"""Generates docs/design/src/pages/onspot.html. Edit this file and rerun: python3 tools/gen_onspot.py"""
import os; OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "pages", "onspot.html")  # run: python3 tools/gen_onspot.py

MODE_IC = dict(bus="directions_bus", tram="tram", metro="subway", train="train", express="train", ferry="directions_boat")
MODE_NAME = dict(bus="Bus", tram="Tram", metro="Metro", train="Train", express="Airport express train", ferry="Ferry")


def dep(line, mode, dest, time="", *, delay=None, now=False, planned=None, sub=None):
    if now: t = '<span class="t-time now">Now</span>'
    elif planned: t = f'<span class="t-time"><s class="planned" aria-label="planned {planned}">{planned}</s> <span class="late-time">{time}</span></span>'
    else: t = f'<span class="t-time">{time}</span>'
    d = f'<span class="delay" aria-label="{delay} minutes late">+{delay}</span>' if delay else '<span></span>'
    s = f'<span class="dep-sub">{sub}</span>' if sub else ""
    return (f'<li><a class="dep" href="#" data-goto="sheets/departure/loaded">'
            f'<span class="pill line-{mode}" aria-label="{MODE_NAME[mode]} {line}">{line}</span><i data-icon="{MODE_IC[mode]}" class="mode"></i>'
            f'<span class="dest"><span>{dest}</span>{s}</span>{d}{t}</a></li>')


OSLOS = [dep("L1", "train", "Asker", now=True, sub="Platform 2"), dep("R14", "train", "Kongsvinger", "4 min", sub="Platform 4"),
         dep("FLY2", "express", "Oslo lufthavn", "8 min", sub="Platform 13"), dep("R21", "train", "Moss", "11 min", sub="Platform 7"),
         dep("F4", "train", "Bergen", "20:29", planned="20:25", delay=4, sub="Platform 3"), dep("RE30", "train", "Gjøvik", "20:34", sub="Platform 9")]
JBT = [dep("1", "metro", "Frognerseteren", now=True), dep("30", "bus", "Bygdøy", "4 min", delay=3),
       dep("17", "tram", "Rikshospitalet", "6 min"), dep("2", "metro", "Ellingsrudåsen", "7 min", sub="Every ~8 min"),
       dep("31", "bus", "Tonsenhagen", "11 min"), dep("5", "metro", "Sognsvann", "19:56")]


def header():
    hours = [("20", "rainy", "8°"), ("21", "rainy", "8°"), ("22", "cloud", "7°"), ("23", "partly_cloudy_night", "6°")]
    hrs = "".join(f'<li><span class="h">{h}</span><i data-icon="{ic}"></i><span class="tmp">{tp}</span></li>' for h, ic, tp in hours)
    return ('<header class="b-head"><div class="b-clock"><span class="clock">19:37</span><span class="date"><b>Fri</b> 2 Oct</span></div>'
            '<div class="b-actions"><button class="icon-btn" aria-label="Find a route" data-goto="sheets/search/empty"><i data-icon="search"></i></button>'
            '<button class="icon-btn" aria-label="Settings" data-goto="settings/main/onspot"><i data-icon="settings"></i></button></div>'
            '<div class="b-wx"><div class="b-now"><i data-icon="rainy" class="wx"></i><span class="tmp">8°</span><span class="wx-label">Light rain</span></div>'
            f'<ol class="b-hours" aria-label="Next hours">{hrs}</ol></div></header>')


def trip(leave, walk, line, mode, frm, arr, dur, soon=None):
    lbl = f"Leave {leave}: walk {walk} min, {MODE_NAME[mode].lower()} {line} from {frm}, arrive {arr}, {dur}"
    soon_html = f'<small>in {soon} min</small>' if soon else ''
    return (f'<li><a class="trip os-trip{" is-next" if soon else ""}" href="#" data-goto="sheets/trip/loaded" aria-label="{lbl}">'
            f'<span class="tt">{leave}{soon_html}</span>'
            f'<span class="os-legs"><span class="os-leg"><i data-icon="directions_walk"></i>{walk} min<i data-icon="arrow_forward" class="os-arrow"></i>'
            f'<span class="pill sm line-{mode}">{line}</span><span class="truncate">from {frm}</span></span>'
            f'<span class="os-arr">arr. {arr}</span></span>'
            f'<span class="dur">{dur}</span></a></li>')


LILL = [trip("19:41", 2, "R14", "train", "Oslo S", "19:53", "12 min", soon=4),
        trip("19:49", 2, "RE11", "train", "Oslo S", "20:01", "12 min"),
        trip("19:56", 2, "L1", "train", "Oslo S", "20:17", "21 min"),
        trip("20:01", 2, "R13", "train", "Oslo S", "20:13", "12 min")]
MAJ = [trip("19:40", 4, "3", "metro", "Jernbanetorget", "19:52", "12 min", soon=3),
       trip("19:44", 4, "5", "metro", "Jernbanetorget", "19:55", "11 min"),
       trip("19:47", 3, "11", "tram", "Jernbanetorget", "20:03", "16 min")]


def dest(name, rows):
    return (f'<section class="os-dest" aria-label="To {name}"><h3><i data-icon="flag"></i>To {name}</h3>'
            f'<ul class="trips">{"".join(rows)}</ul></section>')


def routes(body=None, extra_cls="", label_extra=""):
    inner = body if body is not None else dest("Lillestrøm stasjon", LILL) + dest("Majorstuen", MAJ)
    return (f'<section class="os-routes{extra_cls}" aria-labelledby="os-rl"><h2 class="os-label" id="os-rl">Routes{label_extra}</h2>{inner}</section>')


def stop(name="Oslo S", sub="83 m · Oslo", rows=OSLOS, body=None, end="", sub_icon="near_me"):
    inner = body if body is not None else f'<ul class="deps">{"".join(rows)}</ul>'
    return (f'<section class="stop os-stop"><header class="s-head">'
            f'<h3><button class="s-pick" data-goto="onspot/chooser/loaded" aria-label="{name}, {sub}. Choose stop">{name}<i data-icon="expand_more"></i></button></h3>'
            f'<span class="s-sub"><i data-icon="{sub_icon}" class="ic-sm"></i>{sub}</span>{end}</header>{inner}</section>')


DIVIDER = ('<div class="os-divider" role="separator" aria-orientation="horizontal" aria-label="Drag to resize" '
           'aria-valuemin="0" aria-valuemax="100" aria-valuenow="55" tabindex="0"><span class="grip"></span></div>')


def foot(text='Updated 19:37 · Data from Entur · Weather from MET Norway'):
    return f'<footer class="b-foot">{text}</footer>'


def split(*parts, cls=""):
    return f'<div class="os-split{(" " + cls) if cls else ""}">{"".join(parts)}</div>'


def app(*parts, overlay=""):
    return f'<div class="app os-app"><div class="board os-board">{"".join(parts)}</div>{overlay}</div>'


def state(sid, label, trigger, body, note=None):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trigger}"{n}>\n{body}\n</div>\n'


def status(text, spinner=True, icon="my_location"):
    lead = '<span class="spinner os-spin" aria-hidden="true"></span>' if spinner else f'<i data-icon="{icon}"></i>'
    return f'<div class="os-status" role="status">{lead}<span>{text}</span></div>'


skel_trip = ('<li><div class="trip os-trip"><span class="skeleton" style="width:48px;height:24px"></span>'
             '<span class="os-legs"><span class="skeleton line" style="width:80%"></span><span class="skeleton line" style="width:40%"></span></span>'
             '<span class="skeleton line" style="width:40px"></span></div></li>')
skel_dep = ('<li><div class="dep"><div class="skeleton" style="width:44px;height:28px"></div><div class="skeleton circle" style="width:20px;height:20px"></div>'
            '<div class="skeleton line" style="width:70%"></div><span></span><div class="skeleton line" style="width:48px"></div></div></li>')


def skel_dest(name, n):
    return f'<section class="os-dest" aria-busy="true"><h3><i data-icon="flag"></i>To {name}</h3><ul class="trips">{skel_trip * n}</ul></section>'


SKEL_STOP = ('<section class="stop os-stop" aria-busy="true"><header class="s-head"><span class="skeleton" style="width:120px;height:22px"></span>'
             '<span class="skeleton line" style="width:70px"></span></header>'
             f'<ul class="deps">{skel_dep * 4}</ul></section>')

PIN_OFF = ('<svg class="art" viewBox="0 0 160 120" aria-hidden="true">'
           '<rect x="16" y="30" width="128" height="72" rx="14" style="fill:var(--color-surface-container)"/>'
           '<path d="M16 66h128M60 30v72M104 30v72" style="stroke:var(--color-outline-variant);stroke-width:3"/>'
           '<path d="M80 14c-15 0-26 11-26 25 0 19 26 45 26 45s26-26 26-45c0-14-11-25-26-25z" style="fill:var(--color-primary)"/>'
           '<circle cx="80" cy="39" r="9" style="fill:var(--color-surface)"/>'
           '<circle cx="118" cy="86" r="16" style="fill:var(--color-surface);stroke:var(--color-outline);stroke-width:3"/>'
           '<path d="M107 97l22-22" style="stroke:var(--color-outline);stroke-width:3;stroke-linecap:round"/></svg>')
PIN_LOST = ('<svg class="art" viewBox="0 0 160 120" aria-hidden="true">'
            '<circle cx="80" cy="62" r="44" style="fill:none;stroke:var(--color-outline-variant);stroke-width:3;stroke-dasharray:6 8"/>'
            '<circle cx="80" cy="62" r="26" style="fill:none;stroke:var(--color-outline-variant);stroke-width:3;stroke-dasharray:6 8"/>'
            '<circle cx="80" cy="62" r="10" style="fill:var(--color-surface-container);stroke:var(--color-outline);stroke-width:3"/>'
            '<text x="80" y="68" text-anchor="middle" style="fill:var(--color-on-surface-variant);font:700 16px var(--font-body, sans-serif)">?</text></svg>')

S = []
main = []
main.append(state("loaded", "Loaded", "Precise fix; two saved destinations; nearest stop Oslo S found",
    app(header(), split(routes(), DIVIDER, stop()), foot())))
main.append(state("locating", "Locating", "App opened; waiting for the first precise fix",
    app(header(), status("Finding your location…"),
        split(routes(skel_dest("Lillestrøm stasjon", 3) + skel_dest("Majorstuen", 2)), DIVIDER, SKEL_STOP),
        foot("Data from Entur · Weather from MET Norway")),
    "Destination names are known locally, so they show at once; trips and the stop are skeletons in the loaded layout. No full-screen spinner."))
main.append(state("approximate", "Approximate location", "GPS gave no fix yet; a network (approximate) fix is used meanwhile",
    app(header(), status("Finding your approximate location…"),
        split(routes(dest("Lillestrøm stasjon", LILL[:3]) + dest("Majorstuen", MAJ[:2]), label_extra='<span class="os-approx"><i data-icon="near_me_disabled" class="ic-sm"></i>Approximate location</span>'),
              DIVIDER, stop(sub="About 300 m · Oslo", sub_icon="near_me_disabled", rows=OSLOS[:4])), foot()),
    "T4 inline: results from a rough fix are better than nothing, but distances and walk times are marked “Approximate location” until a precise fix replaces them. The status line keeps saying what the app is doing."))
main.append(state("denied", "Location denied", "The user refused location access, or turned it off for PulseTransit",
    app(header(), '<div class="screen-error os-full">' + PIN_OFF +
        '<h2>Location is off for PulseTransit</h2><p>Location access is needed to show routes and stops from where you are.</p>'
        '<button class="btn" data-state-to="locating"><i data-icon="my_location"></i>Allow location</button>'
        '<p class="os-hint">You can still find a route with <i data-icon="search" class="ic-sm"></i> Search.</p></div>', foot("Data from Entur · Weather from MET Norway")),
    "T5: on the spot has no content without a location, so the area is replaced. Header stays (clock, weather, search, settings) so the user isn’t trapped. “Allow location” asks again, or opens system settings if the OS no longer asks."))
main.append(state("unavailable", "Location unavailable", "Permission granted, but neither GPS nor network gave a fix before the timeout",
    app(header(), '<div class="screen-error os-full">' + PIN_LOST +
        '<h2>Could not find your location.</h2><p>Check that location services are on. Indoors, moving closer to a window helps.</p>'
        '<button class="btn" data-state-to="locating">Try again</button></div>', foot("Data from Entur · Weather from MET Norway")),
    "T5 with Try again: nothing to show and nothing cached for this place. Distinct from “denied”: here the fix is the device, not a permission."))

NO_STOP_ROUTES = [trip("19:38", 18, "5", "metro", "Sognsvann", "20:12", "34 min", soon=1),
                  trip("19:53", 18, "5", "metro", "Sognsvann", "20:27", "34 min")]
main.append(state("no-stops", "No stops nearby", "Out in Nordmarka: no stop within 1 km of the fix",
    app(header(), split(routes(dest("Majorstuen", NO_STOP_ROUTES)), DIVIDER,
        '<section class="stop os-stop"><div class="panel-msg"><i data-icon="wrong_location"></i><strong>No stops within 1 km.</strong>'
        '<span>Routes above include the walk to the nearest stop.</span></div></section>'), foot()),
    "Empty, not an error: the stop panel says why it is empty; routes still work because the planner includes the walk."))
main.append(state("no-destinations", "No destinations", "On-spot mode with no saved destinations (fresh install)",
    app(header(), split(routes('<div class="panel-msg os-empty-routes"><i data-icon="add_location_alt"></i>'
        '<strong>No destinations yet</strong><span>Add a destination to see routes from where you are.</span>'
        '<button class="btn" data-goto="settings/main/onspot"><i data-icon="add"></i>Add destination</button>'
        '<span class="os-hint">For a one-off trip, tap <i data-icon="search" class="ic-sm"></i> Search.</span></div>', extra_cls=" is-empty"),
        DIVIDER, stop()), foot()),
    "First-use empty state teaches the next step. Departures from the nearest stop already work without any setup."))
main.append(state("other-stop", "Other stop chosen", "The user picked Jernbanetorget in the stop chooser",
    app(header(), split(routes(), DIVIDER,
        stop("Jernbanetorget", "240 m · Oslo", JBT, sub_icon="place",
             end='<button class="btn text s-end os-back" data-state-to="loaded"><i data-icon="near_me"></i>Back to nearest</button>')), foot()),
    "The chosen stop stays until the user goes back or moves 20 m; “Back to nearest” is always visible in the panel head so the choice is never sticky by surprise."))
main.append(state("routes-only", "Routes only", "Divider dragged to the bottom edge",
    app(header(), split(routes(), '<button class="os-fold os-fold-bottom" data-state-to="loaded"><i data-icon="expand_less"></i>'
        '<span>Show departures</span><span class="os-fold-sub">Oslo S · 83 m</span></button>', cls="is-routes-only"), foot()),
    "Phone only. The collapsed pane leaves a 48 px bar that names what it hides; tap to restore the last split. Wide screens have no divider and always show both panes, so this state only exists on phones."))
main.append(state("departures-only", "Departures only", "Divider dragged to the top edge",
    app(header(), split('<button class="os-fold os-fold-top" data-state-to="loaded"><i data-icon="expand_more"></i>'
        '<span>Show routes</span><span class="os-fold-sub">2 destinations</span></button>', stop(), cls="is-departures-only"), foot()),
    "Phone only. Mirror of routes-only."))
main.append(state("offline", "Offline (stale)", "No connection; last data 12 min old",
    app(header(), '<div class="callout warning" role="status"><i data-icon="wifi_off"></i><div class="body"><strong>No internet — shown data is 12 min old</strong>Updates as soon as you’re back online.</div></div>',
        split(routes(dest("Lillestrøm stasjon", LILL[1:]) + dest("Majorstuen", MAJ[1:])), DIVIDER, stop(rows=OSLOS[1:])),
        foot('<span class="warn"><i data-icon="wifi_off" class="ic-sm"></i>Updated 19:25</span> · Data from Entur')),
    "T4 under the header, role=status, lasts while offline. Cached routes and departures stay readable; passed times have dropped off. Location keeps working offline."))


def stop_opt(name, dist, modes, *, current=False, goto=None):
    ics = "".join(f'<i data-icon="{MODE_IC[m]}" aria-hidden="true"></i>' for m in modes)
    act = f' data-goto="{goto}"' if goto else ' data-close'
    tag = ' <span class="tag">Nearest</span>' if dist == "83 m" else ""
    chk = '<i data-icon="check_circle" class="ic-fill os-check"></i>' if current else '<span class="os-check-space"></span>'
    return (f'<li><button class="os-opt{" is-current" if current else ""}" role="radio" aria-checked="{"true" if current else "false"}"{act}>'
            f'<span class="text"><span class="os-opt-name">{name}{tag}</span>'
            f'<span class="os-opt-sub"><span class="num">{dist}</span><span class="os-modes" aria-label="{", ".join(MODE_NAME[m] for m in modes)}">{ics}</span></span></span>{chk}</button></li>')


def chooser(body):
    return ('<div class="overlay sheet-overlay os-sheet" data-overlay data-static role="presentation">'
            '<div class="sheet" role="dialog" aria-modal="true" aria-labelledby="os-ch-t">'
            '<div class="sheet-head"><div class="grow"><h2 id="os-ch-t">Choose stop</h2><span class="sub">Stops within 1 km of you</span></div>'
            '<button class="icon-btn" aria-label="Close" data-goto="onspot/main/loaded"><i data-icon="close"></i></button></div>'
            f'{body}</div></div>')


OPTS = [stop_opt("Oslo S", "83 m", ["train", "express", "bus"], current=True, goto="onspot/main/loaded"),
        stop_opt("Jernbanetorget", "240 m", ["metro", "tram", "bus"], goto="onspot/main/other-stop"),
        stop_opt("Oslo bussterminal", "310 m", ["bus"], goto="onspot/main/other-stop"),
        stop_opt("Bjørvika", "420 m", ["bus", "tram"], goto="onspot/main/other-stop"),
        stop_opt("Dronningens gate", "530 m", ["tram"], goto="onspot/main/other-stop"),
        stop_opt("Stortorvet", "610 m", ["tram", "bus"], goto="onspot/main/other-stop"),
        stop_opt("Operahuset", "740 m", ["ferry", "bus"], goto="onspot/main/other-stop")]
skel_opt = ('<li><div class="os-opt"><span class="text"><span class="skeleton line" style="width:60%;height:14px"></span>'
            '<span class="skeleton line" style="width:35%"></span></span><span class="os-check-space"></span></div></li>')
BG = lambda ov: app(header(), split(routes(), DIVIDER, stop()), foot(), overlay=ov)
BG_NONE = lambda ov: app(header(), split(routes(dest("Majorstuen", NO_STOP_ROUTES)), DIVIDER,
    '<section class="stop os-stop"><div class="panel-msg"><i data-icon="wrong_location"></i><strong>No stops within 1 km.</strong></div></section>'), foot(), overlay=ov)

ch = []
ch.append(state("loaded", "Loaded", "Tap on the stop name in the departures panel",
    BG(chooser(f'<ul class="os-opts" role="radiogroup" aria-label="Nearby stops">{"".join(OPTS)}</ul>')),
    "Nearest first, distance as a number you can compare; the current stop is checked. Choosing a stop closes the sheet at once, no confirm."))
ch.append(state("loading", "Loading", "Sheet opened before nearby stops arrived (first seconds after a fix)",
    BG(chooser(f'<ul class="os-opts" aria-busy="true">{skel_opt * 5}</ul>')),
    "Skeleton rows in the list only; the sheet header is static."))
ch.append(state("none", "No stops nearby", "Fix is more than 1 km from any stop",
    BG_NONE(chooser('<div class="panel-msg"><i data-icon="wrong_location"></i><strong>No stops within 1 km.</strong>'
                      '<span>Move closer to a road or station, or find a route with Search.</span>'
                      '<button class="btn text" data-goto="sheets/search/empty"><i data-icon="search"></i>Find a route</button></div>')),
    "Empty, not an error. Offers the one thing that still works: route search."))

html = f'''<!-- Generated by a scratch generator (gen_onspot.py). -->
<section class="v-page" data-page="onspot" data-title="On the spot" data-group="On the spot">
  <h1>On the spot</h1>
  <p class="v-lead">ON_SPOT mode follows the device. Primary question: <b>“How do I get there from here, and what leaves from the stop next to me?”</b> On a phone, routes and the nearest stop share the screen with a draggable divider; on fold, wall and desktop they sit side by side and the divider goes away.</p>
  <article class="v-screen" data-screen="main" data-title="On-spot board" data-purpose="Primary question: when should I leave for each saved destination, and what departs from the nearest stop? Tap a route for its trip, a departure for details, the stop name to choose another stop.">
{"".join(main)}  </article>
  <article class="v-screen" data-screen="chooser" data-title="Stop chooser" data-purpose="Primary action: pick which nearby stop’s departures to show. Bottom sheet on phone, side sheet on wide screens.">
{"".join(ch)}  </article>
</section>
'''
open(OUT, "w").write(html)
print("ok", len(html))
