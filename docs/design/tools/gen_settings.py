# Generates docs/design/src/pages/settings.html. Edit this file and rerun: python3 tools/gen_settings.py
import itertools, html, os, sys
ONLY = os.environ.get('ONLY')
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "pages", "settings.html")  # run: python3 tools/gen_settings.py
_n = itertools.count(1)
def uid(p="st"): return f"{p}{next(_n)}"
def ic(name, cls=""): return f'<i data-icon="{name}"{f" class={chr(34)}{cls}{chr(34)}" if cls else ""}></i>'

# ---------- chrome ----------
def topbar(title, back="settings/main/board", extra="", sub=None):
    t = f'<span class="title">{title}</span>' if not sub else f'<span class="title st-title"><span>{title}</span><small>{sub}</small></span>'
    return (f'<header class="topbar"><button class="icon-btn" aria-label="Back" data-goto="{back}">{ic("arrow_back")}</button>'
            f'{t}{extra}</header>')

def app(title, body, back="board/main/loaded", extra="", after="", sub=None, wide=False, busy=False, cls="settings"):
    b = ' aria-busy="true"' if busy else ""
    w = " cols" if wide else ""
    return (f'<div class="app"{b}><div class="screen">{topbar(title, back, extra, sub)}'
            f'<div class="scroll"><div class="{cls}{w}">{body}</div></div></div>{after}</div>')

def group(title, card, hint="", head_extra="", hint_after=""):
    i = uid("sg")
    h = f'<div class="set-head"><h3 id="{i}">{title}</h3>{head_extra}</div>' if head_extra else f'<h3 id="{i}">{title}</h3>'
    hn = f'<p class="hint">{hint}</p>' if hint else ""
    ha = f'<p class="hint">{hint_after}</p>' if hint_after else ""
    return f'<section class="set-group" aria-labelledby="{i}">{h}{hn}<div class="set-card">{card}</div>{ha}</section>'

def col(*groups): return '<div class="set-col">' + "".join(groups) + "</div>"

def seg(label, options, pressed, disabled=False, to=None):
    bs = []
    for o in options:
        a = ' aria-pressed="true"' if o == pressed else ' aria-pressed="false"'
        d = " disabled" if disabled else ""
        s = f' data-state-to="{to[o]}"' if to and o in to else ""
        bs.append(f"<button{a}{d}{s}>{o}</button>")
    return f'<div class="segmented fill{" is-disabled" if disabled else ""}" role="group" aria-label="{label}">{"".join(bs)}</div>'

def stacked(label, control, sub="", disabled=False, extra=""):
    s = f"<span>{sub}</span>" if sub else ""
    return f'<div class="set-row stacked{" is-disabled" if disabled else ""}"><div class="text"><span>{label}</span>{s}</div>{extra}{control}</div>'

def switch_row(label, sub="", on=True, disabled=False, lead=""):
    s = f"<span>{sub}</span>" if sub else ""
    i = uid("sw")
    l = ic(lead, "lead") if lead else ""
    return (f'<label class="set-row" for="{i}">{l}<span class="text"><span>{label}</span>{s}</span>'
            f'<span class="switch"><input id="{i}" type="checkbox" role="switch"{" checked" if on else ""}{" disabled" if disabled else ""}></span></label>')

def link_row(label, sub="", val="", goto="#", lead=""):
    s = f"<span>{sub}</span>" if sub else ""
    v = f'<span class="val">{val}</span>' if val else ""
    l = ic(lead, "lead") if lead else ""
    return f'<a class="set-row" href="#" data-goto="{goto}">{l}<span class="text"><span>{label}</span>{s}</span>{v}{ic("chevron_right","chev")}</a>'

def snackbar(msg, action="Undo", visible=False):
    i = uid("snk")
    h = "" if visible else " hidden"
    return i, f'<div class="snackbar-host"><div class="snackbar" id="{i}" role="status"{h}><span class="msg">{msg}</span><button class="btn text" data-close>{action}</button></div></div>'

# ---------- data ----------
STOPS3 = [("Jernbanetorget", "All directions", "directions/all", ["metro", "tram", "bus"]),
          ("Oslo S", "Platform 2, Platform 4", "directions/some", ["train"]),
          ("Nationaltheatret", "All directions", "directions/all", ["train", "metro", "tram"])]
STOPS6 = STOPS3 + [("Majorstuen", "All directions", "directions/all", ["metro", "tram", "bus"]),
                   ("Stortinget", "Platform 1", "directions/all", ["metro"]),
                   ("Lillestrøm stasjon", "All directions", "directions/all", ["train", "bus"])]
MODE_IC = {"metro": "subway", "tram": "tram", "bus": "directions_bus", "train": "train", "ferry": "directions_boat"}

# ---------- sections ----------
def mode_group(cur, snk_onspot=None):
    if cur == "board":
        segc = (f'<div class="segmented fill" role="group" aria-label="Mode">'
                f'<button aria-pressed="false" data-open="#{snk_onspot}">On the spot</button><button aria-pressed="true">Wall board</button></div>')
        d = "An always-on display for the stops you choose, with burn-in protection and screen dimming."
    else:
        segc = ('<div class="segmented fill" role="group" aria-label="Mode">'
                '<button aria-pressed="true">On the spot</button><button aria-pressed="false" data-state-to="mode-switch">Wall board</button></div>')
        d = "Follows where you are: routes to your destinations from right here, and departures from the nearest stop."
    return group("Mode", f'<div class="set-row stacked">{segc}<p class="st-desc">{d}</p></div>')

def stops_group(stops=STOPS3, reorder=False, maxed=False, snk=None):
    rows = []
    n = len(stops)
    for k, (name, quays, dgoto, modes) in enumerate(stops):
        icons = "".join(ic(MODE_IC[m], "ic-sm") for m in modes)
        text = (f'<a class="text" href="#" data-goto="settings/{dgoto}"><span>{name}</span>'
                f'<span>{quays}</span></a>')
        if reorder:
            up = f'<button class="icon-btn" aria-label="Move up {name}"{" disabled" if k == 0 else ""}>{ic("arrow_upward")}</button>'
            dn = f'<button class="icon-btn" aria-label="Move down {name}"{" disabled" if k == n - 1 else ""}>{ic("arrow_downward")}</button>'
            cls = " is-lifted" if k == 1 else ""
            rows.append(f'<div class="set-row st-stop{cls}">{ic("drag_indicator","drag")}{text}{up}{dn}</div>')
        else:
            rm = f' data-snackbar="#{snk}"' if snk and name == "Nationaltheatret" else ""
            rows.append(f'<div class="set-row st-stop"><button class="icon-btn drag" aria-label="Drag to reorder {name}">{ic("drag_indicator")}</button>'
                        f'{text}<button class="icon-btn" aria-label="Remove {name}"{rm}>{ic("delete")}</button></div>')
    if not reorder:
        if maxed:
            hid = uid("mx")
            rows.append(f'<div class="set-row st-add is-disabled">{ic("add","lead")}<span class="text"><span>Add stop</span>'
                        f'<span id="{hid}">You can add up to 6 stops. Remove one to add another.</span></span></div>')
        else:
            rows.append(f'<a class="set-row st-add" href="#" data-goto="settings/add-stop/nearby">{ic("add","lead")}<span class="text"><span>Add stop</span></span>{ic("chevron_right","chev")}</a>')
    if reorder:
        btn = '<button class="btn text" data-state-to="board">Done</button>'
        hint = "Drag a stop, or use Move up and Move down. The board shows them in this order."
    else:
        btn = '<button class="btn text" data-state-to="reorder">Reorder</button>'
        hint = "Tap a stop to choose which directions it shows."
    return group(f"Stops on the board ({n} of 6)", "".join(rows), hint=hint, head_extra=btn)

def dest_group(onspot=False):
    sched1 = "Always" if onspot else "Weekdays · 07:00–09:00"
    rows = [
        f'<div class="set-row">{ic("flag","lead")}<a class="text" href="#" data-goto="settings/schedule/weekdays" aria-label="Change when Oslo lufthavn is shown. Now: {sched1}"><span>Oslo lufthavn</span><span class="st-sub">{ic("schedule","ic-sm")}{sched1}</span></a><button class="icon-btn" aria-label="Remove Oslo lufthavn">{ic("delete")}</button></div>',
        f'<div class="set-row">{ic("flag","lead")}<a class="text" href="#" data-goto="settings/schedule/always" aria-label="Change when Majorstuen is shown. Now: Always"><span>Majorstuen</span><span class="st-sub">{ic("schedule","ic-sm")}Always</span></a><button class="icon-btn" aria-label="Remove Majorstuen">{ic("delete")}</button></div>',
    ]
    i = uid("dq")
    rows.append(f'<div class="set-row st-search"><div class="field"><label class="sr-only" for="{i}">Add a destination</label><div class="control">{ic("search")}'
                f'<input id="{i}" type="search" placeholder="Stop, address or place…"></div></div></div>')
    title = "Destinations" if onspot else "Fastest route destinations"
    hint = ("Routes to these show at the top, from wherever you are." if onspot
            else "The board shows the fastest way to each one. Tap one to choose when it shows.")
    return group(title, "".join(rows), hint=hint, hint_after="Up to 5 destinations.")

def theme_group(auto=False):
    to = {"Auto": "auto-theme", "System": "board"}
    segc = seg("Theme", ["System", "Light", "Dark", "Auto"], "Auto" if auto else "System", to=to)
    card = stacked("Day/night mode", segc, "Auto follows the room's light, using the light sensor.")
    if auto:
        a, b = uid("lx"), uid("lx")
        card += (f'<div class="set-row stacked st-lux"><div class="text"><span>Light thresholds</span>'
                 f'<span>Between the two, the theme stays as it is, so it doesn\'t flicker at dusk.</span></div>'
                 f'<div class="st-reading" role="status">{ic("light_mode","ic-sm")}<span>Current: <b class="num">37 lux</b></span><span class="tag">Stays dark</span></div>'
                 f'<div class="st-scale" aria-hidden="true"><span class="zone dark"></span><span class="zone keep"></span><span class="zone light"></span><span class="mark" style="left:46%"></span></div>'
                 f'<div class="st-scale-k" aria-hidden="true"><span>Dark</span><span>Keeps the current theme</span><span>Light</span></div>'
                 f'<div class="st-slider"><label for="{a}">Dark below</label><input id="{a}" type="range" min="0" max="80" value="10"><output class="num" for="{a}">10 lux</output></div>'
                 f'<div class="st-slider"><label for="{b}">Light above</label><input id="{b}" type="range" min="0" max="80" value="50"><output class="num" for="{b}">50 lux</output></div></div>')
    return group("Theme", card)

def transport_group():
    card = (switch_row("Show delay information", "Late departures show the planned time struck through")
            + switch_row("Show informational messages", "Disruptions and notices from the operator")
            + switch_row("Show canceled departures", on=False)
            + switch_row("Show weather forecast"))
    return group("Transport information", card)

def filtered_group():
    chips = (f'<button class="chip st-line" aria-label="Show line 31 again">{"<span class=\"pill sm line-bus\">31</span>"}Snarøya{ic("close","ic-sm")}</button>'
             f'<button class="chip st-line" aria-label="Show line 12 again"><span class="pill sm line-tram">12</span>Majorstuen{ic("close","ic-sm")}</button>')
    card = f'<div class="set-row stacked"><div class="st-chips">{chips}</div></div>'
    return group("Filtered out lines", card, hint="Hidden from the departures and avoided in route searches.",
                 hint_after="To filter out another line, tap its departure on the board.")

def refresh_seg():
    return seg("Refresh interval", ["None", "30 Sec", "1 Min", "2 Min", "5 Min"], "1 Min")

def power_group():
    card = (stacked("Refresh interval", refresh_seg(), "How often departures update")
            + switch_row("Dynamic refresh", "Slower at night and while the screen is dimmed")
            + switch_row("Full screen", "Hides the system bars", on=True)
            + switch_row("Always on display", "Prevents the screen from timing out"))
    return group("Power &amp; refresh", card)

def wake_group(camera_on=True):
    i = uid("cs")
    card = switch_row("Wake screen by front camera motion", "The camera only notices movement. Nothing is recorded or sent.", on=camera_on)
    if camera_on:
        card += stacked("Camera sensitivity", seg("Camera sensitivity", ["Low", "Medium", "High"], "Medium"), "How much motion is needed to wake the screen")
    else:
        card += (f'<div class="set-row stacked is-disabled"><div class="text"><span>Camera sensitivity</span>'
                 f'<span id="{i}">Turn on camera wake to choose how much motion wakes the screen.</span></div>'
                 f'<div class="segmented fill is-disabled" role="group" aria-label="Camera sensitivity" aria-describedby="{i}">'
                 f'<button aria-pressed="false" disabled>Low</button><button aria-pressed="true" disabled>Medium</button><button aria-pressed="false" disabled>High</button></div></div>')
    card += stacked("Auto-dim delay", seg("Auto-dim delay", ["15 Sec", "30 Sec", "1 Min", "2 Min", "Never"], "1 Min"), "Time before the screen dims after the last motion")
    return group("Wake and dim", card)

def oled_group():
    return group("OLED screen care", stacked("Pixel shift", seg("Pixel shift level", ["Off", "Low", "Medium", "High"], "Medium"),
                                             "Moves the board a few pixels now and then so nothing burns in"))

LOC_STATUS = {
    "none": ("info", "Not requested", ""),
    "gps": ("spin", "Requesting GPS (Precise)…", ""),
    "network": ("spin", "GPS failed. Requesting Network (Approximate)…", ""),
    "success": ("check_circle", "Success via GPS", "ok"),
    "denied": ("callout-warn", "Location access is off", "Allow it in system settings, or enter coordinates below."),
    "failed": ("callout-err", "Couldn't find your location", "Neither GPS nor the network answered. Try again outdoors or near a window, or enter coordinates below."),
}

def location_card(status="success", coords="59.9111, 10.7528", lat_err=False, caption=None):
    kind, text, extra = LOC_STATUS[status]
    cap = f'<div class="st-caption">{caption}</div>' if caption else ""
    rows = cap + f'<div class="set-row">{ic("location_on","lead")}<span class="text"><span>Current coordinates</span><span class="num">{coords}</span></span></div>'
    if kind == "spin":
        rows += f'<div class="set-row st-status" role="status"><span class="spinner sm" aria-hidden="true"></span><span class="text"><span>{text}</span></span></div>'
    elif kind == "callout-warn":
        rows += (f'<div class="set-row stacked"><div class="callout warning" role="status">{ic("location_off")}<div class="body"><strong>{text}</strong>{extra}</div>'
                 f'<button class="btn text">Open settings</button></div></div>')
    elif kind == "callout-err":
        rows += (f'<div class="set-row stacked"><div class="callout error" role="status">{ic("error")}<div class="body"><strong>{text}</strong>{extra}</div>'
                 f'<button class="btn text">Try again</button></div></div>')
    else:
        cls = " ok" if extra == "ok" else ""
        rows += f'<div class="set-row st-status{cls}" role="status">{ic(kind, "ic-fill" if extra else "")}<span class="text"><span>{text}</span></span></div>'
    busy = kind == "spin"
    rows += (f'<div class="set-row stacked"><button class="btn outline{" is-loading" if busy else ""}"{" aria-busy=\"true\"" if busy else ""}>'
             f'{ic("my_location")}Detect location automatically</button></div>')
    la, lo, e = uid("lat"), uid("lon"), uid("err")
    lat_v = "59,9111" if lat_err else ""
    lat_attr = f' aria-invalid="true" aria-describedby="{e}"' if lat_err else ""
    lat_help = (f'<div class="help" id="{e}">{ic("error","ic-sm")}Use a dot for decimals, like 59.9111</div>' if lat_err else "")
    rows += (f'<div class="set-row stacked"><div class="text"><span>Or enter coordinates</span></div><div class="st-coords">'
             f'<div class="field{" is-error" if lat_err else ""}"><label for="{la}">Latitude</label><div class="control"><input id="{la}" inputmode="decimal" placeholder="59.9111" value="{lat_v}"{lat_attr}></div>{lat_help}</div>'
             f'<div class="field"><label for="{lo}">Longitude</label><div class="control"><input id="{lo}" inputmode="decimal" placeholder="10.7528" value="{"10.7528" if lat_err else ""}"></div></div>'
             f'</div><button class="btn secondary">Save coordinates</button></div>')
    return rows

def location_group(status="success", **kw):
    return group("Location", location_card(status, **kw), hint="Used for the weather and as the start of your fastest routes.")

def about_group():
    card = (link_row("Transport companies", "Which companies’ departures and routes PulseTransit can show", goto="settings/data-sources/loaded", lead="apartment")
            + f'<div class="set-row">{ic("info","lead")}<span class="text"><span>Version</span><span>Weather comes from MET Norway (yr.no).</span></span><span class="val num">3.2.1</span></div>')
    return group("Data sources and about", card)

def route_group():
    card = (stacked("Prefer", seg("Prefer", ["Fastest", "Fewer transfers", "Less walking"], "Fastest"))
            + link_row("Travel by", "Routes only use the selected kinds of transport.", val="All", goto="settings/travel-by/all")
            + stacked("Walking speed", seg("Walking speed", ["Slow", "Normal", "Fast"], "Normal"))
            + stacked("Route alternatives", seg("Route alternatives", ["Auto", "1", "2", "3", "4", "5"], "Auto"),
                      "How many departures to show for each destination. Auto fits as many as the screen has room for."))
    return group("Route search", card)

def alerts_group():
    return group("Journey alerts", link_row("Journey alerts", "Alerts from live times", val="All alerts", goto="settings/journey-alerts/time", lead="notifications"),
                 hint="How “Notify me about this journey” follows your trip.")

def refresh_group():
    return group("Refresh", stacked("Refresh interval", refresh_seg(), "How often departures and routes update"))

# ---------- main screen ----------
def board_main(theme_auto=False, stops=STOPS3, reorder=False, maxed=False, camera_on=True, snk=None, after="", loc="success"):
    did, dlg = onspot_dialog()
    body = (col(mode_group("board", did), stops_group(stops, reorder, maxed, snk), dest_group(), filtered_group(), transport_group())
            + col(theme_group(theme_auto), power_group(), wake_group(camera_on), oled_group(), location_group(loc), about_group()))
    return app("Settings", body, after=dlg + after, wide=True)

def onspot_dialog():
    i = uid("dlg")
    return i, (f'<div class="overlay" id="{i}" data-overlay hidden role="presentation"><div class="dialog" role="alertdialog" aria-labelledby="{i}t" aria-describedby="{i}d">'
               f'<h2 id="{i}t">Switch to on the spot?</h2><p id="{i}d">Your 3 board stops stay saved. The app follows where you are instead, and the wall-board features (dimming, pixel shift, camera wake) turn off.</p>'
               f'<div class="actions"><button class="btn text" data-close>Keep the wall board</button><button class="btn" data-goto="settings/main/onspot">Switch to on the spot</button></div></div></div>')

def onspot_main(after=""):
    body = (mode_group("onspot") + dest_group(True) + route_group() + alerts_group() + theme_group()
            + transport_group() + filtered_group() + refresh_group() + about_group())
    return app("Settings", body, back="onspot/main/loaded", after=after)

def focus(body, back="board/main/loaded", after=""):
    """A main-screen state scrolled to the section it is about."""
    return app("Settings", body, back=back, after=after)

def state(sid, label, trigger, content, note=None):
    n = f' data-note="{html.escape(note, quote=True)}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{html.escape(trigger, quote=True)}"{n}>\n{content}\n</div>'

def screen(sid, title, purpose, states):
    return (f'<article class="v-screen" data-screen="{sid}" data-title="{title}" data-purpose="{html.escape(purpose, quote=True)}">\n'
            + "\n".join(states) + "\n</article>")

S = []

# main
snk_id, snk = snackbar("Removed Nationaltheatret")
snk2_id, snk2 = snackbar("Removed Nationaltheatret", visible=True)
loc_variants = "".join(group(t, location_card(s, coords=c), hint=h) for t, s, c, h in [
    ("Requesting GPS", "gps", "Not set", "Right after “Detect location automatically”. The button shows progress and can't be pressed twice."),
    ("Falling back to network", "network", "Not set", "GPS gave nothing in time, so it asks for an approximate fix."),
    ("Found", "success", "59.9111, 10.7528", "Coordinates fill in; weather and routes use them at once."),
    ("Permission denied", "denied", "Not set", "T4 with a way out: system settings, or type the coordinates."),
    ("Failed", "failed", "Not set", "T4, retryable. Manual entry stays available underneath."),
])
mode_dlg = ('<div class="overlay" data-overlay data-static role="presentation"><div class="dialog" role="alertdialog" aria-labelledby="st-md-t" aria-describedby="st-md-d">'
            '<h2 id="st-md-t">Switch to the wall board?</h2><p id="st-md-d">On-spot destinations stay saved. The board shows your 3 favourite stops instead of the nearest one.</p>'
            '<div class="actions"><button class="btn text" data-state-to="onspot">Stay on the spot</button><button class="btn" data-goto="settings/main/board">Switch to wall board</button></div></div></div>')
S.append(screen("main", "Settings", "Primary question: what does the board show, and how does the screen behave? Every change applies at once; there is no Save. On wall, fold and desktop it splits into two columns: what's shown (left) and how the device behaves (right).", [
    state("board", "Wall board", "Opened from the board's settings button in BOARD mode",
          board_main(snk=snk_id, after=snk),
          "Grouped by job: what the board shows (stops, destinations, lines, info), then how the device behaves (theme, power, wake and dim, OLED care, location). Phone shows the left column, then the right. Remove acts at once with T3 Undo."),
    state("onspot", "On the spot", "Opened in ON_SPOT mode",
          onspot_main(),
          "Only what applies when following the device: no stops, location, wall-display or OLED sections. Single centred column on wide screens."),
    state("auto-theme", "Auto theme", "Theme set to Auto",
          focus(theme_group(True) + power_group()),
          "Scrolled to Theme. The live reading updates about once a second, so the thresholds can be set against the room as it is now."),
    state("stops-max", "6 stops (max)", "Six stops saved",
          focus(stops_group(STOPS6, maxed=True) + dest_group()),
          "Add stays visible but disabled with the reason next to it, not hidden. Not an error, so no tier: a limit stated where it applies."),
    state("reorder", "Reorder", "Tapped Reorder",
          focus(stops_group(reorder=True)),
          "Drag handles for touch; Move up / Move down buttons for keyboard and screen readers. The lifted row shows a drag in progress."),
    state("removed", "Stop removed", "Tapped Remove on Nationaltheatret",
          focus(stops_group(STOPS3[:2]) + dest_group(), after=snk2),
          "T3: reversible, so it acts at once and offers Undo for 8 s instead of a confirmation dialog."),
    state("location-states", "Location states", "Detect location automatically pressed (each outcome shown)",
          focus(loc_variants),
          "The Location card in each status, top to bottom. Denied and failed are T4: they persist in the card until fixed, and manual entry stays usable."),
    state("coords-error", "Coordinates error", "Saved latitude typed with a comma",
          focus(location_group("none", coords="Not set", lat_err=True) + about_group()),
          "T1 field error on blur or save, replacing the old status line “Invalid manual coordinates format.” Says what to type, with an example."),
    state("camera-off", "Camera wake off", "Camera wake switched off",
          focus(wake_group(False) + oled_group()),
          "Sensitivity only matters with the camera on, so it is disabled and says why, instead of disappearing and shifting the rows."),
    state("mode-switch", "Switch mode (dialog)", "Tapped Wall board in on-spot settings",
          onspot_main(after=mode_dlg),
          "T6: changing mode changes what the whole app does, the one decision worth a dialog. Nothing is lost either way."),
]))

# add-stop
def add_item(name, sub, modes, added=False, disabled=False):
    icons = "".join(ic(MODE_IC[m], "ic-sm") for m in modes)
    if added:
        act = f'<span class="st-added">{ic("check_circle","ic-fill ic-done")}Added</span>'
    else:
        act = f'<button class="btn text"{" disabled" if disabled else ""} aria-label="Add {name}">Add</button>'
    return (f'<li class="list-item"><span class="st-stop-ic" aria-hidden="true">{ic(MODE_IC[modes[0]])}</span><span class="text"><span>{name}</span>'
            f'<span>{sub}</span></span>{act}</li>')

def search_field(value="", label="Search by name…"):
    i = uid("q")
    clear = f'<button class="icon-btn" aria-label="Clear search">{ic("close")}</button>' if value else ""
    return (f'<div class="field st-searchbar"><label class="sr-only" for="{i}">Search stops</label><div class="control">{ic("search")}'
            f'<input id="{i}" type="search" placeholder="{label}" value="{value}">{clear}</div></div>')

NEARBY = [("Jernbanetorget", "60 m", ["metro", "tram", "bus"], True), ("Oslo S", "180 m", ["train"], True),
          ("Oslo bussterminal", "420 m", ["bus"], False), ("Stortorvet", "450 m", ["tram", "bus"], False),
          ("Kongens gate", "610 m", ["tram"], False), ("Stortinget", "700 m", ["metro"], False)]

def add_list(header, items, disabled=False):
    return (f'<div class="st-list"><div class="list-header">{header}</div><ul class="list set-card">'
            + "".join(add_item(n, s, m, a, disabled) for n, s, m, a in items) + "</ul></div>")

def skel_rows(n=5):
    r = '<li class="list-item"><span class="skeleton circle" style="width:40px;height:40px"></span><span class="text" style="display:grid;gap:8px"><span class="skeleton line" style="width:60%"></span><span class="skeleton line" style="width:30%"></span></span></li>'
    return '<ul class="list set-card">' + r * n + "</ul>"

snk3_id, snk3 = snackbar("Added Stortorvet to the board", action="Undo")
NEARBY_I = [(n, f"Oslo · {d}", m, a) for n, d, m, a in NEARBY]
def nearby_with_snack():
    items = "".join(add_item(n, s, m, a) for n, s, m, a in NEARBY_I).replace('aria-label="Add Stortorvet"', f'aria-label="Add Stortorvet" data-snackbar="#{snk3_id}"')
    return f'<div class="st-list"><div class="list-header">Nearby stop places</div><ul class="list set-card">{items}</ul></div>'

AS_BACK = "settings/main/board"
S.append(screen("add-stop", "Add stop places", "Primary action: add a stop to the board, nearest first. Search covers stops anywhere in Norway.", [
    state("nearby", "Nearby", "Opened from “Add stop”; location known",
          app("Add stop places", search_field() + nearby_with_snack(), back=AS_BACK, after=snk3, cls="settings narrow"),
          "Stops already on the board say Added instead of offering a second Add."),
    state("loading", "Loading", "Finding nearby stops or searching",
          app("Add stop places", search_field() + '<div class="st-list"><div class="list-header">Nearby stop places</div>' + skel_rows() + "</div>", back=AS_BACK, busy=True, cls="settings narrow"),
          "Skeleton rows match the loaded rows. The field stays usable."),
    state("results", "Search results", "Typed “Majo”",
          app("Add stop places", search_field("Majo") + add_list("Search results", [
              ("Majorstuen", "Oslo", ["metro", "tram", "bus"], False),
              ("Majorstukrysset", "Oslo", ["bus"], False),
              ("Majorstuhuset", "Oslo", ["tram"], False)]), back=AS_BACK, cls="settings narrow")),
    state("no-results", "No stops found", "Search matched nothing",
          app("Add stop places", search_field("Majorsten") + f'<div class="empty">{ic("search_off","ic-lg")}<h2>No stops found</h2><p>Check the spelling, or try the first part of the name, like “Majo”.</p></div>', back=AS_BACK, cls="settings narrow"),
          "Empty result, not an error: it says what to try next."),
    state("none-nearby", "None nearby", "No stops within 1 km of the location",
          app("Add stop places", search_field() + f'<div class="empty">{ic("near_me_disabled","ic-lg")}<h2>No stops within 1 km.</h2><p>Search by name to add a stop anywhere in Norway.</p></div>', back=AS_BACK, cls="settings narrow")),
    state("max-reached", "Max 6 reached", "Opened with 6 stops already saved",
          app("Add stop places", '<div class="callout info" role="status">' + ic("info") + '<div class="body"><strong>Maximum of 6 stop places reached</strong>Remove one in Settings to add another.</div></div>'
              + search_field() + add_list("Nearby stop places", [(n, s, m, a) for n, s, m, a in NEARBY_I], disabled=True), back=AS_BACK, cls="settings narrow"),
          "T4: a lasting condition for this screen, with the way out. Add buttons are disabled; the callout says why."),
]))

# directions
QUAYS = [("1", ["L1", "R14"], ["train", "train"], "Spikkestad, Drammen"),
         ("2", ["RE30", "FLY2"], ["train", "express"], "Lillehammer, Oslo lufthavn"),
         ("3", ["L1"], ["train"], "Lillestrøm"),
         ("4", ["R14", "F4"], ["train", "express"], "Kongsberg, Oslo lufthavn"),
         ("13", ["FLY2"], ["express"], "Oslo lufthavn")]
def quay_rows(selected):
    out = []
    for q, lines, kinds, dest in QUAYS:
        i = uid("qy")
        pills = "".join(f'<span class="pill sm line-{k}">{l}</span>' for l, k in zip(lines, kinds))
        out.append(f'<label class="set-row" for="{i}"><span class="check"><input id="{i}" type="checkbox"{" checked" if q in selected else ""}></span>'
                   f'<span class="text"><span>Platform {q}</span><span class="st-sub"><span class="st-pills">{pills}</span>Towards {dest}</span></span></label>')
    return "".join(out)
def dir_status(selected):
    if not selected:
        return f'<div class="st-summary" role="status">{ic("check_circle","ic-fill ic-done")}<span>Showing all directions</span></div>'
    return (f'<div class="st-summary" role="status">{ic("filter_alt","ic-done")}<span>Showing {len(selected)} of 5 platforms</span>'
            f'<button class="btn text">Show all</button></div>')
DIR_HINT = '<p class="st-intro">Choose the platforms the board shows for Oslo S. With none chosen, it shows every direction.</p>'
S.append(screen("directions", "Stop directions", "Primary question: which directions of this stop does the board show? Tick platforms; none ticked means all.", [
    state("all", "All directions", "Stop has no platform filter",
          app("Directions", DIR_HINT + dir_status([]) + f'<div class="set-card">{quay_rows([])}</div>', back=AS_BACK, sub="Oslo S", cls="settings narrow")),
    state("some", "Some selected", "Platforms 2 and 4 ticked",
          app("Directions", DIR_HINT + dir_status(["2", "4"]) + f'<div class="set-card">{quay_rows(["2", "4"])}</div>', back=AS_BACK, sub="Oslo S", cls="settings narrow")),
    state("loading", "Loading", "Platforms being fetched from Entur",
          app("Directions", DIR_HINT + skel_rows(4), back=AS_BACK, sub="Oslo S", busy=True, cls="settings narrow")),
    state("failed", "Failed", "Entur didn't answer, nothing cached",
          app("Directions", f'<div class="screen-error">{ic("cloud_off","ic-lg")}<h2>Couldn’t load the platforms</h2><p>Entur didn’t answer. Until you choose, the board keeps showing every direction for Oslo S.</p><button class="btn" data-state-to="loading">Try again</button></div>',
              back=AS_BACK, sub="Oslo S", cls="settings narrow st-fill"),
          "T5: the list is this screen's only content and nothing is cached. Top bar stays, so Back still works."),
]))

# schedule
DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
def schedule_body(mode, days=(), frm="07:00", to="09:00", next_day=False, err=False, summary=""):
    always = mode == "always"
    segc = ('<div class="segmented fill" role="group" aria-label="When to show">'
            f'<button aria-pressed="{str(always).lower()}" data-state-to="always">Always</button><button aria-pressed="{str(not always).lower()}" data-state-to="weekdays">At set times</button></div>')
    b = '<p class="st-intro">The route is only shown and updated at these times.</p>' + f'<div class="set-card">{stacked("Oslo lufthavn", segc)}'
    if not always:
        e = uid("de")
        presets = "".join(f'<button class="chip"{" aria-pressed=\"true\"" if p == pre else ""}>{p}</button>' for p, pre in
                          [("Every day", "Every day" if len(days) == 7 else ""), ("Weekdays", "Weekdays" if list(days) == DAYS[:5] else ""), ("Weekend", "Weekend" if list(days) == DAYS[5:] else "")])
        chips = "".join(f'<button class="chip st-day" aria-pressed="{str(d in days).lower()}">{d}</button>' for d in DAYS)
        errattr = f' aria-invalid="true" aria-describedby="{e}"' if err else ""
        errh = f'<div class="help" id="{e}">{ic("error","ic-sm")}Pick at least one day</div>' if err else ""
        b += (f'<div class="set-row stacked{" st-err" if err else ""}"><div class="text"><span>Days</span></div><div class="st-chips">{presets}</div>'
              f'<div class="st-days field{" is-error" if err else ""}" role="group" aria-label="Days"{errattr}>{chips}{errh}</div></div>')
        f1, f2 = uid("tf"), uid("tt")
        nd = '<span class="tag">next day</span>' if next_day else ""
        b += (f'<div class="set-row stacked"><div class="st-times"><div class="field"><label for="{f1}">From</label><div class="control"><input id="{f1}" type="time" value="{frm}"></div></div>'
              f'<span class="st-dash" aria-hidden="true">–</span><div class="field"><label for="{f2}">To {nd}</label><div class="control"><input id="{f2}" type="time" value="{to}"></div></div></div></div>')
    b += "</div>"
    if summary:
        b += f'<div class="st-summary" role="status">{ic("schedule")}<span>Shows <b>{summary}</b></span></div>'
    return b
SAVE = '<button class="btn text">Save</button>'
SAVE_ERR = '<button class="btn text" data-state-to="no-days">Save</button>'
S.append(screen("schedule", "Destination schedule", "Primary question: when should the route to this destination appear on the board? Tap Save to apply.", [
    state("weekdays", "Weekdays 07–09", "Opened for a weekday-morning destination",
          app("When to show", schedule_body("set", DAYS[:5], summary="Weekdays · 07:00–09:00"), back=AS_BACK, extra=SAVE, cls="settings narrow")),
    state("always", "Always", "Destination with no schedule",
          app("When to show", schedule_body("always", summary="Always"), back=AS_BACK, extra=SAVE, cls="settings narrow")),
    state("overnight", "Over midnight", "End time earlier than start time",
          app("When to show", schedule_body("set", ["Fri", "Sat"], "22:00", "02:00", next_day=True, summary="Fri, Sat · 22:00–02:00 next day"), back=AS_BACK, extra=SAVE, cls="settings narrow"),
          "Not an error: an end before the start means the next day, and the label says so."),
    state("no-days", "No days (error)", "Saved with every day unticked",
          app("When to show", schedule_body("set", [], err=True, summary=""), back=AS_BACK, extra=SAVE_ERR, cls="settings narrow"),
          "T1 on the day picker, focus moves there. No T2 summary: the form is one screen with one problem. Save stays enabled so pressing it explains."),
]))

# travel-by
GROUPS = [("Bus", "directions_bus", ["Local bus", "Express bus", "Airport bus", "Rail replacement bus"]),
          ("Train", "train", ["Local train", "Regional train", "Long-distance and night train", "Express train"]),
          ("Boat", "directions_boat", ["Car ferry", "Passenger ferry", "Express boat"]),
          ("Other", "tram", ["Metro", "Tram", "Plane", "On-demand transport", "Cable car and funicular"])]
def travel_body(off=()):
    out = '<p class="st-intro">Routes only use the selected kinds of transport.</p>'
    for g, icn, modes in GROUPS:
        on = [m for m in modes if m not in off]
        if len(on) == len(modes): st, box, cnt = "true", "check_box", "All"
        elif not on: st, box, cnt = "false", "check_box_outline_blank", "None"
        else: st, box, cnt = "mixed", "indeterminate_check_box", f"{len(on)} of {len(modes)}"
        gi = uid("tg")
        rows = (f'<div class="set-row st-group-row"><span class="lead st-gic">{ic(icn)}</span><span class="text"><span id="{gi}">{g}</span><span>{cnt}</span></span>'
                f'<button class="icon-btn st-tri" role="checkbox" aria-checked="{st}" aria-labelledby="{gi}">{ic(box, "ic-fill" if st != "false" else "")}</button></div>')
        for m in modes:
            i = uid("tm")
            rows += f'<label class="set-row st-sub-row" for="{i}"><span class="text"><span>{m}</span></span><span class="check"><input id="{i}" type="checkbox"{"" if m in off else " checked"}></span></label>'
        out += f'<div class="set-card">{rows}</div>'
    return out
S.append(screen("travel-by", "Travel by", "Primary question: which kinds of transport may routes use? Changes apply at once.", [
    state("all", "All", "No modes excluded", app("Travel by", travel_body(), back="settings/main/onspot", cls="settings narrow")),
    state("partial", "Partly selected", "Airport bus, plane and on-demand transport turned off",
          app("Travel by", travel_body(["Airport bus", "Plane", "On-demand transport"]), back="settings/main/onspot", cls="settings narrow"),
          "Group boxes show mixed when only some kinds are on, with the count beside the name, e.g. “3 of 4”."),
]))

# data sources
COMPANIES = [("Ruter", "Oslo and Akershus"), ("Vy", "Trains and buses, nationwide"), ("Flytoget", "Airport express train"),
             ("SJ Norge", "Trains, north and west"), ("Go-Ahead Nordic", "Southern line trains"), ("Norled", "Ferries and express boats"),
             ("Skyss", "Vestland"), ("AtB", "Trøndelag"), ("Kolumbus", "Rogaland"), ("Brakar", "Buskerud")]
DS_INTRO = '<p class="st-intro">Departures and routes come from Entur, the national registry of public transport in Norway. These companies share their timetables with it. If a company isn’t listed, PulseTransit can’t show its departures.</p>'
def ds_search(v=""):
    i = uid("ds")
    clear = f'<button class="icon-btn" aria-label="Clear search">{ic("close")}</button>' if v else ""
    return f'<div class="field st-searchbar"><label class="sr-only" for="{i}">Search companies</label><div class="control">{ic("search")}<input id="{i}" type="search" placeholder="Search companies" value="{v}">{clear}</div></div>'
DS_FOOT = '<p class="st-foot">To check a particular trip, search for it at entur.no.<br>Weather comes from MET Norway (yr.no).</p>'
def ds_list():
    return ('<div class="st-list"><div class="list-header num">61 companies</div><ul class="list set-card">'
            + "".join(f'<li class="list-item"><span class="text"><span>{n}</span><span>{d}</span></span></li>' for n, d in COMPANIES)
            + '<li class="list-item muted"><span class="text"><span>…and 51 more</span></span></li></ul></div>')
DS_BACK = "settings/main/board"
S.append(screen("data-sources", "Data sources", "Primary question: can PulseTransit show my operator's departures?", [
    state("loaded", "Loaded", "List fetched from Entur", app("Data sources", DS_INTRO + ds_search() + ds_list() + DS_FOOT, back=DS_BACK, cls="settings narrow")),
    state("loading", "Loading", "Fetching the list",
          app("Data sources", DS_INTRO + ds_search() + '<div class="st-list"><div class="list-header"><span class="skeleton line" style="width:96px;display:inline-block"></span></div>' + skel_rows(5).replace('<span class="skeleton circle" style="width:40px;height:40px"></span>', "") + "</div>", back=DS_BACK, busy=True, cls="settings narrow"),
          "The intro is local text, so only the list is skeleton."),
    state("no-match", "No match", "Searched “Flixbus”",
          app("Data sources", DS_INTRO + ds_search("Flixbus") + f'<div class="empty st-empty-sm">{ic("search_off","ic-lg")}<p>No company matches “Flixbus”. Companies that aren’t in Entur aren’t shown in PulseTransit.</p></div>' + DS_FOOT, back=DS_BACK, cls="settings narrow"),
          "A real answer, not an error: the company isn't covered."),
    state("failed", "Failed", "Entur didn't answer",
          app("Data sources", DS_INTRO + f'<div class="screen-error">{ic("cloud_off","ic-lg")}<h2>Couldn’t load the list from Entur.</h2><p>Check your connection and try again. Departures on the board are not affected.</p><button class="btn" data-state-to="loading">Try again</button></div>', back=DS_BACK, cls="settings narrow"),
          "T5 in the list area: the list is the content and nothing is cached. The intro still answers the question in general terms."),
]))

# journey alerts
KINDS = ["When my ride leaves", "When my stop is next", "Where to change", "When to walk", "Faster routes at a change", "A missed ride (with location)", "Arrival"]
def ja_body(loc=False, off=()):
    name = uid("jm")
    def radio(val, title, detail, checked):
        i = uid("jr")
        return (f'<label class="set-row st-radio" for="{i}"><span class="check"><input type="radio" id="{i}" name="{name}"{" checked" if checked else ""}></span>'
                f'<span class="text"><span>{title}</span><span>{detail}</span></span></label>')
    b = '<p class="st-intro">How “Notify me about this journey” follows your trip. You can change it any time.</p>'
    b += group("Following the trip", radio("t", "Alerts from live times", "Departures, your stop and changes, following delays. No location.", not loc)
               + radio("l", "Also notice if I miss my ride", "Uses your location during the journey, also with the screen off.", loc),
               hint_after=f'{ic("lock","ic-sm")} Your location stays on this device and is never sent anywhere.')
    rows = ""
    on_count = 0
    for k in KINDS:
        needs = "missed" in k
        dis = needs and not loc
        on = k not in off and not dis
        on_count += on
        sub = "Needs “Also notice if I miss my ride”" if dis else ""
        rows += switch_row(k, sub, on=on, disabled=dis)
    total = 7
    cnt = "All alerts" if on_count == total else f"{on_count} of {total} alerts"
    if not loc and not off: cnt = "All alerts"
    b += group("Notify me about", rows, head_extra=f'<span class="val st-count num">{cnt}</span>')
    return b
S.append(screen("journey-alerts", "Journey alerts", "Primary question: how should a followed journey alert me, and about what?", [
    state("time", "Time only", "Default: alerts from live times, no location",
          app("Journey alerts", ja_body(), back="settings/main/onspot", cls="settings narrow"),
          "Missed-ride alerts need location, so that switch is off and disabled with the reason, and the count treats it as not applicable."),
    state("location", "With location", "Chose “Also notice if I miss my ride”",
          app("Journey alerts", ja_body(loc=True), back="settings/main/onspot", cls="settings narrow"),
          "Choosing this asks for location permission at that moment (see journeys/rationale)."),
    state("some-kinds", "Some kinds off", "Turned off walk, faster-route and arrival alerts",
          app("Journey alerts", ja_body(loc=True, off=["When to walk", "Faster routes at a change", "Arrival"]), back="settings/main/onspot", cls="settings narrow")),
]))

page = ('<!-- Generated by scratchpad/settings/gen_settings.py. -->\n'
        '<section class="v-page" data-page="settings" data-title="Settings" data-group="Settings">\n<h1>Settings</h1>\n'
        '<p class="v-lead">Set up what the board shows and how the device behaves. Visited rarely, so it optimises for finding a setting and understanding it, not speed. '
        'Every change applies at once (no Save), except the destination schedule. Sections depend on the mode: <b>Wall board</b> or <b>On the spot</b>. '
        'On ≥700 px the content is a centred column (max 640 px); from 1000 px the board settings split into two columns, '
        '<i>what the board shows</i> and <i>how the device behaves</i>, mirroring the app\'s current tablet layout. A list/detail split was rejected: '
        'the sub-screens are short, one-off tasks, and a second pane would leave most of a wall display empty most of the time.</p>\n'
        + "\n".join(x for x in S if not ONLY or f'data-screen="{ONLY}"' in x[:80]) + "\n</section>\n")
open(sys.argv[1] if len(sys.argv) > 1 else OUT, "w").write(page)
print("ok", len(page))
