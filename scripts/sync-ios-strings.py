#!/usr/bin/env python3
"""Generates the iOS string catalog from the shared Compose string resources.

ui/src/commonMain/composeResources/values*/strings.xml (Android-format XML, used by the Android and
desktop apps through Compose Multiplatform resources) stay the single source of truth: edit those,
then run this script and commit the regenerated iosApp/iosApp/Localizable.xcstrings. Keys are the
Android resource names; Swift looks them up with L("key", args...).

The widget extension gets a catalog of its own with the widget_* strings,
iosApp/JourneyActivity/Localizable.xcstrings: the system reads a widget's configuration titles (App
Intents) from the extension's bundle. Everything the widgets draw goes through L(), from the app's.

    scripts/sync-ios-strings.py          # rewrite the catalog
    scripts/sync-ios-strings.py --check  # exit 1 if the catalog is out of date (for CI)
"""

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "ui/src/commonMain/composeResources"
# Each catalog, with which keys it holds.
OUTPUTS = [
    (ROOT / "iosApp/iosApp/Localizable.xcstrings", lambda name: True),
    (ROOT / "iosApp/JourneyActivity/Localizable.xcstrings", lambda name: name.startswith("widget_")),
]
# Android resource qualifier -> Apple language code. English is the development language.
LANGUAGES = {"values": "en", "values-nb": "nb"}


def to_apple_format(text):
    """Android's escapes and format specifiers to Foundation's."""
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@")
    # Strings are passed as Swift Strings (%@) and integers as Int, which is 64-bit (%lld).
    text = re.sub(r"%(\d+\$)?s", lambda m: f"%{m.group(1) or ''}@", text)
    text = re.sub(r"%(\d+\$)?d", lambda m: f"%{m.group(1) or ''}lld", text)
    return text


def unit(value):
    return {"stringUnit": {"state": "translated", "value": value}}


def read(qualifier):
    root = ET.parse(RES / qualifier / "strings.xml").getroot()
    entries = {}
    for element in root:
        name = element.get("name")
        if element.get("translatable") == "false" and qualifier != "values":
            continue
        if element.tag == "string":
            entries[name] = unit(to_apple_format("".join(element.itertext())))
        elif element.tag == "plurals":
            forms = {item.get("quantity"): unit(to_apple_format("".join(item.itertext()))) for item in element}
            entries[name] = {"variations": {"plural": forms}}
    return entries


def build(keep):
    strings = {}
    for qualifier, language in LANGUAGES.items():
        for name, localization in read(qualifier).items():
            if not keep(name):
                continue
            entry = strings.setdefault(name, {"extractionState": "manual", "localizations": {}})
            entry["localizations"][language] = localization
    catalog = {"sourceLanguage": "en", "strings": dict(sorted(strings.items())), "version": "1.0"}
    return json.dumps(catalog, indent=2, ensure_ascii=False) + "\n"


def main():
    for output, keep in OUTPUTS:
        generated = build(keep)
        if "--check" in sys.argv:
            if not output.exists() or output.read_text(encoding="utf-8") != generated:
                sys.exit(f"{output.relative_to(ROOT)} is out of date; run scripts/sync-ios-strings.py")
            continue
        output.write_text(generated, encoding="utf-8")
        print(f"Wrote {output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
