#!/usr/bin/env python3
"""Generates docs/design/src/pages/onboarding.html. Edit this file and rerun: python3 tools/gen_onboarding.py"""
import os; OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "pages", "onboarding.html")  # run: python3 tools/gen_onboarding.py


def f(c):
    return f'style="fill:var(--color-{c})"'


def st(c, w, extra=""):
    return f'style="fill:none;stroke:var(--color-{c});stroke-width:{w};stroke-linecap:round;stroke-linejoin:round{extra}"'


def bars(x, y, ws, c="on-surface-variant", h=8, gap=0):
    return "".join(f'<rect x="{x}" y="{y + i * (h + gap)}" width="{w}" height="{h}" rx="{h / 2}" {f(c)}/>' for i, w in enumerate(ws))


def backdrop():
    return f'<rect x="8" y="8" width="304" height="224" rx="28" {f("surface-container")}/>'


def svg(body, label_id=None):
    return f'<svg class="illus ob-illus" viewBox="0 0 320 240" aria-hidden="true">{backdrop()}{body}</svg>'


def dep_row(x, y, w, line, cd_w=26):
    return (f'<rect x="{x}" y="{y}" width="30" height="18" rx="5" {f("line-" + line)}/>'
            f'<rect x="{x + 40}" y="{y + 5}" width="{w}" height="8" rx="4" {f("on-surface-variant")}/>'
            f'<rect x="{x + 40 + w + 18}" y="{y + 3}" width="{cd_w}" height="12" rx="6" {f("primary")}/>')


def gear(cx, cy, r):
    import math
    d = round(2 * math.pi * r / 16, 3)
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" {st("primary", r * 0.42, ";stroke-linecap:butt;stroke-dasharray:" + str(d) + " " + str(d))}/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r * 0.78}" {f("primary")}/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r * 0.32}" {f("surface-container")}/>')


ILL = {
    # Nearest stop: map with you-dot, a stop marker and its departures card
    "nearest": svg(
        f'<path d="M8 92 H312 M8 150 H312 M118 8 V232 M226 8 V232" {st("outline-variant", 14, ";stroke-linecap:butt")}/>'
        f'<circle cx="118" cy="92" r="30" {f("primary-container")}/><circle cx="118" cy="92" r="11" {f("surface")}/><circle cx="118" cy="92" r="8" {f("primary")}/>'
        f'<path d="M118 92 L190 92" {st("primary", 4, ";stroke-dasharray:2 9")}/>'
        f'<rect x="190" y="68" width="40" height="40" rx="10" {f("line-bus")}/><rect x="200" y="80" width="20" height="12" rx="3" {f("on-line-bus")}/><rect x="206" y="108" width="8" height="26" {f("outline")}/>'
        f'<rect x="40" y="158" width="240" height="62" rx="14" {f("surface")}/>'
        + dep_row(56, 168, 96, "tram") + dep_row(56, 194, 70, "bus", 20)),
    # Routes: route banner, then a walk + metro line from you to a flag
    "routes": svg(
        f'<rect x="32" y="30" width="256" height="64" rx="16" {f("route")}/>'
        f'<rect x="48" y="44" width="40" height="12" rx="6" {f("on-route")}/><rect x="48" y="64" width="60" height="18" rx="6" {f("route-accent")}/>'
        f'<rect x="126" y="46" width="110" height="10" rx="5" {f("on-route")}/><rect x="126" y="66" width="30" height="14" rx="4" {f("line-metro")}/><rect x="162" y="68" width="60" height="9" rx="4.5" {f("on-route")}/>'
        f'<path d="M60 178 L118 178" {st("on-surface-variant", 5, ";stroke-dasharray:1 10")}/>'
        f'<path d="M118 178 L250 178" {st("line-metro", 10)}/>'
        f'<circle cx="160" cy="178" r="7" {f("surface")}/><circle cx="205" cy="178" r="7" {f("surface")}/>'
        f'<circle cx="52" cy="178" r="18" {f("primary-container")}/><circle cx="52" cy="178" r="8" {f("primary")}/>'
        f'<path d="M262 196 V140" {st("on-surface", 4)}/><path d="M262 140 L292 150 L262 160 Z" {f("route-accent")}/>'),
    # Resize: a phone with routes above, departures below and a drag bar between
    "resize": svg(
        f'<rect x="96" y="20" width="128" height="200" rx="18" {f("surface")}/>'
        f'<rect x="106" y="32" width="108" height="64" rx="10" {f("route")}/>' + bars(116, 44, [70, 46], "on-route", 8, 8)
        + f'<rect x="136" y="110" width="48" height="8" rx="4" {f("primary")}/>'
        f'<path d="M150 102 L160 94 L170 102 M150 126 L160 134 L170 126" {st("primary", 4)}/>'
        + dep_row(108, 146, 40, "tram", 20) + dep_row(108, 172, 30, "metro", 20) + dep_row(108, 198, 48, "bus", 14)
        + f'<path d="M248 70 V170 M236 84 L248 70 L260 84 M236 156 L248 170 L260 156" {st("on-surface-variant", 4)}/>'),
    # Choose stops: stacked stop cards and a gear
    "stops": svg(
        f'<rect x="40" y="36" width="190" height="72" rx="14" {f("surface")}/>' + bars(56, 50, [96], "on-surface", 10) + dep_row(56, 72, 70, "tram", 22)
        + f'<rect x="40" y="120" width="190" height="72" rx="14" {f("surface")}/>' + bars(56, 134, [120], "on-surface", 10) + dep_row(56, 156, 56, "train", 22)
        + f'<rect x="40" y="204" width="190" height="20" rx="10" {f("outline-variant")}/>'
        + gear(256, 82, 30)
        + f'<circle cx="256" cy="170" r="22" {f("primary-container")}/><path d="M256 160 V180 M246 170 H266" {st("on-primary-container", 5)}/>'),
    # Fastest routes: route banners, the top one highlighted
    "fastest": svg(
        f'<rect x="28" y="34" width="264" height="74" rx="16" {f("route")}/>'
        f'<rect x="44" y="48" width="36" height="10" rx="5" {f("on-route")}/><rect x="44" y="66" width="56" height="26" rx="8" {f("route-accent")}/>'
        f'<rect x="118" y="52" width="104" height="10" rx="5" {f("on-route")}/>'
        f'<rect x="118" y="72" width="16" height="14" rx="4" {f("on-route")}/><rect x="140" y="72" width="30" height="14" rx="4" {f("line-train")}/><rect x="176" y="72" width="30" height="14" rx="4" {f("line-bus")}/>'
        f'<rect x="238" y="58" width="38" height="12" rx="6" {f("on-route")}/>'
        f'<rect x="28" y="122" width="264" height="56" rx="16" {f("surface")}/>'
        f'<rect x="44" y="136" width="40" height="10" rx="5" {f("on-surface-variant")}/><rect x="44" y="152" width="44" height="14" rx="6" {f("primary")}/>'
        f'<rect x="118" y="138" width="90" height="10" rx="5" {f("on-surface")}/><rect x="118" y="154" width="30" height="12" rx="4" {f("line-express")}/>'
        f'<rect x="28" y="190" width="264" height="30" rx="15" {f("outline-variant")}/>'
        f'<circle cx="54" cy="205" r="7" {f("primary")}/>' + bars(70, 201, [110], "on-surface-variant", 8)),
    # Details: rows, a tap ripple, a sheet with a timeline
    "details": svg(
        f'<rect x="36" y="24" width="248" height="120" rx="14" {f("surface")}/>'
        + dep_row(52, 38, 110, "tram") + f'<rect x="44" y="64" width="232" height="30" rx="8" {f("primary-container")}/>' + dep_row(52, 70, 92, "metro")
        + f'<circle cx="120" cy="79" r="16" style="fill:none;stroke:var(--color-primary);stroke-width:3;opacity:.6"/><circle cx="120" cy="79" r="6" {f("primary")}/>'
        + dep_row(52, 104, 70, "bus", 20)
        + f'<path d="M56 150 H264 a20 20 0 0 1 20 20 V232 H36 V170 a20 20 0 0 1 20 -20 Z" {f("surface-container-high")}/>'
        f'<rect x="144" y="158" width="32" height="5" rx="2.5" {f("outline")}/>'
        f'<path d="M70 178 V226" {st("line-metro", 6)}/>'
        f'<circle cx="70" cy="180" r="7" {f("surface")}/><circle cx="70" cy="180" r="4" {f("line-metro")}/><circle cx="70" cy="204" r="5" {f("line-metro")}/>'
        + bars(90, 176, [120], "on-surface", 9) + bars(90, 200, [86], "on-surface-variant", 8)),
    # Screen care: a wall screen, dimmed side, moon and a shift arrow
    "care": svg(
        f'<rect x="42" y="44" width="236" height="150" rx="16" style="fill:var(--color-background);stroke:var(--color-outline);stroke-width:3"/>'
        f'<rect x="62" y="64" width="78" height="26" rx="8" {f("primary")}/>'
        + bars(62, 108, [150, 110, 130], "on-surface-variant", 9, 12)
        + f'<rect x="144" y="194" width="32" height="20" {f("outline")}/><rect x="120" y="212" width="80" height="8" rx="4" {f("outline")}/>'
        f'<circle cx="246" cy="74" r="26" {f("primary")}/><circle cx="260" cy="64" r="24" {f("background")}/>'
        f'<path d="M30 216 H72 M60 206 L72 216 L60 226" {st("on-surface-variant", 4)}/>'),
    # Location rationale: a big pin with rings on a map
    "location": svg(
        f'<path d="M8 80 H312 M8 170 H312 M96 8 V232 M232 8 V232" {st("outline-variant", 12, ";stroke-linecap:butt")}/>'
        f'<circle cx="160" cy="150" r="62" {f("primary-container")}/><circle cx="160" cy="150" r="36" style="fill:var(--color-primary);opacity:.18"/>'
        f'<path d="M160 158 C136 128 122 112 122 92 a38 38 0 0 1 76 0 C198 112 184 128 160 158 Z" {f("primary")}/>'
        f'<circle cx="160" cy="92" r="14" {f("on-primary")}/>'
        f'<circle cx="160" cy="166" r="6" {f("primary")}/>'),
    # Location denied: a muted pin with a slash
    "location-off": svg(
        f'<path d="M8 80 H312 M8 170 H312 M96 8 V232 M232 8 V232" {st("outline-variant", 12, ";stroke-linecap:butt")}/>'
        f'<circle cx="160" cy="132" r="72" {f("surface")}/>'
        f'<path d="M160 168 C136 138 122 122 122 102 a38 38 0 0 1 76 0 C198 122 184 138 160 168 Z" {f("outline")}/>'
        f'<circle cx="160" cy="102" r="14" {f("surface")}/>'
        f'<path d="M116 64 L204 176" {st("surface", 14)}/><path d="M116 64 L204 176" {st("on-surface-variant", 6)}/>'),
    # Camera rationale: front camera, motion lines, and a shield with a check (stays on device)
    "camera": svg(
        f'<rect x="60" y="28" width="132" height="184" rx="20" {f("surface")}/>'
        f'<circle cx="126" cy="54" r="14" {f("primary-container")}/><circle cx="126" cy="54" r="7" {f("primary")}/>'
        f'<rect x="76" y="86" width="100" height="56" rx="10" {f("surface-container-high")}/>' + bars(86, 98, [60, 40], "on-surface-variant", 8, 8)
        + bars(76, 156, [90, 60], "outline-variant", 10, 10)
        + f'<path d="M210 70 q14 22 0 44 M226 58 q24 34 0 68" {st("primary", 5)}/>'
        f'<path d="M240 140 L276 152 V178 C276 198 262 212 240 220 C218 212 204 198 204 178 V152 Z" {f("success-container")}/>'
        f'<path d="M224 180 L236 192 L258 168" {st("on-success-container", 6)}/>'),
    # Camera denied: the same camera with a slash
    "camera-off": svg(
        f'<rect x="94" y="28" width="132" height="184" rx="20" {f("surface")}/>'
        f'<circle cx="160" cy="70" r="26" {f("surface-container-high")}/><circle cx="160" cy="70" r="12" {f("outline")}/>'
        + bars(110, 120, [100, 70, 86], "outline-variant", 10, 12)
        + f'<path d="M112 34 L208 206" {st("surface", 14)}/><path d="M112 34 L208 206" {st("on-surface-variant", 6)}/>'),
}

# ---------------------------------------------------------------- mode
def mode_state(selected):
    def card(key, icon, title, desc, goto):
        sel = selected == key
        cls = "mode-card is-selected" if sel else "mode-card"
        pressed = ' aria-pressed="true"' if sel else ' aria-pressed="false"'
        check = '<i data-icon="check_circle" class="ob-check ic-fill"></i>' if sel else ""
        return (f'<button type="button" class="{cls}"{pressed} data-goto="{goto}">'
                f'<span class="mc-ic"><i data-icon="{icon}"></i></span><b>{title}</b><span>{desc}</span>{check}</button>')
    cards = (card("onspot", "near_me", "On the spot",
                  "Follows where you are: routes to your destinations from right here, and departures from the nearest stop.",
                  "onboarding/intro/onspot-1")
             + card("board", "dashboard", "Wall board",
                    "An always-on display for the stops you choose, with burn-in protection and screen dimming.",
                    "onboarding/intro/board-1"))
    return f'''<div class="app"><div class="ob ob-mode">
  <div class="ob-col">
    <div class="ob-brand"><span class="ob-logo"><i data-icon="departure_board"></i></span><span>PulseTransit</span></div>
    <h1 class="ob-title" id="mode-t-{selected}">How will you use PulseTransit?</h1>
    <div class="ob-cards" role="group" aria-labelledby="mode-t-{selected}">{cards}</div>
    <p class="ob-hint"><i data-icon="info"></i>You can change this later in Settings.</p>
  </div>
</div></div>'''


# ---------------------------------------------------------------- intro
INTRO = {
    "onspot": [
        ("nearest", "Departures from where you are", "The nearest stop’s departures show on their own and follow you as you move. Tap the stop’s name to pick another one nearby."),
        ("routes", "Routes from right here", "Add the places you often go to in Settings, and each gets its next routes from where you stand. For a one-off trip, tap the search button."),
        ("resize", "Make room", "Drag the bar between routes and departures to give either one more space."),
    ],
    "board": [
        ("stops", "Choose your stops", "Open Settings to add up to 6 stops, and pick which directions of each to show. When they don’t all fit, the board takes turns showing them."),
        ("fastest", "Fastest routes", "Add up to 5 destinations in Settings to see the fastest way there from the board’s location, and choose when each one is shown, like weekday mornings."),
        ("details", "Tap for details", "Tap a departure to see its stops and later times, or to hide a line you never take. Tap a route to see every step and get alerts along the way."),
        ("care", "Made to stay on", "Settings has burn-in protection, dimming when no one is around, waking by camera motion and a theme that follows the light in the room."),
    ],
}
FINISH = {"board": "board/main/empty", "onspot": "onboarding/permissions/location"}
MODE_SEL = {"board": "onboarding/mode/selected-board", "onspot": "onboarding/mode/selected-onspot"}


def intro_state(mode, i):
    steps = INTRO[mode]
    n = len(steps)
    ill, title, body = steps[i]
    sid = f"{mode}-{i + 1}"
    last = i == n - 1
    back = (f'<button type="button" class="icon-btn" aria-label="Back" data-goto="{MODE_SEL[mode]}"><i data-icon="arrow_back"></i></button>' if i == 0
            else f'<button type="button" class="icon-btn" aria-label="Back" data-state-to="{mode}-{i}"><i data-icon="arrow_back"></i></button>')
    skip = "" if last else f'<button type="button" class="btn text" data-goto="{FINISH[mode]}">Skip</button>'
    dots = "".join(f'<i class="on"></i>' if k == i else "<i></i>" for k in range(n))
    nxt = (f'<button type="button" class="btn ob-next" data-goto="{FINISH[mode]}">Get started</button>' if last
           else f'<button type="button" class="btn ob-next" data-state-to="{mode}-{i + 2}">Next<i data-icon="arrow_forward"></i></button>')
    return f'''<div class="app"><div class="ob">
  <header class="ob-top">{back}<span class="spacer"></span>{skip}</header>
  <div class="ob-col ob-main" role="group" aria-roledescription="slide" aria-labelledby="in-t-{sid}">
    {ILL[ill]}
    <h1 class="ob-title" id="in-t-{sid}">{title}</h1>
    <p class="ob-text">{body}</p>
  </div>
  <footer class="ob-foot">
    <div class="step-dots" aria-hidden="true">{dots}</div>
    <span class="ob-sr" aria-live="polite">Step {i + 1} of {n}</span>
    {nxt}
  </footer>
</div></div>'''


# ---------------------------------------------------------------- permissions
def perm(ill, title, body, actions, top="", points="", tid="p"):
    return f'''<div class="app"><div class="ob">
  <header class="ob-top">{top}</header>
  <div class="ob-col ob-main">
    {ILL[ill]}
    <h1 class="ob-title" id="{tid}">{title}</h1>
    <p class="ob-text">{body}</p>{points}
  </div>
  <footer class="ob-foot ob-actions">{actions}</footer>
</div></div>'''


def points(items):
    lis = "".join(f'<li><i data-icon="{ic}"></i><span>{t}</span></li>' for ic, t in items)
    return f'\n    <ul class="ob-points">{lis}</ul>'


CLOSE_TO_SETTINGS = '<button type="button" class="icon-btn" aria-label="Back to Settings" data-goto="settings/main/board"><i data-icon="arrow_back"></i></button><span class="ob-top-title">Wake screen by front camera motion</span>'

PERM = [
    ("location", "Location", "Intro finished in On the spot mode, before the system prompt",
     "Rationale first, so the system prompt isn’t a surprise. Allow opens the system prompt; Not now leads to the denied state, which still offers a way forward.",
     perm("location", "Use your location",
          "On the spot finds the nearest stop and plans routes from where you stand. Your position is sent only to Entur’s journey planner, to answer those searches.",
          '<button type="button" class="btn block" data-goto="onspot/main/loaded"><i data-icon="my_location"></i>Allow location</button>'
          '<button type="button" class="btn text block" data-state-to="location-denied">Not now</button>',
          points=points([("near_me", "Precise location gives the nearest stop; approximate still works, with a wider search."),
                         ("settings", "You can turn it off at any time in your phone’s settings.")]), tid="pl-t")),
    ("location-denied", "Location denied", "System prompt denied, or Not now",
     "T5 for this mode: on-spot content can’t work without a position and nothing can stand in for it, so the screen explains and offers two ways forward (system settings, or a fixed location). Never a dead end: switching to Wall board is one tap.",
     perm("location-off", "On the spot needs your location",
          "Location access is needed to show routes and stops from where you are. Turn it on in your phone’s settings, or set a fixed location instead.",
          '<button type="button" class="btn block" data-state-to="location"><i data-icon="open_in_new"></i>Open settings</button>'
          '<button type="button" class="btn outline block" data-goto="settings/main/onspot"><i data-icon="edit_location_alt"></i>Set location manually</button>'
          '<button type="button" class="btn text block" data-goto="onboarding/mode/selected-board">Use as a wall board instead</button>',
          tid="pld-t")),
    ("camera", "Camera", "Settings: user turns on “Wake screen by front camera motion” without camera permission",
     "Shown from Settings, right where the feature is turned on (iOS drops a second prompt that overlaps another). Privacy is the whole question, so it’s answered before the system prompt.",
     perm("camera", "Wake the screen when someone walks by",
          "While the screen is dimmed, the front camera watches for movement and wakes the board.",
          '<button type="button" class="btn block" data-goto="settings/main/board"><i data-icon="photo_camera"></i>Allow camera</button>'
          '<button type="button" class="btn text block" data-goto="settings/main/board">Not now</button>',
          top=CLOSE_TO_SETTINGS,
          points=points([("lock", "Frames never leave this device. Each one is compared with the last and thrown away."),
                         ("videocam_off", "Nothing is recorded, saved or sent anywhere."),
                         ("tune", "Choose how much motion wakes it in Settings, under Camera Sensitivity.")]), tid="pc-t")),
    ("camera-denied", "Camera denied", "System prompt denied (or denied earlier)",
     "Not an error tier: a permission-denied path with a route to system settings. The Settings switch goes back off; while it’s off a T4 callout there repeats “Camera access is off · Open settings”. The board keeps dimming and wakes on touch.",
     perm("camera-off", "Camera access is off",
          "PulseTransit can’t use the front camera, so the board won’t wake by motion. It still dims when the room is quiet and wakes when you touch it.",
          '<button type="button" class="btn block" data-state-to="camera"><i data-icon="open_in_new"></i>Open settings</button>'
          '<button type="button" class="btn text block" data-goto="settings/main/board">Keep it off</button>',
          top=CLOSE_TO_SETTINGS, tid="pcd-t")),
]


def state(sid, label, trigger, note, body):
    n = f' data-note="{note}"' if note else ""
    return f'<div class="v-state" data-state="{sid}" data-label="{label}" data-trigger="{trigger}"{n}>\n{body}\n</div>'


out = ['<!-- Generated from a scratch script; edit by hand freely. -->',
       '<section class="v-page" data-page="onboarding" data-title="Onboarding" data-group="Onboarding">',
       '  <h1>Onboarding</h1>',
       '  <p class="v-lead">First launch: pick a mode, learn the four (or three) things that aren’t obvious, then grant what the mode needs. Primary question: <b>“What is this app going to do for me?”</b> Everything is skippable and reversible in Settings. Phone first; on Fold, Wall and Desktop the content sits in a centred 560 px column with a larger illustration.</p>',
       '  <article class="v-screen" data-screen="mode" data-title="Choose a mode" data-purpose="Primary action: pick how the app is used. Tapping a card continues at once; there is no separate Continue button.">',
       state("empty", "Nothing selected", "First launch (no stops or destinations from an older install)",
             "Judgment call: two big choices, both reversible in Settings, so tapping a card proceeds directly. No Continue button means no disabled button and no “pick one first” error to explain.",
             mode_state(None)),
       state("selected-board", "Wall board selected", "Came back from the wall-board intro",
             "The earlier choice stays highlighted (check + aria-pressed). Tapping either card continues with that mode.", mode_state("board")),
       state("selected-onspot", "On the spot selected", "Came back from the on-the-spot intro",
             "The earlier choice stays highlighted (check + aria-pressed). Tapping either card continues with that mode.", mode_state("onspot")),
       '  </article>',
       '  <article class="v-screen" data-screen="intro" data-title="Intro" data-purpose="Primary action: Next (Get started on the last step). Skip ends the intro at any step; Back goes one step back, or to the mode choice from step 1.">']
order = [("board", 0), ("board", 1), ("board", 2), ("board", 3), ("onspot", 0), ("onspot", 1), ("onspot", 2)]
for mode, i in order:
    n = len(INTRO[mode])
    name = "Wall board" if mode == "board" else "On the spot"
    trig = f"{name} chosen" if i == 0 else f"Next on step {i}"
    note = ""
    if i == n - 1:
        note = ("Last step: no Skip; Get started opens the empty board (it explains how to add stops)." if mode == "board"
                else "Last step: no Skip; Get started goes to the location rationale.")
    elif i == 0:
        note = "Step indicator: dots are decorative; “Step 1 of %d” is read by screen readers and announced on each change." % n
    out.append(state(f"{mode}-{i + 1}", f"{'Board' if mode == 'board' else 'On-spot'} {i + 1}/{n}", trig, note, intro_state(mode, i)))
out.append('  </article>')
out.append('  <article class="v-screen" data-screen="permissions" data-title="Permissions" data-purpose="Explain why before the system prompt asks. Primary action: allow; there is always a way forward without it.">')
for sid, label, trig, note, body in PERM:
    out.append(state(sid, label, trig, note, body))
out.append('  </article>')
out.append('</section>')
open(OUT, "w").write("\n".join(out) + "\n")
print("ok")
