"""Generates docs/design/src/pages/sheets.html. Edit this file and rerun: python3 tools/gen_sheets.py"""
import os; ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")  # run: python3 tools/gen_sheets.py
OUT = os.path.join(ROOT, "src", "pages", "sheets.html")

line7 = open(os.path.join(ROOT, "src", "pages", "board.html")).read().split("\n")[6]
assert line7.startswith('<div class="app"><div class="board">')
BOARD = line7[len('<div class="app">'):-len('</div>')]
BG = f'<div class="sheet-bg" aria-hidden="true" inert>{BOARD}</div>'

UID = [0]
def uid(p):
    UID[0] += 1
    return f"{p}{UID[0]}"

def state(sid, label, trigger, body, note=None):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trigger}"{n}>\n<div class="app">{body}</div>\n</div>'

def sheet(inner, label_id, cls="", extra_after=""):
    return (f'{BG}<div class="overlay sheet-overlay side" data-overlay data-static role="presentation">'
            f'<div class="sheet ps-sheet {cls}" role="dialog" aria-modal="true" aria-labelledby="{label_id}">{inner}</div></div>{extra_after}')

def close_btn(goto="board/main/loaded"):
    return f'<button class="icon-btn" aria-label="Close" data-goto="{goto}"><i data-icon="close"></i></button>'

def snack(msg, action="Undo"):
    return (f'<div class="snackbar-host ps-snack"><div class="snackbar" role="status"><span class="msg">{msg}</span>'
            f'<button class="btn text" data-state-to="results">{action}</button></div></div>')

# ---------------------------------------------------------------- departure
STOPS31 = [("19:43", "Jernbanetorget"), ("19:45", "Brugata"), ("19:46", "Hausmanns gate"), ("19:49", "Carl Berners plass"),
           ("19:51", "Sinsen"), ("19:54", "Lofthus"), ("19:57", "Tonsenhagen")]

def stoplist(stops, rail="rail-bus"):
    out = []
    for i, (t, n) in enumerate(stops):
        cls = ["tl", rail]
        if i == 0: cls += ["first", "here"]
        if i == len(stops) - 1: cls.append("last")
        sub = '<span>Platform B · you board here</span>' if i == 0 else ('<span>Last stop</span>' if i == len(stops) - 1 else "")
        out.append(f'<li class="{" ".join(cls)}"><span class="tm">{t}</span><span class="rail"><i class="dot"></i></span>'
                   f'<span class="what"><b>{n}</b>{sub}</span><span></span></li>')
    return f'<ol class="timeline ps-stops">{"".join(out)}</ol>'

def stoplist_skeleton():
    rows = "".join(f'<li class="tl{" first" if i == 0 else ""}{" last" if i == 4 else ""}"><span class="tm"><span class="skeleton line" style="width:40px;margin-left:auto"></span></span>'
                   f'<span class="rail"><i class="dot"></i></span><span class="what"><span class="skeleton line" style="width:{w}%"></span></span><span></span></li>'
                   for i, w in enumerate([55, 40, 62, 48, 50]))
    return f'<ol class="timeline ps-stops" aria-busy="true" aria-label="Loading stops">{rows}</ol>'

def times_row(times, sel=0, cancelled_idx=None):
    chips = []
    for i, (t, s) in enumerate(times):
        c = "tchip" + (" is-selected" if i == sel else "") + (" is-cancelled" if i == cancelled_idx else "")
        tt = f"<s>{t}</s>" if i == cancelled_idx else t
        if i == cancelled_idx: s = "Cancelled"
        chips.append(f'<button class="{c}" aria-pressed="{"true" if i == sel else "false"}">{tt}<small>{s}</small></button>')
    return f'<div class="times-row" role="group" aria-label="Departures">{"".join(chips)}</div>'

TIMES31 = [("19:43", "in 6 min"), ("19:53", "in 16 min"), ("20:03", "in 26 min"), ("20:13", "in 36 min"), ("20:23", "in 46 min"), ("20:33", "in 56 min")]

def dep_head(hid, line="31", mode="bus", dest="Tonsenhagen", sub="From Jernbanetorget · Platform B", cancelled=False):
    pill_cls = f"pill line-{mode}" + (" ps-faded" if cancelled else "")
    return (f'<div class="sheet-head"><span class="{pill_cls}" aria-label="Bus {line}">{line}</span><div class="grow">'
            f'<h2 id="{hid}" class="ps-wrap">{dest}</h2><span class="sub">{sub}</span></div>{close_btn()}</div>')

def dep_when(count="6 min", clock="19:43", live=True, every="Every ~10 min", cancelled=False):
    if cancelled:
        return (f'<div class="ps-when"><span class="ps-count cancelled-label">Cancelled</span><span class="ps-when-meta">'
                f'<s class="planned">{clock}</s><span class="muted">{every}</span></span></div>')
    lv = '<span class="live"><i data-icon="sensors" class="ic-sm"></i>Live</span>' if live else ""
    return (f'<div class="ps-when"><span class="ps-count">{count}</span><span class="ps-when-meta">'
            f'<span class="row"><b class="num">{clock}</b>{lv}</span><span class="muted">{every}</span></span></div>')

def filter_btn(line="31"):
    return (f'<div class="actions-row"><button class="btn outline" data-goto="board/main/filtered"><i data-icon="visibility_off"></i>Filter out line {line}</button></div>'
            f'<p class="ps-hint">Hidden from the departures and avoided in route searches. Show it again any time in Settings › Filtered out lines.</p>')

def departure(variant):
    hid = uid("dd-t")
    if variant == "long":
        stops = [("19:44", "Jernbanetorget"), ("19:47", "Kontraskjæret"), ("19:50", "Rådhusplassen / Oslo rådhus"),
                 ("19:53", "Aker brygge"), ("19:55", "Tjuvholmen / Astrup Fearnley Museet"), ("19:58", "Filipstad (Kaiområdet ved Color Line-terminalen)")]
        body = (dep_head(hid, "32", "bus", "Filipstad via Aker brygge og Tjuvholmen", "From Jernbanetorget · Platform E")
                + dep_when("7 min", "19:44", True, "Every ~15 min")
                + '<div class="section-label">Departures</div>' + times_row([("19:44", "in 7 min"), ("19:59", "in 22 min"), ("20:14", "in 37 min"), ("20:29", "in 52 min")])
                + '<div class="section-label">Stops for the 19:44 departure</div><p class="ps-ride"><i data-icon="schedule" class="ic-sm"></i>14 min to Filipstad (Kaiområdet ved Color Line-terminalen)</p>'
                + stoplist(stops).replace("Platform B", "Platform E") + filter_btn("32"))
        return sheet(body, hid)
    cancelled = variant == "cancelled"
    body = dep_head(hid, cancelled=cancelled)
    body += dep_when(cancelled=cancelled)
    if cancelled:
        body += ('<div class="callout error" role="status"><i data-icon="cancel"></i><div class="body"><strong>This departure won’t run</strong>'
                 'The operator cancelled the 19:43. The next one leaves at 19:53, in 16 min.</div></div>')
    body += '<div class="section-label">Departures</div>' + times_row(TIMES31, 0, 0 if cancelled else None)
    if cancelled:
        body += ('<div class="actions-row"><button class="btn secondary" data-state-to="loaded"><i data-icon="arrow_forward"></i>Show the 19:53 departure</button></div>')
    else:
        body += '<div class="section-label">Stops for the 19:43 departure</div>'
        if variant == "loaded":
            body += '<p class="ps-ride"><i data-icon="schedule" class="ic-sm"></i>14 min to Tonsenhagen</p>' + stoplist(STOPS31)
        elif variant == "loading-stops":
            body += stoplist_skeleton()
        elif variant == "stops-unavailable":
            body += ('<div class="callout info" role="status"><i data-icon="info"></i><div class="body"><strong>No stop list for this departure</strong>'
                     'Entur didn’t send its stops. The times above are still live.</div>'
                     '<button class="btn text" data-state-to="loading-stops">Try again</button></div>')
    body += filter_btn()
    return sheet(body, hid)

# ---------------------------------------------------------------- trip
def tl(time, what, *, cls="", tail="<span></span>", small=""):
    sm = f"<small>{small}</small>" if small else ""
    return f'<li class="tl {cls}"><span class="tm">{time}{sm}</span><span class="rail"><i class="dot"></i></span><span class="what">{what}</span>{tail}</li>'

LIVE = '<span><span class="live"><i data-icon="sensors" class="ic-sm"></i>Live</span></span>'
MAP = '<button class="btn text ps-inline">Show on map</button>'

def trip_head(hid, title, sub, summary, goto="board/main/loaded"):
    return (f'<div class="sheet-head"><div class="grow"><h2 id="{hid}">{title}</h2><span class="sub">{sub}</span></div>{close_btn(goto)}</div>'
            f'<div class="leg-summary ps-legsum">{summary}</div>')

AIR_SUM = ('<i data-icon="directions_walk"></i>9 min<i data-icon="chevron_right"></i><span class="pill sm line-express">FLY2</span>'
           '<span class="ps-dot">·</span>Direct<span class="ps-dot">·</span>650 m walking')

def trip_air(variant):
    hid = uid("tr-t")
    following = variant in ("following", "underway", "location-lost")
    riding = variant in ("underway", "location-lost")
    expanded = variant in ("stops-expanded", "underway", "location-lost")
    top = ""
    if variant == "following":
        top = ('<div class="callout info ps-follow" role="status"><i data-icon="notifications_active"></i><div class="body">'
               '<strong>Following your journey to Oslo lufthavn</strong>Walking to Oslo S · FLY2 leaves 19:52 from platform 13</div></div>')
    elif variant == "underway":
        top = ('<div class="callout info ps-follow" role="status"><i data-icon="notifications_active"></i><div class="body">'
               '<strong>Following your journey to Oslo lufthavn</strong>On FLY2 to Oslo lufthavn · Next stop: Lillestrøm · Arrives 20:08</div></div>')
    elif variant == "location-lost":
        top = ('<div class="callout warning" role="status"><i data-icon="location_off"></i><div class="body">'
               '<strong>Location off, following times only</strong>Alerts keep coming from the live times, but a missed ride can’t be noticed.</div>'
               '<button class="btn text">Turn on</button></div>')
    walk_cls = "first walk" + (" past" if riding else "") + (" current" if variant == "following" else "")
    walk_what = ('<b>Torggata 12</b><span class="row"><i data-icon="directions_walk" class="ic-sm"></i>Walk 9 min to Oslo S' + MAP + '</span>')
    if variant == "following":
        walk_what += '<span class="ps-now"><i data-icon="near_me" class="ic-sm"></i>You’re here · 6 min to go</span>'
    rows = [tl("19:40", walk_what, cls=walk_cls)]
    board_cls = "rail-express" + (" past" if riding else "")
    show_btn = (f'<button class="btn text ps-inline" data-state-to="{"loaded" if expanded else "stops-expanded"}" aria-expanded="{"true" if expanded else "false"}">'
                f'<i data-icon="{"expand_less" if expanded else "expand_more"}" class="ic-sm"></i>{"Hide stops" if expanded else "Show stops"}</button>')
    board_what = ('<b>Oslo S</b><span class="ps-line"><span class="pill sm line-express">FLY2</span> towards Oslo lufthavn · Platform 13</span>'
                  f'<span class="row">16 min · 1 stop {show_btn}</span>')
    rows.append(tl("19:52", board_what, cls=board_cls, tail=LIVE, small="Wait 3 min" if not riding else ""))
    if expanded:
        mid_cls = "rail-express" + (" current next" if riding else "")
        mid_what = '<b>Lillestrøm</b>' + ('<span class="ps-now"><i data-icon="train" class="ic-sm"></i>Next stop · in 4 min</span>' if riding else '')
        rows.append(tl("20:00", mid_what, cls=mid_cls))
    rows.append(tl("20:08", '<b>Oslo lufthavn</b><span>Arrive</span>', cls="last rail-express"))
    body = trip_head(hid, "To Oslo lufthavn", "Leave 19:40 · 28 min · arr. 20:08", AIR_SUM) + top
    body += f'<ol class="timeline ps-trip">{"".join(rows)}</ol>'
    if following:
        body += '<div class="sheet-foot"><button class="btn outline block" data-state-to="loaded"><i data-icon="notifications_off"></i>Stop following</button></div>'
    else:
        body += '<div class="sheet-foot"><button class="btn block" data-goto="journeys/rationale/android"><i data-icon="notifications"></i>Notify me about this journey</button></div>'
    return sheet(body, hid)

def trip_changes():
    hid = uid("tr-t")
    summary = ('<i data-icon="directions_walk"></i>3 min<i data-icon="chevron_right"></i><span class="pill sm line-metro">3</span>'
               '<i data-icon="chevron_right"></i><span class="pill sm line-bus">31</span><i data-icon="chevron_right"></i>'
               '<span class="pill sm line-bus">31E</span><span class="ps-dot">·</span>2 changes<span class="ps-dot">·</span>420 m walking')
    rows = [
        tl("19:39", '<b>Torggata 12</b><span class="row"><i data-icon="directions_walk" class="ic-sm"></i>Walk 3 min to Jernbanetorget' + MAP + '</span>', cls="first walk"),
        tl("19:42", '<b>Jernbanetorget</b><span class="ps-line"><span class="pill sm line-metro">3</span> towards Kolsås · Platform 2</span>'
           '<span class="row">7 min · 3 stops <button class="btn text ps-inline" aria-expanded="false"><i data-icon="expand_more" class="ic-sm"></i>Show stops</button></span>', cls="rail-metro", tail=LIVE),
        tl("19:49", '<b>Majorstuen</b><span class="row"><i data-icon="hourglass_top" class="ic-sm"></i>Wait 4 min</span>', cls="rail-metro ps-change"),
        tl("19:53", '<b>Majorstuen</b><span class="ps-line"><span class="pill sm line-bus">31</span> towards Snarøya · Platform D</span>'
           '<span class="row">9 min · 5 stops <button class="btn text ps-inline" aria-expanded="false"><i data-icon="expand_more" class="ic-sm"></i>Show stops</button></span>', cls="rail-bus", tail=LIVE),
        tl("20:02", '<b>Lysaker stasjon</b><span class="row"><i data-icon="hourglass_top" class="ic-sm"></i>Wait 2 min</span>', cls="rail-bus ps-change"),
        tl("20:04", '<b>Lysaker stasjon</b><span class="ps-line"><span class="pill sm line-bus">31E</span> towards Fornebu · Platform A</span>'
           '<span class="row">8 min · 4 stops <button class="btn text ps-inline" aria-expanded="false"><i data-icon="expand_more" class="ic-sm"></i>Show stops</button></span>', cls="rail-bus"),
        tl("20:12", '<b>Fornebu senter</b><span>Arrive</span>', cls="last rail-bus"),
    ]
    body = trip_head(hid, "To Fornebu", "Leave 19:39 · 33 min · arr. 20:12", summary)
    body += f'<ol class="timeline ps-trip">{"".join(rows)}</ol>'
    body += '<div class="sheet-foot"><button class="btn block" data-goto="journeys/rationale/android"><i data-icon="notifications"></i>Notify me about this journey</button></div>'
    return sheet(body, hid)

def trip_walk():
    hid = uid("tr-t")
    summary = '<i data-icon="directions_walk"></i>Walk all the way<span class="ps-dot">·</span>1.2 km'
    rows = [
        tl("19:38", '<b>Torggata 12</b><span class="row"><i data-icon="directions_walk" class="ic-sm"></i>Walk 15 min to Nationaltheatret' + MAP + '</span>'
           '<span>Faster than any ride right now: the next tram gets you there at 19:56.</span>', cls="first walk"),
        tl("19:53", '<b>Nationaltheatret</b><span>Arrive</span>', cls="last walk"),
    ]
    body = trip_head(hid, "To Nationaltheatret", "Leave 19:38 · 15 min · arr. 19:53", summary)
    body += f'<ol class="timeline ps-trip">{"".join(rows)}</ol>'
    body += '<div class="sheet-foot"><button class="btn block" data-goto="journeys/rationale/android"><i data-icon="notifications"></i>Notify me about this journey</button></div>'
    return sheet(body, hid)

# ---------------------------------------------------------------- search
SAVED = [("flight", "Oslo lufthavn", "Gardermoen · airport"), ("work", "Fornebu", "Bærum · Work"), ("train", "Lillestrøm stasjon", "Lillestrøm · train station")]

def search_field(value="", busy=False, fid=None, describedby=None, autofocus=False):
    fid = fid or uid("q")
    val = f' value="{value}"' if value else ""
    clear = '<button class="icon-btn ps-clear" aria-label="Clear" data-state-to="empty"><i data-icon="cancel"></i></button>' if value else ""
    return (f'<div class="field ps-search"><label for="{fid}" class="ps-sr">To</label><div class="control"><i data-icon="search" class="muted"></i>'
            f'<input id="{fid}" type="search" placeholder="Stop, address or place…" autocomplete="off"{val}>{clear}</div></div>')

def time_chip(label="Leave now", is_set=False):
    sel = ' aria-pressed="true"' if is_set else ""
    return (f'<div class="ps-chips"><button class="chip"{sel} data-goto="sheets/time/{"tomorrow" if is_set else "leave-now"}" aria-label="Change travel time: {label}">'
            f'<i data-icon="schedule" class="ic-sm"></i>{label}<i data-icon="arrow_drop_down" class="ic-sm"></i></button></div>')

def search_head(hid):
    return f'<div class="sheet-head"><div class="grow"><h2 id="{hid}">Find a route</h2><span class="sub">From Torggata 12 (the board)</span></div>{close_btn()}</div>'

def place_item(icon, name, sub, goto=None, state_to=None, mark=None):
    if mark:
        name = name.replace(mark, f"<mark>{mark}</mark>", 1)
    act = f' data-goto="{goto}"' if goto else (f' data-state-to="{state_to}"' if state_to else "")
    return (f'<li><a class="list-item ps-place" href="#"{act}><span class="ps-pic"><i data-icon="{icon}"></i></span>'
            f'<span class="text"><span class="truncate">{name}</span><span class="truncate">{sub}</span></span></a></li>')

def trip_row(leave, rel, legs, dur, arr, goto="sheets/trip/loaded"):
    return (f'<li><a class="trip" href="#" data-goto="{goto}" aria-label="Leave {leave}, {dur}, arrive {arr}">'
            f'<span class="tt">{leave}<small>{rel}</small></span><span class="leg-summary">{legs}</span>'
            f'<span class="dur">{dur}<small>arr. {arr}</small></span></a></li>')

W = lambda m: f'<i data-icon="directions_walk"></i>{m}'
A = '<i data-icon="chevron_right"></i>'
P = lambda cls, n: f'<span class="pill sm line-{cls}">{n}</span>'

MAJ_TRIPS = [
    ("19:41", "in 4 min", W(3) + A + P("metro", "2") + A + W(1), "11 min", "19:52"),
    ("19:44", "in 7 min", W(3) + A + P("metro", "3"), "10 min", "19:54"),
    ("19:46", "in 9 min", W(1) + A + P("tram", "19") + A + P("metro", "5"), "13 min", "19:59"),
    ("19:49", "in 12 min", W(3) + A + P("metro", "1"), "10 min", "19:59"),
]
TMR_TRIPS = [
    ("08:15", "tomorrow", W(3) + A + P("metro", "2"), "11 min", "08:26"),
    ("08:18", "tomorrow", W(3) + A + P("metro", "3"), "10 min", "08:28"),
    ("08:21", "tomorrow", W(1) + A + P("tram", "19") + A + P("metro", "5"), "13 min", "08:34"),
]

def results_block(trips, saved=False, title="To Majorstuen"):
    rows = "".join(trip_row(*t) for t in trips)
    save = ('<button class="btn secondary" aria-pressed="true" data-state-to="results"><i data-icon="bookmark_added" class="ic-fill"></i>Saved</button>' if saved
            else '<button class="btn outline" data-state-to="saved"><i data-icon="bookmark_add"></i>Save destination</button>')
    return (f'<div class="ps-res-head"><span class="section-label">{title}</span></div><ul class="trips" aria-label="Route alternatives">{rows}</ul>'
            f'<div class="actions-row">{save}</div>')

def search(variant):
    hid = uid("sr-t")
    body = search_head(hid)
    snackbar = ""
    if variant == "empty":
        body += search_field() + time_chip()
        body += ('<div class="section-label">Saved destinations</div><ul class="list ps-places">'
                 + "".join(place_item(i, n, s, state_to="searching") for i, n, s in SAVED) + '</ul>'
                 '<div class="section-label">Recent</div><ul class="list ps-places">'
                 + place_item("history", "Nationaltheatret", "Oslo · stop", state_to="searching") + '</ul>')
    elif variant == "typing":
        body += search_field("Majorst")
        items = [("subway", "Majorstuen", "Oslo · stop · metro, tram, bus"), ("location_on", "Majorstuveien 12", "Oslo · address"),
                 ("school", "Majorstuen skole", "Oslo · place"), ("church", "Majorstuen kirke", "Oslo · place"),
                 ("directions_bus", "Majorstuen (Kirkeveien)", "Oslo · stop · bus")]
        body += ('<ul class="list ps-places" aria-label="Places">' + "".join(place_item(i, n, s, state_to="results", mark="Majorst") for i, n, s in items) + '</ul>')
    elif variant == "no-places":
        body += search_field("Xyzzy")
        body += ('<div class="empty ps-empty"><i data-icon="search_off" class="ic-lg muted"></i><h2>No places found</h2>'
                 '<p>Nothing matches “Xyzzy”. Check the spelling, or try a stop, street or place name.</p></div>')
    elif variant == "searching":
        body += search_field("Majorstuen") + time_chip()
        sk = "".join('<li><div class="trip"><span class="skeleton" style="width:52px;height:30px"></span><span class="skeleton line" style="width:70%"></span>'
                     '<span class="skeleton" style="width:48px;height:30px"></span></div></li>' for _ in range(4))
        body += f'<div class="ps-res-head"><span class="section-label">To Majorstuen</span></div><ul class="trips" aria-busy="true" aria-label="Finding routes">{sk}</ul>'
    elif variant in ("results", "saved"):
        body += search_field("Majorstuen") + time_chip() + results_block(MAJ_TRIPS, saved=variant == "saved")
        if variant == "saved":
            snackbar = snack("Saved Majorstuen to destinations")
    elif variant == "time-set":
        body += search_field("Majorstuen") + time_chip("Leave 08:15 tomorrow", True) + results_block(TMR_TRIPS)
    elif variant == "no-route":
        body += search_field("Kirkenes") + time_chip()
        body += ('<div class="screen-error ps-empty"><i data-icon="wrong_location" class="ic-lg muted"></i><h2>No route to Kirkenes found</h2>'
                 '<p>Entur has no public transport there leaving now. Try a later time, or a place nearby.</p>'
                 '<button class="btn" data-goto="sheets/time/depart-at"><i data-icon="schedule"></i>Change travel time</button></div>')
    elif variant == "no-route-by":
        body += search_field("Fornebu") + time_chip("Arrive by 08:30", True).replace("sheets/time/tomorrow", "sheets/time/arrive-by")
        body += ('<div class="callout warning" role="status"><i data-icon="event_busy"></i><div class="body"><strong>No route gets you to Fornebu by 08:30</strong>'
                 'The earliest you can arrive is 08:41, if you leave at 08:04.</div></div>'
                 '<div class="ps-res-head"><span class="section-label">Earliest arrival</span></div><ul class="trips">'
                 + trip_row("08:04", "tomorrow", W(3) + A + P("metro", "3") + A + P("bus", "31E"), "37 min", "08:41")
                 + '</ul><div class="actions-row"><button class="btn outline" data-goto="sheets/time/arrive-by"><i data-icon="schedule"></i>Change travel time</button></div>')
    elif variant == "offline":
        body += search_field("Majorstuen") + time_chip()
        body += ('<div class="screen-error ps-empty"><i data-icon="wifi_off" class="ic-lg muted"></i><h2>No internet</h2>'
                 '<p>Route search needs a connection. Your saved destinations keep showing on the board from the last update.</p>'
                 '<button class="btn" data-state-to="searching">Try again</button></div>')
    return sheet(body, hid, "tall", snackbar)

# ---------------------------------------------------------------- time picker
def timepick(variant):
    hid = uid("tp-t")
    modes = [("leave-now", "Leave now"), ("depart-at", "Depart at"), ("arrive-by", "Arrive by")]
    cur = "depart-at" if variant == "tomorrow" else variant
    seg = "".join(f'<button aria-pressed="{"true" if k == cur else "false"}" data-state-to="{k}">{l}</button>' for k, l in modes)
    body = (f'<div class="sheet-head"><div class="grow"><h2 id="{hid}">When to travel</h2><span class="sub">To Majorstuen</span></div>'
            f'{close_btn("sheets/search/results")}</div>'
            f'<div class="segmented ps-seg" role="group" aria-label="Travel time">{seg}</div>')
    if variant != "leave-now":
        tmr = variant == "tomorrow"
        fid = uid("tm")
        body += ('<div class="ps-when-pick">'
                 f'<div class="ps-chips" role="group" aria-label="Day"><button class="chip" aria-pressed="{"false" if tmr else "true"}" data-state-to="{"depart-at" if tmr else variant}">Today</button>'
                 f'<button class="chip" aria-pressed="{"true" if tmr else "false"}" data-state-to="tomorrow">Tomorrow</button></div>'
                 f'<div class="field ps-time"><label for="{fid}">{"Arrive by" if variant == "arrive-by" else "Leave at"}</label><div class="control">'
                 f'<input id="{fid}" type="time" value="08:15" class="num"></div></div></div>')
    summary = {"leave-now": "Leave now", "depart-at": "Leave 08:15", "arrive-by": "Arrive by 08:15", "tomorrow": "Leave 08:15 tomorrow"}[variant]
    hint = {"leave-now": "Routes leaving from 19:37, with live times.",
            "depart-at": "Routes leaving at 08:15 today or later.",
            "arrive-by": "The latest routes that still get you there by 08:15.",
            "tomorrow": "Routes leaving at 08:15 on Sat 3 Oct or later."}[variant]
    done = "sheets/search/time-set" if variant == "tomorrow" else "sheets/search/results"
    body += (f'<div class="ps-summary" role="status"><i data-icon="event" class="muted"></i><div><b>{summary}</b><span>{hint}</span></div></div>'
             f'<div class="sheet-foot"><button class="btn block" data-goto="{done}">Done</button></div>')
    return sheet(body, hid, "ps-time-sheet")

# ---------------------------------------------------------------- page
P_OUT = []
P_OUT.append('<!-- Generated by a scratch generator (sheets). Edit by hand or regenerate. -->')
P_OUT.append('<section class="v-page" data-page="sheets" data-title="Details &amp; route search" data-group="Board">')
P_OUT.append('<h1>Details &amp; route search</h1>')
P_OUT.append('<p class="v-lead">Everything that opens on top of the board: a departure’s details, a trip’s steps, a one-off route search and its travel time. '
             'Bottom sheets on phones; on the wall, fold and desktop they slide in from the right (max 440 px) so the board stays readable beside them.</p>')

def screen(sid, title, purpose, states):
    P_OUT.append(f'<article class="v-screen" data-screen="{sid}" data-title="{title}" data-purpose="{purpose}">')
    P_OUT.extend(states)
    P_OUT.append('</article>')

screen("departure", "Departure details", "Primary question: when does this ride leave and where does it go? Secondary action: filter out the line.", [
    state("loaded", "Loaded", "Tap a departure row on the board", departure("loaded")),
    state("loading-stops", "Loading stops", "Sheet opens before Entur returns the stop list", departure("loading-stops"),
          "Times are already known from the board, so only the stop list is a skeleton."),
    state("stops-unavailable", "Stop list unavailable", "Entur returns no stops for this service journey", departure("stops-unavailable"),
          "T4 inside the stop-list section only: the rest of the sheet is fine, and the condition lasts for this departure."),
    state("cancelled", "Cancelled", "The selected departure is cancelled", departure("cancelled"),
          "T4 in the sheet (role=status): a lasting fact about this departure, with one action to the next one."),
    state("long", "Long destination", "Destination and stop names far longer than usual", departure("long"),
          "Details are where truncated board text is read in full, so the title and stops wrap; nothing is cut."),
])
screen("trip", "Trip details", "Primary question: how do I get there, step by step? Primary action: Notify me about this journey.", [
    state("loaded", "Loaded", "Tap the fastest-route banner or a route result", trip_air("loaded")),
    state("stops-expanded", "Stops expanded", "Tap Show stops on a ride leg", trip_air("stops-expanded")),
    state("changes", "2 changes", "Route with transfers", trip_changes(), "Waits sit on the rail between rides, at the stop where you change."),
    state("walk-only", "Walk only", "Walking beats every ride", trip_walk()),
    state("following", "Following", "User turned on journey alerts", trip_air("following"),
          "Progress is shown on the leg you are on; the primary button becomes Stop following."),
    state("underway", "Under way", "Riding FLY2, location shows the train past Oslo S", trip_air("underway"),
          "Past legs dim; the next stop fills and gets a label."),
    state("location-lost", "Location lost", "Location turned off or lost mid-journey", trip_air("location-lost"),
          "T4: a lasting condition that reduces what alerts can do, with one action. Not T6, because the journey still works on live times."),
])
screen("search", "Route search", "Primary question: how do I get to a place I don’t have saved? Primary action: pick a route.", [
    state("empty", "Empty", "Tap Find a route on the board", search("empty")),
    state("typing", "Typing", "User types “Majorst”", search("typing"), "Stops, addresses and places each get their own icon; the matched part is highlighted."),
    state("no-places", "No places found", "Geocoder returns nothing for “Xyzzy”", search("no-places"), "Empty, no results: plain copy and a hint, no illustration."),
    state("searching", "Searching", "Place picked, trip search running", search("searching"), "Skeleton mirrors the trip rows."),
    state("results", "Results", "Trip search returns alternatives", search("results")),
    state("time-set", "Travel time set", "User picked Leave 08:15 tomorrow", search("time-set"), "The chip carries the chosen time, so it is never hidden in a dialog."),
    state("no-route", "No route", "Trip search returns nothing for Kirkenes", search("no-route"),
          "T5 in the sheet: the sheet’s main content can’t be shown. Action points at the likely fix, the travel time."),
    state("no-route-by", "No route by arrival", "Arrive by 08:30 to Fornebu can’t be met", search("no-route-by"),
          "T4 with the earliest possible arrival underneath, so the user still has a usable answer."),
    state("offline", "Offline", "No connection when searching", search("offline"),
          "T5 in the sheet: route search has no cache. The board behind keeps its own T4."),
    state("saved", "Saved (snackbar)", "Tap Save destination", search("saved"),
          "T3 with Undo: saving is reversible, so no confirmation. The destination then appears on the board with its default schedule."),
])
screen("time", "Travel time", "Primary question: when do I want to travel? Primary action: Done.", [
    state("leave-now", "Leave now", "Tap the travel-time chip", timepick("leave-now")),
    state("depart-at", "Depart at", "Choose Depart at", timepick("depart-at")),
    state("arrive-by", "Arrive by", "Choose Arrive by", timepick("arrive-by")),
    state("tomorrow", "Tomorrow", "Choose Tomorrow", timepick("tomorrow")),
])
P_OUT.append('</section>')
open(OUT, "w").write("\n".join(P_OUT) + "\n")
print("wrote", OUT, os.path.getsize(OUT))
