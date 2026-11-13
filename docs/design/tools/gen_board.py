"""Generates src/pages/board.html. Edit this file and rerun: python3 tools/gen_board.py"""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "pages", "board.html")

MODE_IC = dict(bus="directions_bus", tram="tram", metro="subway", train="train", express="train", ferry="directions_boat", other="airport_shuttle")
MODE_NAME = dict(bus="Bus", tram="Tram", metro="Metro", train="Train", express="Airport express train", ferry="Ferry", other="Line")

def dep(line, mode, dest, time="", *, delay=None, cancelled=False, now=False, planned=None, sub=None, info=None, goto="sheets/departure/loaded"):
    if cancelled: t = '<span class="t-time cancelled-label">Cancelled</span>'
    elif now: t = '<span class="t-time now">Now</span>'
    elif planned: t = f'<span class="t-time"><s class="planned" aria-label="planned {planned}">{planned}</s> <span class="late-time">{time}</span></span>'
    else: t = f'<span class="t-time">{time}</span>'
    d = f'<span class="delay" aria-label="{delay} minutes late">+{delay}</span>' if delay else '<span></span>'
    s = ""
    if info: s = f'<span class="dep-sub info"><i data-icon="info" class="ic-sm"></i>{info}</span>'
    elif sub: s = f'<span class="dep-sub">{sub}</span>'
    return (f'<li><a class="dep{" is-cancelled" if cancelled else ""}" href="#" data-goto="{goto}">'
            f'<span class="pill line-{mode}" aria-label="{MODE_NAME[mode]} {line}">{line}</span><i data-icon="{MODE_IC[mode]}" class="mode"></i>'
            f'<span class="dest"><span>{dest}</span>{s}</span>{d}{t}</a></li>')

def stop(name, sub, rows=None, body=None, extra=""):
    inner = body if body is not None else f'<ul class="deps">{"".join(rows)}</ul>{extra}'
    return f'<section class="stop"><header class="s-head"><h3>{name}</h3><span class="s-sub">{sub}</span></header>{inner}</section>'

def header(weather=True, wx_now=("rainy", "8°", "Light rain")):
    hours = [("20", "rainy", "8°"), ("21", "rainy", "8°"), ("22", "cloud", "7°"), ("23", "partly_cloudy_night", "6°")]
    hrs = "".join(f'<li><span class="h">{h}</span><i data-icon="{ic}"></i><span class="tmp">{tp}</span></li>' for h, ic, tp in hours)
    wx = (f'<div class="b-wx"><div class="b-now"><i data-icon="{wx_now[0]}" class="wx"></i><span class="tmp">{wx_now[1]}</span><span class="wx-label">{wx_now[2]}</span></div>'
          f'<ol class="b-hours" aria-label="Next hours">{hrs}</ol></div>') if weather else ""
    return (f'<header class="b-head"><div class="b-clock"><span class="clock">19:37</span><span class="date"><b>Fri</b> 2 Oct</span></div>'
            f'<div class="b-actions"><button class="icon-btn" aria-label="Find a route" data-goto="sheets/search/empty"><i data-icon="search"></i></button>'
            f'<button class="icon-btn" aria-label="Settings" data-goto="settings/main/board"><i data-icon="settings"></i></button></div>{wx}</header>')

def route(dest="Oslo lufthavn", leave="19:40", legs=None, dur="28 min", arr="20:08"):
    legs = legs or '<i data-icon="directions_walk"></i>9 min<i data-icon="arrow_forward"></i><span class="pill sm line-express">FLY2</span>from Oslo S'
    return (f'<a class="route" href="#" data-goto="sheets/trip/loaded" aria-label="Fastest to {dest}: leave {leave}, {dur}, arrive {arr}">'
            f'<span class="r-leave"><span class="r-k">Leave</span><span class="r-t">{leave}</span></span>'
            f'<span class="r-body"><span class="r-title">Fastest to {dest}</span><span class="r-legs">{legs}</span></span>'
            f'<span class="r-dur"><span>{dur}</span><span class="r-arr">arr. {arr}</span></span></a>')

def foot(text='Updated 19:37 · Data from Entur · Weather from MET Norway'):
    return f'<footer class="b-foot">{text}</footer>'

JBT = [dep("1", "metro", "Frognerseteren", now=True), dep("30", "bus", "Bygdøy", "4 min", delay=3),
       dep("31", "bus", "Tonsenhagen", "6 min"), dep("2", "metro", "Ellingsrudåsen", "7 min", sub="Every ~8 min"),
       dep("17", "tram", "Rikshospitalet", "11 min", delay=1), dep("5", "metro", "Sognsvann", "19:56")]
OSLOS = [dep("L1", "train", "Asker", now=True), dep("R14", "train", "Kongsvinger", "8 min", sub="Platform 4"),
         dep("FLY2", "express", "Oslo lufthavn", "12 min", sub="Platform 13"), dep("F4", "train", "Bergen", "20:29", planned="20:25", delay=4, sub="Platform 3")]
NAT = [dep("12", "tram", "Majorstuen", "3 min"), dep("13", "tram", "Bekkestua", "5 min"), dep("81", "bus", "Myrvoll stasjon", "9 min", delay=1)]
MAJ = [dep("4", "metro", "Vestli", "2 min"), dep("19", "tram", "Ljabru", "5 min"), dep("20", "bus", "Galgeberg", "8 min")]

def board(*parts, cls=""):
    return f'<div class="app{(" " + cls) if cls else ""}"><div class="board">{"".join(parts)}</div></div>'

def stops(*s, cls=""):
    return f'<div class="stops{(" " + cls) if cls else ""}">{"".join(s)}</div>'

def state(sid, label, trigger, body, note=None):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trigger}"{n}>\n{body}\n</div>\n'

S1 = lambda rows=JBT: stop("Jernbanetorget", "Oslo · all directions", rows)
S2 = lambda rows=OSLOS: stop("Oslo S", "Platforms 2–4", rows)

skel_rows = "".join('<li><div class="dep"><div class="skeleton" style="width:44px;height:28px"></div><div class="skeleton circle" style="width:20px;height:20px"></div><div class="skeleton line" style="width:70%"></div><span></span><div class="skeleton line" style="width:48px"></div></div></li>' for _ in range(4))
skel_stop = lambda n: f'<section class="stop" aria-busy="true"><header class="s-head"><h3>{n}</h3></header><ul class="deps">{skel_rows}</ul></section>'

states = []
states.append(state("loaded", "Loaded", "Stops and a scheduled destination configured; data fresh",
    board(header(), route(), stops(S1(), S2()), foot())))
states.append(state("loading", "Loading", "First launch of the day, nothing cached yet",
    board(header(weather=False), '<div class="route is-muted" aria-busy="true"><span class="r-leave"><div class="skeleton" style="width:52px;height:36px"></div></span><span class="r-body"><span class="r-title">Finding fastest route to Oslo lufthavn…</span><div class="skeleton line" style="width:60%"></div></span><span></span></div>',
          stops(skel_stop("Jernbanetorget"), skel_stop("Oslo S")), foot("Loading departures…")),
    "Stop names are known locally, so they show at once; only the rows are skeletons. No spinner."))
states.append(state("empty", "No stops yet", "Board mode chosen, no favourite stops",
    board(header(), '<div class="empty"><svg class="art" viewBox="0 0 160 120" aria-hidden="true"><rect x="20" y="24" width="120" height="76" rx="12" style="fill:var(--color-surface-container)"/><rect x="32" y="40" width="22" height="12" rx="3" style="fill:var(--color-line-bus)"/><rect x="60" y="42" width="56" height="8" rx="4" style="fill:var(--color-outline-variant)"/><rect x="32" y="60" width="22" height="12" rx="3" style="fill:var(--color-line-metro)"/><rect x="60" y="62" width="44" height="8" rx="4" style="fill:var(--color-outline-variant)"/><rect x="32" y="80" width="22" height="12" rx="3" style="fill:var(--color-line-train)"/><rect x="60" y="82" width="50" height="8" rx="4" style="fill:var(--color-outline-variant)"/><circle cx="128" cy="26" r="16" style="fill:var(--color-primary)"/><path d="M121 26h14M128 19v14" style="stroke:var(--color-on-primary);stroke-width:3;stroke-linecap:round"/></svg><h2>Choose your stops</h2><p>Add up to 6 stops and pick which directions to show. The board keeps their departures up to date on its own.</p><button class="btn" data-goto="settings/add-stop/nearby">Add stops</button></div>', foot("Data from Entur · Weather from MET Norway")),
    "Weather and clock still work, so the board is useful from minute one."))
states.append(state("no-departures", "Stop with no departures", "Late night: one stop has nothing in the next 2 hours",
    board(header(wx_now=("partly_cloudy_night", "3°", "Partly cloudy")), stops(stop("Snarøya", "Bærum", body='<div class="panel-msg"><i data-icon="schedule"></i><strong>No upcoming departures</strong><span>Nothing leaves in the next 2 hours.</span><button class="btn text">Search further ahead</button></div>'), S2(OSLOS[:2])), foot())))
states.append(state("refreshing", "Refreshing", "Pull to refresh on a phone, or the app comes back to the foreground with old data",
    board('<div class="progress top" role="progressbar" aria-label="Updating departures"></div>', header(), route(), stops(S1(), S2()), foot("Updating…")),
    "Thin bar only; content stays. Automatic refreshes on the wall show no indicator at all."))
states.append(state("stale", "Offline (old data)", "No internet for more than 2 refresh intervals; cache available",
    board(header(), '<div class="callout warning" role="status"><i data-icon="wifi_off"></i><div class="body"><strong>No internet</strong>Shown data is 12 min old. Updates as soon as you’re back online.</div></div>', route(), stops(S1(JBT[2:]), S2(OSLOS[1:])), foot('<span class="warn"><i data-icon="wifi_off" class="ic-sm"></i>Updated 19:25</span> · Data from Entur')),
    "T4: a lasting condition with useful data still shown. Departures already passed are dropped from the cached list, so the countdowns stay true."))
states.append(state("entur-down", "Can’t reach Entur (old data)", "Internet works but Entur doesn’t answer; cache available",
    board(header(), '<div class="callout error" role="status"><i data-icon="cloud_off"></i><div class="body"><strong>Can’t reach Entur</strong>Shown data is 6 min old. Retrying automatically.</div><button class="btn text">Retry</button></div>', route(), stops(S1(JBT[1:]), S2(OSLOS[1:])), foot('<span class="warn"><i data-icon="cloud_off" class="ic-sm"></i>Updated 19:31</span> · Data from Entur')),
    "T4. Different cause, different copy: the user can’t fix Entur, so we say we’re retrying."))
states.append(state("no-cache", "Can’t load anything", "Offline on first launch, nothing cached",
    board(header(weather=False), stops(*[stop(n, s, body='<div class="panel-msg" role="status"><i data-icon="wifi_off"></i><strong>No internet</strong><span>Departures show as soon as you’re online.</span></div>') for n, s in [("Jernbanetorget", "Oslo · all directions"), ("Oslo S", "Platforms 2–4")]]), foot("Data from Entur")),
    "T5 inside each panel, not over the whole board: the clock keeps running and the stop names stay, so the wall still looks intentional."))
states.append(state("partial", "One stop failed", "Entur returns an error for one stop only",
    board(header(), route(), stops(S1(), stop("Oslo S", "Platforms 2–4", body='<div class="panel-msg" role="status"><i data-icon="cloud_off"></i><strong>Couldn’t load departures</strong><span>Retrying automatically.</span></div>')), foot()),
    "Partial failure stays inside its panel."))
states.append(state("route-unavailable", "Route unavailable", "Trip search fails for a scheduled destination",
    board(header(), '<div class="route is-muted" role="status"><span class="r-leave"><i data-icon="cloud_off" class="ic-lg"></i></span><span class="r-body"><span class="r-title">Fastest route to Oslo lufthavn unavailable</span><span class="r-legs">Retrying automatically</span></span><span></span></div>', stops(S1(), S2()), foot()),
    "T4 in the banner’s own slot, so the layout doesn’t jump when it comes back."))
states.append(state("no-route-by", "No route by arrival time", "Destination scheduled with “arrive by 08:30”; nothing makes it",
    board(header(), '<div class="route is-muted" role="status"><span class="r-leave"><i data-icon="schedule" class="ic-lg"></i></span><span class="r-body"><span class="r-title">No route gets you to Fornebu by 08:30</span><span class="r-legs">The next one arrives 08:41</span></span><span></span></div>', stops(S1(), S2()), foot())))
states.append(state("update", "Update available", "A new version is in the store",
    board(header(), '<div class="callout info" role="status"><i data-icon="system_update"></i><div class="body"><strong>A new version of PulseTransit is available</strong></div><button class="btn text">Not now</button><button class="btn text">Update</button></div>', route(), stops(S1(), S2()), foot()),
    "T4, dismissible: it doesn’t affect what the board shows. Hidden again after “Not now” until the next version."))
six = [S1(JBT[:3]), S2(OSLOS[:3]), stop("Nationaltheatret", "Oslo", NAT), stop("Majorstuen", "Oslo · towards city centre", MAJ)]
states.append(state("paging", "6 stops, paging", "More stops than fit; the board takes turns",
    board(header(), route(), stops(*six), '<div class="page-dots" role="status" aria-label="Page 1 of 2. The next page shows in 12 seconds."><i class="dot on"></i><i class="dot"></i><span>Stops 1–4 of 6</span></div>', foot()),
    "Stops that don’t fit rotate every 20 s. Tapping the dots pauses rotation (“Page 1 of 2. Paused.”). On a phone the board scrolls instead."))
states.append(state("two-routes", "Two destinations", "Two destinations scheduled for this time",
    board(header(), '<div class="routes">' + route() + route("Fornebu by 08:30", "07:52", '<span class="pill sm line-metro">3</span><i data-icon="arrow_forward"></i><span class="pill sm line-bus">31</span>', "34 min", "08:26") + '</div>', stops(S1(), S2()), foot())))
states.append(state("dimmed", "Dimmed (no one around)", "No motion for the auto-dim delay, or 00–05",
    board(header(), route(), stops(S1(), S2()), foot(), cls="dimmed").replace('</div></div>', '<span class="dim-note">Dimmed · wakes on motion</span></div></div>', 1),
    "Whole board at ~55% brightness; on OLED black that keeps text readable from the sofa without lighting the room. Refresh slows ×2. Pixel shift moves everything by up to 6 px every few minutes (see Shell)."))
long_rows = [dep("81", "bus", "Filipstad via Aker brygge og Tjuvholmen", cancelled=True), dep("RE30", "train", "Gjøvik", "20:58", planned="20:31", delay=27, info="Replacement bus from Jaren"),
             dep("FLY24", "express", "Oslo lufthavn via Lillestrøm stasjon og Kjeller", "14 min"), dep("110", "bus", "Kampen", "15 min", sub="Every ~5 min · +4 more"),
             dep("N12", "bus", "Kolsås", "00:47"), dep("B9", "ferry", "Nesoddtangen", "13 min")]
states.append(state("stress", "Long content", "Long names, big delays, cancellations, messages",
    board(header(wx_now=("weather_snowy", "−12°", "Heavy snow")), route("Universitetssykehuset Nord-Norge, Breivika", "19:41", '<i data-icon="directions_walk"></i>12 min<i data-icon="arrow_forward"></i><span class="pill sm line-train">R10</span><i data-icon="arrow_forward"></i><span class="pill sm line-express">FLY2</span><i data-icon="arrow_forward"></i><span class="pill sm line-bus">42</span>', "5 h 12 min", "00:53"),
          stops(stop("Jernbanetorget (Oslo bussterminal, plattform A–F)", "Oslo · 3 of 7 directions", long_rows), S2(OSLOS[:2])), foot()),
    "Destinations truncate to one line (full text in details). Route titles wrap to 2 lines. Line pills never truncate. Negative temperatures use a true minus."))
states.append(state("filtered", "Line hidden (snackbar)", "User filtered out line 31 from departure details",
    board(header(), route(), stops(S1([r for r in JBT if '>31<' not in r]), S2()), foot(), '<div class="snackbar-host"><div class="snackbar" role="status"><span class="msg">Line 31 hidden from departures</span><button class="btn text" data-state-to="loaded">Undo</button></div></div>'),
    "T3 with Undo instead of a confirmation: hiding is reversible (Settings › Filtered lines)."))

page = f'''<!-- Generated by tools/gen_board.py. Edit the generator, not this file. -->
<section class="v-page" data-page="board" data-title="Wall board" data-group="Board">
  <h1>Wall board</h1>
  <p class="v-lead">The always-on board in BOARD mode. Primary question: <b>“When does my next ride leave?”</b> Read from 1–2 m: countdowns are the largest text after the clock, and everything else steps back. Switch viewport to <b>Wall</b> for the landscape tablet it’s made for; <b>Fold</b> shows a foldable, <b>Phone</b> a phone used as a board.</p>
  <article class="v-screen" data-screen="main" data-title="Board" data-purpose="Primary question: when does my next ride leave? Tap a departure for details, tap the route banner for the trip. No primary button: the board is read, not operated.">
{"".join(states)}  </article>
</section>
'''
open(OUT, "w").write(page)
print("wrote", OUT, len(states), "states")
