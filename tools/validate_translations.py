#!/usr/bin/env python3
"""Read-only validation of translation sources.

Checks, for every translations/<package>/ directory:
  1. placeholders (printf, {0}/{name}, HTML tags) are preserved in every
     non-empty value of the editable files;
  2. App-layer keys (legacy/dex/assets/server) do not overlap with
     Platform-layer static resources (resources.json keys and the literal
     source values from platform/source-records.json);
  3. generated ru.json files are in sync with their editable sources.

Usage: python3 tools/validate_translations.py [translations_dir]
Exit code 1 if any placeholder error or generated-file drift is found.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

PRINTF = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")
BRACE = re.compile(r"\{[A-Za-z0-9_]*\}")
TAG = re.compile(
    r"</?(?:a|b|i|u|em|strong|br|p|div|span|font|small|big|sup|sub|tt|img|li|ul|ol|h[1-6]|xliff:g)"
    r"(?:\s[^>]*)?/?>")
APP_FILES = ("legacy", "dex", "assets", "server")


def tokens(text):
    printf = Counter(re.sub(r"\d+\$", "", m) for m in PRINTF.findall(text))
    return printf, Counter(BRACE.findall(text)), Counter(TAG.findall(text))


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def collect_strings(obj, out):
    if isinstance(obj, str):
        out.add(obj)
    elif isinstance(obj, list):
        for item in obj:
            collect_strings(item, out)
    elif isinstance(obj, dict):
        for item in obj.values():
            collect_strings(item, out)


def check_package(pkg_dir, errors, warnings):
    pkg = pkg_dir.name
    app_entries = {}
    for name in APP_FILES:
        path = pkg_dir / "app" / f"{name}.json"
        if path.exists():
            app_entries[name] = load(path).get("entries", {})
    res_path = pkg_dir / "platform" / "resources.json"
    platform_entries = load(res_path).get("entries", {}) if res_path.exists() else {}

    groups = [(f"app/{n}", e) for n, e in app_entries.items()]
    groups.append(("platform/resources", platform_entries))
    for label, entries in groups:
        for src, dst in entries.items():
            if not dst:
                continue
            if tokens(src) != tokens(dst):
                errors.append(f"{pkg} {label}: placeholder mismatch {src!r} -> {dst!r}")

    static_values = set()
    rec_path = pkg_dir / "platform" / "source-records.json"
    if rec_path.exists():
        collect_strings(load(rec_path).get("records", []), static_values)
    static_values |= set(platform_entries)
    for name, entries in app_entries.items():
        for src, dst in entries.items():
            if src not in static_values:
                continue
            plat = platform_entries.get(src)
            if plat is not None and dst and plat != dst:
                warnings.append(
                    f"{pkg} app/{name} vs platform: different values for {src!r}: "
                    f"{dst!r} / {plat!r}")
            elif name == "server":
                warnings.append(f"{pkg} app/server key exists in static APK resources: {src!r}")
            else:
                warnings.append(f"{pkg} app/{name} key also static resource: {src!r}")

    for layer, files in (("app", APP_FILES), ("platform", ("resources",))):
        gen_path = pkg_dir / layer / "ru.json"
        if not gen_path.exists():
            continue
        gen = load(gen_path).get("entries", {})
        merged = {}
        for name in files:
            path = pkg_dir / layer / f"{name}.json"
            if path.exists():
                merged.update({k: v for k, v in load(path).get("entries", {}).items() if v})
        extra = [k for k in gen if k not in merged]
        if layer == "platform" and extra:
            errors.append(f"{pkg} platform/ru.json has {len(extra)} keys absent from resources.json")
        for k, v in gen.items():
            if k in merged and merged[k] != v:
                errors.append(f"{pkg} {layer}/ru.json drift for {k!r}")
        if layer == "app":
            leaked = [k for k in gen if k in static_values]
            if leaked:
                errors.append(f"{pkg} app/ru.json contains {len(leaked)} Platform-owned keys")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    root = Path(args[0]) if args else Path(__file__).resolve().parent.parent / "translations"
    errors, warnings = [], []
    for pkg_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        check_package(pkg_dir, errors, warnings)
    for e in errors:
        print("ERROR", e)
    verbose = "--verbose" in sys.argv
    differing = [w for w in warnings if "different values" in w]
    if verbose:
        for w in warnings:
            print("WARN ", w)
    elif "--quiet" not in sys.argv:
        for w in differing:
            print("WARN ", w)
    print(f"errors: {len(errors)}, overlaps: {len(warnings)} "
          f"(different values: {len(differing)}; use --verbose for all)")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
