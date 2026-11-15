#!/usr/bin/env python3
"""Build Cobalt's translation layers, catalog metadata, and human changelog.

Editable files live under translations/<package>/app|platform/*.json. Generated ru.json files
are the only files downloaded by Cobalt. The script is intentionally stdlib-only so GitHub Actions
and a local Termux checkout produce the same bytes and SHA-256 values.
"""
from __future__ import annotations
import argparse, datetime as dt, hashlib, json, os, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "manifests" / "catalog.json"
PACKAGES = ("com.heytap.themestore", "com.oplus.themestore")
APP_SOURCES = ("legacy.json", "dex.json", "assets.json", "server.json")

def read_json(path: Path, default=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return {} if default is None else default

def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def active_entries(value):
    entries = value.get("entries", {}) if isinstance(value, dict) else {}
    return {str(k): str(v) for k, v in entries.items()
            if isinstance(k, str) and isinstance(v, str) and k and v}

def build_layer(package: str, layer: str):
    directory = ROOT / "translations" / package / layer
    if layer == "app":
        sections = {}
        merged = {}
        for filename in APP_SOURCES:
            section = filename[:-5]
            entries = active_entries(read_json(directory / filename))
            sections[section] = entries
            # Source order is deliberate: a server override wins over legacy/dex/assets.
            merged.update(entries)
        document = {
            "schema_version": 3,
            "package": package,
            "layer": "app",
            "language": "ru",
            "language_name": "Русский",
            "generated": True,
            "source_files": list(APP_SOURCES),
            "sections": sections,
            "entries": merged,
            "counts": {name: len(values) for name, values in sections.items()},
            "notes": [
                "This file is generated. Edit app/legacy.json, dex.json, assets.json or server.json.",
                "Empty server entries are placeholders and are not applied.",
            ],
        }
    else:
        source = read_json(directory / "resources.json")
        entries = active_entries(source)
        document = {
            "schema_version": 3,
            "package": package,
            "layer": "platform",
            "language": "ru",
            "language_name": "Русский",
            "generated": True,
            "source_files": ["resources.json", "source-records.json", "conflicts.json"],
            "sections": {"resources": entries},
            "entries": entries,
            "counts": {"resources": len(entries)},
            "notes": [
                "This file is generated. Edit platform/resources.json.",
                "Cobalt patches matching strings in the installed resources.arsc and then asks Platform to mount it.",
            ],
        }
    output = directory / "ru.json"
    write_json(output, document)
    raw = output.read_bytes()
    return output, document, hashlib.sha256(raw).hexdigest(), len(raw)

def git_parent():
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD^"], cwd=ROOT, text=True).strip()
    except subprocess.CalledProcessError:
        return None

def old_entries(revision, path):
    if not revision:
        return {}
    try:
        raw = subprocess.check_output(["git", "show", f"{revision}:{path.as_posix()}"], cwd=ROOT)
        return active_entries(json.loads(raw.decode("utf-8")))
    except (subprocess.CalledProcessError, json.JSONDecodeError):
        return {}

def changed_source_files(parent):
    if not parent:
        return []
    try:
        names = subprocess.check_output(
            ["git", "diff", "--name-only", parent, "HEAD", "--", "translations"], cwd=ROOT, text=True
        ).splitlines()
    except subprocess.CalledProcessError:
        return []
    return [Path(name) for name in names if name.endswith(".json") and Path(name).name != "ru.json"]

def diff_entries(before, after):
    added = sorted(set(after) - set(before))
    removed = sorted(set(before) - set(after))
    changed = sorted(k for k in set(before) & set(after) if before[k] != after[k])
    return added, changed, removed

def markdown_change(parent, revision, stamp):
    files = changed_source_files(parent)
    if not files:
        return None, None
    lines = [f"# Изменения переводов — {stamp}", "", f"Коммит-источник: `{revision[:12]}`", "",
             "Этот журнал создан автоматически. Пустые серверные заготовки не считаются активным переводом.", ""]
    summary = {"files": 0, "added": 0, "changed": 0, "removed": 0, "sections": []}
    for path in files:
        current = active_entries(read_json(ROOT / path))
        before = old_entries(parent, path)
        added, changed, removed = diff_entries(before, current)
        if not (added or changed or removed):
            continue
        summary["files"] += 1
        package = path.parts[1] if len(path.parts) > 1 else "unknown"
        area = "/".join(path.parts[2:-1])
        section = path.stem
        lines += [f"## {package} · {area} · `{path.name}`", ""]
        if added:
            lines += [f"### Добавлено ({len(added)})", ""]
            for key in added:
                lines.append(f"- `{key}` → `{current[key]}`")
            lines.append("")
        if changed:
            lines += [f"### Изменено ({len(changed)})", ""]
            for key in changed:
                lines.append(f"- `{key}`: `{before[key]}` → `{current[key]}`")
            lines.append("")
        if removed:
            lines += [f"### Удалено ({len(removed)})", ""]
            for key in removed:
                lines.append(f"- `{key}` (было: `{before[key]}`)")
            lines.append("")
        summary["added"] += len(added); summary["changed"] += len(changed); summary["removed"] += len(removed)
        summary["sections"].append(f"{package}/{area}/{section}")
    if summary["files"] == 0:
        return None, None
    name = f"{stamp}-{revision[:8]}.md"
    path = ROOT / "changes" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    return path, summary

def update_index(change_path, summary, stamp):
    if not change_path or not summary:
        return
    index_path = ROOT / "changes" / "index.json"
    index = read_json(index_path, {"schema_version": 1, "entries": []})
    entries = index.get("entries", [])
    entries.insert(0, {
        "date": stamp,
        "path": change_path.relative_to(ROOT).as_posix(),
        "summary": summary,
    })
    index["entries"] = entries[:100]
    index["latest"] = entries[0]
    write_json(index_path, index)

def update_catalog(layer_info, change_path, summary, stamp):
    catalog = read_json(CATALOG)
    catalog["schema_version"] = 3
    catalog["updated"] = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    catalog["changelog"] = {
        "path": "changes/index.json",
        "latest": change_path.relative_to(ROOT).as_posix() if change_path else catalog.get("changelog", {}).get("latest", ""),
        "updated": stamp,
    }
    for app in catalog.get("apps", []):
        package = app.get("package")
        if package not in layer_info:
            continue
        for layer in app.get("layers", []):
            if layer.get("served_by") == "app":
                info = layer_info[package]["app"]
                layer.update({
                    "kind": "runtime",
                    "path": info["path"], "sha256": info["sha256"], "bytes": info["bytes"],
                    "entries": info["entries"], "content_id": info["sha256"][:8],
                    "available": True, "changelog_path": "changes/index.json",
                    "change_summary": info["summary"],
                })
            elif layer.get("served_by") == "platform":
                info = layer_info[package]["platform"]
                layer.update({
                    "kind": "resource_map", "path": info["path"], "entry": "resources.arsc",
                    "sha256": info["sha256"], "bytes": info["bytes"], "entries": info["entries"],
                    "content_id": info["sha256"][:8], "available": info["entries"] > 0,
                    "changelog_path": "changes/index.json", "change_summary": info["summary"],
                })
                # Keep the old payload shape populated for schema-2 readers, but it now points to text.
                layer["payload"] = {"path": info["path"], "entry": "resources.arsc",
                                     "sha256": info["sha256"], "bytes": info["bytes"],
                                     "available": info["entries"] > 0}
    write_json(CATALOG, catalog)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--no-changelog", action="store_true")
    parser.add_argument("--initial", action="store_true")
    args = parser.parse_args()
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")
    info = {}
    for package in PACKAGES:
        info[package] = {}
        for layer in ("app", "platform"):
            output, document, digest, size = build_layer(package, layer)
            info[package][layer] = {
                "path": output.relative_to(ROOT).as_posix(), "sha256": digest,
                "bytes": size, "entries": len(active_entries(document)),
                "summary": "Синхронизация редактируемого каталога" if args.initial else "Обновление переводов",
            }
    change_path = summary = None
    if not args.no_changelog and not args.initial:
        parent = git_parent()
        revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        change_path, summary = markdown_change(parent, revision, stamp)
        update_index(change_path, summary, stamp)
    if summary:
        text = f"Добавлено {summary['added']}, изменено {summary['changed']}, удалено {summary['removed']} записей"
        for package in info:
            for layer in info[package]: info[package][layer]["summary"] = text
    update_catalog(info, change_path, summary, stamp)
    print(json.dumps({"layers": info, "changelog": str(change_path) if change_path else None}, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
