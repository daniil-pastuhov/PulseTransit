import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;

//! Drawing helpers shared by the views. Everything is laid out from the screen's size, since the
//! same code runs on a 454 px AMOLED and a 260 px memory-in-pixel display.
module Draw {
    // Fjord's dark theme (docs/design/tokens.json): blue.300 and slate.300 lit, blue.800 and slate.700 unlit.
    const RIDE = 0x7FB0FF;
    const RIDE_DIM = 0x0C2F6B;
    const OTHER = 0xAEB7C4;
    const OTHER_DIM = 0x3A4352;
    const MUTED_TEXT = 0xAEB7C4;

    //! How wide a line of text may be at [y] on a round screen, inside [margin] from its edge.
    function widthAt(dc as Dc, y as Numeric, margin as Numeric) as Number {
        var r = dc.getWidth() / 2.0 - margin;
        var dy = y - dc.getHeight() / 2.0;
        if (dy.abs() >= r) {
            return 0;
        }
        return (2 * Math.sqrt(r * r - dy * dy)).toNumber();
    }

    //! Draws [text] centred with its top at [y] on at most [maxLines] lines, in the first of
    //! [fonts] it fits in whole. When it fits in none: nothing if [strict], else the last font, cut
    //! short with an ellipsis. Each line is as wide as the round screen is across it, so text
    //! near the top or bottom wraps sooner. Returns the height used.
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
            var width = widthAt(dc, y + last * dc.getFontHeight(font) + dc.getFontHeight(font) / 2, margin);
            var line = lines[last] as String;
            while (line.length() > 0 && dc.getTextWidthInPixels(line + "…", font) > width) {
                line = line.substring(0, line.length() - 1) as String;
            }
            lines[last] = line + "…";
        }
        var lineHeight = dc.getFontHeight(font);
        for (var i = 0; i < lines.size(); i++) {
            dc.drawText(dc.getWidth() / 2, y + i * lineHeight, font, lines[i] as String, Graphics.TEXT_JUSTIFY_CENTER);
        }
        return lines.size() * lineHeight;
    }

    //! Marks, as the last element of wrap's result, that words were left over.
    const CUT = "\n";

    //! [words] filled into at most [maxLines] lines from [y] down, each as wide as the screen is
    //! across its middle. Ends with [CUT] when not every word found room.
    function wrap(dc as Dc, words as Array, font as Graphics.FontType, y as Numeric, maxLines as Number, margin as Numeric) as Array {
        var lines = [];
        var lineHeight = dc.getFontHeight(font);
        var next = 0;
        while (next < words.size() && lines.size() < maxLines) {
            var width = widthAt(dc, y + lines.size() * lineHeight + lineHeight / 2, margin);
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
