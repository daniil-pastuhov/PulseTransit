import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;

//! Drawing helpers shared by the views. Everything is laid out from the screen's size and shape,
//! since the same code runs on a round 466 px AMOLED, a square Venu and a two-colour 166 px Instinct.
module Draw {
    // Fjord's dark theme (docs/design/tokens.json): blue.300 and slate.300 lit, blue.800 and slate.700 unlit.
    const RIDE = 0x7FB0FF;
    const RIDE_DIM = 0x0C2F6B;
    const OTHER = 0xAEB7C4;
    const OTHER_DIM = 0x3A4352;
    const MUTED_TEXT = 0xAEB7C4;

    //! The part of a semi-octagon screen's width (the Instincts') that each corner's cut takes.
    const CORNER = 0.15;

    function isRound() as Boolean {
        return System.getDeviceSettings().screenShape == System.SCREEN_SHAPE_ROUND;
    }

    var _window as Array<Number>? = null;
    var _windowAsked as Boolean = false;

    //! The Instincts' round window in the corner of the screen as [x, y, width, height], null on
    //! every other watch. Those are also the two-colour screens, so it tells how to draw as well
    //! as where.
    function subscreen() as Array<Number>? {
        if (!_windowAsked) {
            _windowAsked = true;
            var box = (WatchUi has :getSubscreen) ? WatchUi.getSubscreen() : null;
            if (box != null) {
                _window = [box.x as Number, box.y as Number, box.width as Number, box.height as Number];
            }
        }
        return _window;
    }

    function isMono() as Boolean {
        return subscreen() != null;
    }

    //! Secondary text: grey, or white where there are two colours.
    function muted() as Number {
        return isMono() ? Graphics.COLOR_WHITE : MUTED_TEXT;
    }

    //! Where a line [height] tall with its top at [top] may start and end, [margin] inside the
    //! screen's edge: the circle's chord on a round screen, the full width on a square one, less
    //! the cut corners of the Instincts' and the room their window takes.
    function bounds(dc as Dc, top as Numeric, height as Numeric, margin as Numeric) as Array<Number> {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var left = margin.toFloat();
        var right = (w - margin).toFloat();
        if (isRound()) {
            var r = w / 2.0 - margin;
            var dy = top + height / 2.0 - h / 2.0;
            if (dy.abs() >= r) {
                return [w / 2, w / 2];
            }
            var half = Math.sqrt(r * r - dy * dy);
            left = w / 2.0 - half;
            right = w / 2.0 + half;
        } else if (System.getDeviceSettings().screenShape == System.SCREEN_SHAPE_SEMI_OCTAGON) {
            var cut = w * CORNER;
            var inset = 0.0;
            if (top < cut) {
                inset = cut - top;
            } else if (top + height > h - cut) {
                inset = top + height - (h - cut);
            }
            left += inset;
            right -= inset;
        }
        var sub = subscreen();
        // The window's rim is drawn a little outside its box.
        var rim = w / 13.0;
        if (sub != null && top < sub[1] + sub[3] && top + height > sub[1] && sub[0] - rim < right) {
            right = sub[0] - rim;
        }
        if (right < left) {
            right = left;
        }
        return [left.toNumber(), right.toNumber()];
    }

    //! Draws [text] centred with its top at [y] on at most [maxLines] lines, in the first of
    //! [fonts] it fits in whole. When it fits in none: nothing if [strict], else the last font, cut
    //! short with an ellipsis. Each line is as wide as the screen leaves it ([bounds]), and
    //! centred there, so on a round screen text near the top or bottom wraps sooner. Returns the
    //! height used.
    function text(dc as Dc, text as String, fonts as Array, y as Numeric, maxLines as Number, margin as Numeric, strict as Boolean) as Number {
        if (text.length() == 0 || maxLines <= 0) {
            return 0;
        }
        var words = split(text);
        var font = fonts[0] as Graphics.FontType;
        var lines = [];
        var whole = false;
        for (var i = 0; i < fonts.size() && !whole; i++) {
            font = fonts[i] as Graphics.FontType;
            lines = wrap(dc, words, font, y, maxLines, margin);
            whole = !(lines[lines.size() - 1] as String).equals(CUT);
        }
        if (!whole) {
            if (strict) {
                return 0;
            }
            // Drop the marker and end the last line with an ellipsis, shortened until that fits.
            lines = lines.slice(0, lines.size() - 1);
            var last = lines.size() - 1;
            var room = bounds(dc, y + last * dc.getFontHeight(font), dc.getFontHeight(font), margin);
            var width = room[1] - room[0];
            var line = lines[last] as String;
            while (line.length() > 0 && dc.getTextWidthInPixels(line + "…", font) > width) {
                line = line.substring(0, line.length() - 1) as String;
            }
            lines[last] = line + "…";
        }
        var lineHeight = dc.getFontHeight(font);
        for (var i = 0; i < lines.size(); i++) {
            var across = bounds(dc, y + i * lineHeight, lineHeight, margin);
            dc.drawText((across[0] + across[1]) / 2, y + i * lineHeight, font, lines[i] as String, Graphics.TEXT_JUSTIFY_CENTER);
        }
        return lines.size() * lineHeight;
    }

    //! Marks, as the last element of wrap's result, that words were left over.
    const CUT = "\n";

    //! [words] filled into at most [maxLines] lines from [y] down, each as wide as [bounds] leaves it. Ends with [CUT] when not every word found room.
    function wrap(dc as Dc, words as Array, font as Graphics.FontType, y as Numeric, maxLines as Number, margin as Numeric) as Array {
        var lines = [];
        var lineHeight = dc.getFontHeight(font);
        var next = 0;
        while (next < words.size() && lines.size() < maxLines) {
            var across = bounds(dc, y + lines.size() * lineHeight, lineHeight, margin);
            var width = across[1] - across[0];
            // A separator dot belongs to the line it ends, never to the start of the next.
            if ((words[next] as String).equals("·") && next + 1 < words.size() && lines.size() > 0) {
                next++;
            }
            var line = words[next] as String;
            var fits = dc.getTextWidthInPixels(line, font) <= width;
            next++;
            while (fits && next < words.size() && dc.getTextWidthInPixels(line + " " + (words[next] as String), font) <= width) {
                line = line + " " + (words[next] as String);
                next++;
            }
            lines.add(line);
            if (!fits) {
                // One word wider than the line: nothing after it can be placed.
                lines.add(CUT);
                return lines;
            }
        }
        if (next < words.size()) {
            lines.add(CUT);
        }
        return lines;
    }

    //! [text]'s words: what spaces separate.
    function split(text as String) as Array {
        var words = [];
        var rest = text;
        var space = rest.find(" ");
        while (space != null) {
            if (space > 0) {
                words.add(rest.substring(0, space));
            }
            rest = rest.substring(space + 1, rest.length()) as String;
            space = rest.find(" ");
        }
        if (rest.length() > 0) {
            words.add(rest);
        }
        return words;
    }

    //! The time of day of [seconds] as the watch shows times.
    function clock(seconds as Number) as String {
        var info = Gregorian.info(new Time.Moment(seconds), Time.FORMAT_SHORT);
        var hour = info.hour;
        if (!System.getDeviceSettings().is24Hour) {
            hour = hour % 12;
            if (hour == 0) {
                hour = 12;
            }
        }
        return hour.format("%d") + ":" + info.min.format("%02d");
    }

    //! The journey's walks, waits and rides in proportion to their time, the part behind lit: a
    //! ring round a round screen, a bar along the bottom of a square one, and on the Instincts a
    //! ring inside their window, which leaves the few lines below it to the text.
    function progress(dc as Dc, segments as Array, now as Number) as Void {
        var sub = subscreen();
        if (isRound()) {
            ring(dc, segments, now);
        } else if (sub != null) {
            windowRing(dc, sub, segments, now);
        } else {
            bar(dc, segments, now);
        }
    }

    //! How far the journey has come, round the inside of the Instincts' window: a thick arc for
    //! the part behind, a thin circle for the rest. Too small to tell the rides apart.
    function windowRing(dc as Dc, sub as Array<Number>, segments as Array, now as Number) as Void {
        if (segments.size() == 0) {
            return;
        }
        var start = (segments[0] as Array)[1] as Number;
        var end = (segments[segments.size() - 1] as Array)[2] as Number;
        if (end <= start) {
            return;
        }
        var cx = sub[0] + sub[2] / 2;
        var cy = sub[1] + sub[3] / 2;
        var radius = sub[2] / 2 - 3;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(1);
        dc.drawCircle(cx, cy, radius);
        var behind = sweep(now, start, end);
        if (behind >= 1) {
            dc.setPenWidth(4);
            if (behind >= 359) {
                dc.drawCircle(cx, cy, radius - 1);
            } else {
                dc.drawArc(cx, cy, radius - 1, Graphics.ARC_CLOCKWISE, 90, degrees(behind));
            }
            dc.setPenWidth(1);
        }
    }

    function barHeight(dc as Dc) as Number {
        var height = dc.getWidth() / 30;
        return height < 5 ? 5 : height;
    }

    //! The top of [bar], for the views to end their text above.
    function barTop(dc as Dc) as Number {
        return dc.getHeight() - dc.getHeight() / 16 - barHeight(dc);
    }

    //! The journey as a bar: rides in blue, the part behind lit. With two colours the part behind
    //! is the full bar and the part ahead a thin line.
    function bar(dc as Dc, segments as Array, now as Number) as Void {
        if (segments.size() == 0) {
            return;
        }
        var start = (segments[0] as Array)[1] as Number;
        var end = (segments[segments.size() - 1] as Array)[2] as Number;
        if (end <= start) {
            return;
        }
        var top = barTop(dc);
        var height = barHeight(dc);
        var across = bounds(dc, top, height, dc.getWidth() / 12);
        var x0 = across[0];
        var total = across[1] - across[0];
        var gap = segments.size() > 1 ? (dc.getWidth() / 90 < 2 ? 2 : dc.getWidth() / 90) : 0;
        var mono = isMono();
        var thin = height / 4 < 1 ? 1 : height / 4;
        var lit = x0 + sweep(now, start, end) / 360 * total;
        for (var i = 0; i < segments.size(); i++) {
            var segment = segments[i] as Array;
            var ride = (segment[0] as Number) == PlanFormat.SEGMENT_RIDE;
            var from = x0 + sweep(segment[1] as Number, start, end) / 360 * total + gap / 2.0;
            var to = x0 + sweep(segment[2] as Number, start, end) / 360 * total - gap / 2.0;
            if (to - from < 1) {
                continue;
            }
            var done = (lit > to ? to : lit) - from;
            if (mono) {
                dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
                dc.fillRectangle(from, top + (height - thin) / 2, to - from, thin);
                if (done >= 1) {
                    dc.fillRectangle(from, top, done, height);
                }
            } else {
                dc.setColor(ride ? RIDE_DIM : OTHER_DIM, Graphics.COLOR_TRANSPARENT);
                dc.fillRoundedRectangle(from, top, to - from, height, height / 2);
                if (done >= 1) {
                    dc.setColor(ride ? RIDE : OTHER, Graphics.COLOR_TRANSPARENT);
                    dc.fillRoundedRectangle(from, top, done, height, height / 2);
                }
            }
        }
    }

    //! The journey as a ring round the screen, clockwise from the top: one arc per walk, wait and
    //! ride in proportion to its time, rides in blue, the part already behind lit.
    function ring(dc as Dc, segments as Array, now as Number) as Void {
        if (segments.size() == 0) {
            return;
        }
        var start = (segments[0] as Array)[1] as Number;
        var end = (segments[segments.size() - 1] as Array)[2] as Number;
        if (end <= start) {
            return;
        }
        var width = dc.getWidth() / 30;
        var radius = dc.getWidth() / 2 - width / 2 - 1;
        var gap = segments.size() > 1 ? 2.0 : 0.0;
        dc.setPenWidth(width);
        for (var i = 0; i < segments.size(); i++) {
            var segment = segments[i] as Array;
            var ride = (segment[0] as Number) == PlanFormat.SEGMENT_RIDE;
            var from = sweep(segment[1] as Number, start, end) + gap / 2;
            var to = sweep(segment[2] as Number, start, end) - gap / 2;
            var lit = sweep(now, start, end);
            if (lit > to) {
                lit = to;
            }
            dc.setColor(ride ? RIDE_DIM : OTHER_DIM, Graphics.COLOR_TRANSPARENT);
            arc(dc, radius, from, to);
            dc.setColor(ride ? RIDE : OTHER, Graphics.COLOR_TRANSPARENT);
            arc(dc, radius, from, lit);
        }
        dc.setPenWidth(1);
    }

    //! How far round the ring [at] is, in degrees clockwise from the top.
    function sweep(at as Number, start as Number, end as Number) as Float {
        var fraction = (at - start).toFloat() / (end - start);
        if (fraction < 0) {
            fraction = 0.0;
        } else if (fraction > 1) {
            fraction = 1.0;
        }
        return fraction * 360;
    }

    //! An arc between two sweeps. Under a degree nothing is drawn: drawArc takes equal angles for a full circle.
    function arc(dc as Dc, radius as Number, from as Float, to as Float) as Void {
        if (to - from < 1) {
            return;
        }
        // drawArc counts degrees counter-clockwise from 3 o'clock.
        dc.drawArc(dc.getWidth() / 2, dc.getHeight() / 2, radius, Graphics.ARC_CLOCKWISE, degrees(from), degrees(to));
    }

    function degrees(sweep as Float) as Number {
        var angle = 90 - Math.round(sweep).toNumber();
        while (angle < 0) {
            angle += 360;
        }
        return angle % 360;
    }
}
