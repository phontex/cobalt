#!/usr/bin/env python3
"""Inspect TTF/OTF fonts inside an .apk or .apks bundle for Cyrillic coverage.

Usage: python3 tools/check_apk_fonts.py <path-to-apk-or-apks>
"""
import io
import struct
import sys
import zipfile
from pathlib import Path

RU_LETTERS = set(range(0x0410, 0x0450)) | {0x0401, 0x0451}


def parse_sfnt(data: bytes):
    if len(data) < 12 or data[:4] not in (b"\x00\x01\x00\x00", b"OTTO", b"true", b"typ1"):
        return None
    num_tables = struct.unpack(">H", data[4:6])[0]
    tables = {}
    for i in range(num_tables):
        off = 12 + i * 16
        if off + 16 > len(data):
            break
        tag = data[off:off + 4].decode("latin1")
        t_off, t_len = struct.unpack(">II", data[off + 8:off + 16])
        tables[tag] = (t_off, t_len)

    family = ""
    if "name" in tables:
        noff, _ = tables["name"]
        _, count, str_off = struct.unpack(">HHH", data[noff:noff + 6])
        for i in range(count):
            rec = noff + 6 + i * 12
            pid, _, _, nid, length, offset = struct.unpack(">HHHHHH", data[rec:rec + 12])
            if nid in (1, 4, 16) and pid == 3:
                s = data[noff + str_off + offset:noff + str_off + offset + length].decode("utf-16-be", "ignore")
                if s and not family:
                    family = s

    codepoints = set()
    if "cmap" in tables:
        coff, _ = tables["cmap"]
        _, nsub = struct.unpack(">HH", data[coff:coff + 4])
        for i in range(nsub):
            pid, _, sub_off = struct.unpack(">HHI", data[coff + 4 + i * 8:coff + 12 + i * 8])
            if pid not in (0, 3):
                continue
            so = coff + sub_off
            fmt = struct.unpack(">H", data[so:so + 2])[0]
            if fmt == 4:
                seg_count = struct.unpack(">H", data[so + 6:so + 8])[0] // 2
                ends = struct.unpack(f">{seg_count}H", data[so + 14:so + 14 + seg_count * 2])
                starts_off = so + 16 + seg_count * 2
                starts = struct.unpack(f">{seg_count}H", data[starts_off:starts_off + seg_count * 2])
                deltas_off = starts_off + seg_count * 2
                deltas = struct.unpack(f">{seg_count}h", data[deltas_off:deltas_off + seg_count * 2])
                ranges_off = deltas_off + seg_count * 2
                ranges = struct.unpack(f">{seg_count}H", data[ranges_off:ranges_off + seg_count * 2])
                for s, e, d, r in zip(starts, ends, deltas, ranges):
                    if s == 0xFFFF:
                        break
                    for cp in range(max(s, 0x0400), min(e, 0x04FF) + 1):
                        if r != 0 or ((cp + d) & 0xFFFF) != 0:
                            codepoints.add(cp)
            elif fmt == 12:
                ngroups = struct.unpack(">I", data[so + 12:so + 16])[0]
                for g in range(ngroups):
                    go = so + 16 + g * 12
                    sc, ec, _ = struct.unpack(">III", data[go:go + 12])
                    if ec < 0x0400 or sc > 0x04FF:
                        continue
                    codepoints.update(range(max(sc, 0x0400), min(ec, 0x04FF) + 1))

    return family, len(RU_LETTERS & codepoints), len(RU_LETTERS)


def scan_apk(zf: zipfile.ZipFile, label: str):
    for item in zf.infolist():
        if not item.filename.lower().endswith((".ttf", ".otf", ".ttc")):
            continue
        res = parse_sfnt(zf.read(item.filename))
        if res:
            fam, ru, tot = res
            print(f"{label} :: {item.filename} ({item.file_size // 1024}KB) | family={fam!r} | russian={ru}/{tot}")
        else:
            print(f"{label} :: {item.filename} ({item.file_size // 1024}KB) | unparsed")


def main():
    if len(sys.argv) < 2:
        raise SystemExit("usage: check_apk_fonts.py <apk_or_apks>")
    path = Path(sys.argv[1])
    with zipfile.ZipFile(path) as zf:
        if path.suffix.lower() == ".apks":
            for name in sorted(zf.namelist()):
                if name.endswith(".apk"):
                    with zipfile.ZipFile(io.BytesIO(zf.read(name))) as inner:
                        scan_apk(inner, name)
        else:
            scan_apk(zf, path.name)


if __name__ == "__main__":
    main()
