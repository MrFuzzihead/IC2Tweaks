#!/usr/bin/env python3
"""Offline IC2-internals compatibility scanner for 1.7.10 mod packs.

Reads every jar in the given mods directories (no full extraction) and looks for references that
matter to IC2Tweaks' packet-model work:

  * ic2/core/energy          -- the energy net internals we mix into (critical)
  * ic2/core/block/wiring    -- cables (high)
  * ic2/core/coremod         -- IC2's own coremod namespace (high)
  * other ic2/core/*         -- machine/tile internals (medium; delivery-change exposure)
  * ic2/api/energy           -- API consumers (informational; expected)
  * classes shipped under ic2/core/** by another jar (package injection, high)
  * mixin configs / AT files / coremod manifests that mention IC2

Usage:
  py tools/scan_ic2_refs.py --pack "Label=C:/path/to/mods" [--pack ...] \
      --json build/recon/scan.json --md COMPAT_RECON.md

The JSON output is the raw record; the Markdown output is the review artifact.
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import sys
import zipfile

TOOL_VERSION = "1.0"

# Byte patterns matched against class constant pools and text files. Class refs use '/', annotation
# values sometimes use '.'; the character classes accept both.
SLASH_OR_DOT = rb"[/.]"
INTERNAL = rb"ic2" + SLASH_OR_DOT + rb"core" + SLASH_OR_DOT
PATTERNS = {
    "energy": re.compile(INTERNAL + rb"energy", re.I),
    "wiring": re.compile(INTERNAL + rb"block" + SLASH_OR_DOT + rb"wiring", re.I),
    "coremod": re.compile(INTERNAL + rb"coremod", re.I),
    "machines": re.compile(INTERNAL + rb"block" + SLASH_OR_DOT + rb"(?:machine|tileentity|reactor)", re.I),
    "api_energy": re.compile(rb"ic2" + SLASH_OR_DOT + rb"api" + SLASH_OR_DOT + rb"energy", re.I),
    "any_core": re.compile(INTERNAL, re.I),
}
CORE_TOKEN = re.compile(INTERNAL + rb"[A-Za-z0-9_$/.]*", re.I)
ENERGY_TOKEN = re.compile(INTERNAL + rb"energy[A-Za-z0-9_$/.]*", re.I)
WIRING_TOKEN = re.compile(INTERNAL + rb"block" + SLASH_OR_DOT + rb"wiring[A-Za-z0-9_$/.]*", re.I)
COREMOD_TOKEN = re.compile(INTERNAL + rb"coremod[A-Za-z0-9_$/.]*", re.I)
MACHINE_TOKEN = re.compile(
    INTERNAL + rb"block" + SLASH_OR_DOT + rb"(?:machine|tileentity|reactor)[A-Za-z0-9_$/.]*", re.I
)
API_TOKEN = re.compile(rb"ic2" + SLASH_OR_DOT + rb"api" + SLASH_OR_DOT + rb"energy" + rb"[/.][A-Za-z0-9_$/.]*", re.I)
MIXIN_MARKERS = (b"org/spongepowered/asm/mixin", b"Lorg/spongepowered/asm/mixin/Mixin;")
MAX_CLASS_BYTES = 8 * 1024 * 1024
MAX_TEXT_BYTES = 4 * 1024 * 1024
MAX_TOKENS = 12


def normalize(token):
    return token.replace("/", ".")


def token_list(blob, pattern, limit=MAX_TOKENS):
    seen = []
    for match in pattern.finditer(blob):
        token = normalize(match.group(0).decode("latin-1"))
        if token not in seen:
            seen.append(token)
            if len(seen) >= limit:
                break
    return seen


def manifest_fields(archive):
    try:
        raw = archive.read("META-INF/MANIFEST.MF")
    except KeyError:
        return {}
    text = raw.decode("utf-8", "ignore").replace("\r\n", "\n").replace("\r", "\n")
    # Unfold continuation lines (leading space).
    text = re.sub(r"\n ", "", text)
    fields = {}
    for line in text.split("\n"):
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        fields.setdefault(key.strip().lower(), []).append(value.strip())
    return fields


def first(fields, key):
    values = fields.get(key.lower())
    return values[0] if values else ""


def scan_jar(path, label, sha256):
    name = os.path.basename(path)
    result = {
        "pack": label,
        "jar": name,
        "size": os.path.getsize(path),
        "sha256": sha256,
        "severity": "none",
        "mechanisms": [],
        "energy": [],
        "wiring": [],
        "coremod_refs": [],
        "machines": [],
        "any_core": [],
        "api_energy": [],
        "mixin_classes": [],
        "mixin_configs": [],
        "at_files": [],
        "injected_core": [],
        "injected_api": [],
        "nested_jars": [],
        "coremod": "",
        "tweak": "",
        "error": "",
    }

    try:
        archive = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError) as exc:
        result["error"] = str(exc)
        return result

    with archive:
        fields = manifest_fields(archive)
        result["coremod"] = first(fields, "FMLCorePlugin")
        result["tweak"] = first(fields, "TweakClass")
        mixin_configs = []
        for value in fields.get("mixinconfigs", []):
            for part in re.split(r"[,\s]+", value):
                if part:
                    mixin_configs.append(part.strip())
        result["manifest_mixin_configs"] = mixin_configs

        core_tokens = set()
        api_tokens = set()
        has_mixins = False
        modifying_ic2 = False
        ic2_mixins = []

        for info in archive.infolist():
            entry = info.filename
            lower = entry.lower()

            if entry.startswith("ic2/core/"):
                result["injected_core"].append(entry)
                continue
            if entry.startswith("ic2/api/"):
                result["injected_api"].append(entry)
                continue
            if lower.endswith(".jar") and entry != name:
                result["nested_jars"].append(entry)
                continue

            if not (lower.endswith(".class") or lower.endswith(".json") or lower.endswith(".cfg")
                    or lower.endswith(".txt") or lower.endswith(".xml")):
                continue
            if info.file_size > (MAX_CLASS_BYTES if lower.endswith(".class") else MAX_TEXT_BYTES):
                continue

            try:
                blob = archive.read(entry)
            except (KeyError, OSError):
                continue

            if lower.endswith(".class"):
                class_is_mixin = any(marker in blob for marker in MIXIN_MARKERS)
                if class_is_mixin:
                    has_mixins = True
                    if PATTERNS["any_core"].search(blob) and ("mixins/" in entry.lower() or "mixin" in entry.lower()):
                        ic2_mixins.append(entry[:-6].replace("/", "."))
                class_has_core = any(
                    PATTERNS[k].search(blob) for k in ("energy", "wiring", "coremod", "machines", "any_core")
                )
                looks_like_transformer = class_is_mixin or b"IClassTransformer" in blob or re.search(
                    r"(?i)(^|/)(asm|hooks?|transformer|coremod)(/|$)", entry
                ) is not None
                if class_has_core and looks_like_transformer:
                    modifying_ic2 = True
                for key in ("energy", "wiring", "coremod_refs", "machines", "any_core", "api_energy"):
                    pattern_key = "coremod" if key == "coremod_refs" else key
                    if PATTERNS[pattern_key].search(blob):
                        if key == "energy":
                            result[key].extend(token_list(blob, ENERGY_TOKEN))
                        elif key == "wiring":
                            result[key].extend(token_list(blob, WIRING_TOKEN))
                        elif key == "coremod_refs":
                            result[key].extend(token_list(blob, COREMOD_TOKEN))
                        elif key == "machines":
                            result[key].extend(token_list(blob, MACHINE_TOKEN))
                        elif key == "api_energy":
                            api_tokens.update(token_list(blob, API_TOKEN, limit=3))
                        else:
                            core_tokens.update(token_list(blob, CORE_TOKEN))
            else:
                text = blob.decode("utf-8", "ignore")
                if "ic2" not in text.lower():
                    continue
                if lower.endswith("_at.cfg"):
                    result["at_files"].append(entry)
                if lower.endswith(".json") and ("mixins" in lower or "mixin" in lower):
                    if not any(entry == config for config in result["mixin_configs"]):
                        result["mixin_configs"].append(entry)
                for key in ("energy", "wiring", "coremod_refs", "machines", "any_core", "api_energy"):
                    pattern_key = "coremod" if key == "coremod_refs" else key
                    if PATTERNS[pattern_key].search(blob):
                        if key == "energy":
                            result[key].extend(token_list(blob, ENERGY_TOKEN))
                        elif key == "wiring":
                            result[key].extend(token_list(blob, WIRING_TOKEN))
                        elif key == "coremod_refs":
                            result[key].extend(token_list(blob, COREMOD_TOKEN))
                        elif key == "machines":
                            result[key].extend(token_list(blob, MACHINE_TOKEN))
                        elif key == "api_energy":
                            api_tokens.update(token_list(blob, API_TOKEN, limit=3))
                        else:
                            core_tokens.update(token_list(blob, CORE_TOKEN))

        for key in ("energy", "wiring", "coremod_refs", "machines", "any_core"):
            result[key] = unique_sorted(result[key])

        # The energy/wiring/coremod patterns already imply any_core; keep any_core as the broad bucket.
        result["any_core"] = unique_sorted(list(core_tokens))
        result["api_energy"] = unique_sorted(list(api_tokens))
        result["has_mixins"] = has_mixins
        result["mixin_classes"] = unique_sorted(ic2_mixins)

        modifies_ic2 = modifying_ic2 or bool(result["at_files"]) or bool(result["injected_core"])
        result["modifying_ic2"] = modifying_ic2

        if result["mixin_classes"]:
            result["mechanisms"].append("mixin")
        if result["coremod"]:
            result["mechanisms"].append("coremod")
        if result["at_files"]:
            result["mechanisms"].append("access-transformer")
        if result["injected_core"]:
            result["mechanisms"].append("package-injection")

        if result["injected_core"]:
            if any(entry.startswith("ic2/core/energy") for entry in result["injected_core"]):
                result["severity"] = "critical"
            else:
                result["severity"] = "high"
        elif result["energy"]:
            result["severity"] = "critical"
        elif modifies_ic2 and result["wiring"]:
            result["severity"] = "high"
        elif modifies_ic2 and (result["any_core"] or result["machines"]):
            result["severity"] = "medium"
        elif result["wiring"] or result["any_core"] or result["machines"]:
            result["severity"] = "medium"
        elif result["api_energy"]:
            result["severity"] = "api"
        else:
            result["severity"] = "none"

    return result


def unique_sorted(values):
    seen = []
    for value in values:
        if value and value not in seen:
            seen.append(value)
    seen.sort()
    return seen[:MAX_TOKENS]


def sha256_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def scan_pack(label, directory, skip_names):
    jars = sorted(
        entry for entry in os.listdir(directory)
        if entry.lower().endswith(".jar")
        and not any(entry.lower().startswith(prefix.lower()) for prefix in skip_names)
    )
    results = []
    for index, entry in enumerate(jars, 1):
        path = os.path.join(directory, entry)
        print(f"[{label}] {index}/{len(jars)} {entry}", file=sys.stderr, flush=True)
        results.append(scan_jar(path, label, sha256_file(path)))
    return {"label": label, "directory": directory, "jar_count": len(jars), "results": results}


def short(values, limit=4):
    if not values:
        return ""
    shown = values[:limit]
    suffix = f" (+{len(values) - limit})" if len(values) > limit else ""
    return "<br>".join(f"`{value}`" for value in shown) + suffix


def render_markdown(packs, generated):
    lines = []
    lines.append("# IC2 Internals Compatibility Scan")
    lines.append("")
    lines.append(f"Generated: {generated}")
    lines.append("")
    lines.append(
        "Offline scan of the production mod packs for references to IC2 internals that the packet-model "
        "work touches. The scanner reads every class constant pool, mixin config, access transformer and "
        "manifest entry in each jar; it does not execute any mod code."
    )
    lines.append("")
    lines.append("## Severity legend")
    lines.append("")
    lines.append("| Severity | Meaning |")
    lines.append("|---|---|")
    lines.append("| critical | References `ic2/core/energy` or injects classes under `ic2/core/energy` — direct overlap with our hooks |")
    lines.append("| high | **Modifies** cabling internals (`ic2/core/block/wiring`) via mixin/coremod/AT, or injects other `ic2/core/**` classes |")
    lines.append("| medium | References or mixins other IC2 internals (machines, reactors, items, cables) — delivery/behavior exposure rather than a hook conflict |")
    lines.append("| api | Only uses `ic2/api/energy` (expected; delivery-change exposure, not a hook conflict) |")
    lines.append("")
    lines.append("\"Modifies\" means a mixin class, coremod transformer, access transformer or package injection; a jar that merely "
                 "extends or calls an IC2 internal class is at most medium.")
    lines.append("")

    lines.append("## Summary")
    lines.append("")
    lines.append("| Pack | Jars | critical | high | medium | api | none |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|")
    for pack in packs:
        counts = {key: 0 for key in ("critical", "high", "medium", "api", "none")}
        for result in pack["results"]:
            counts[result["severity"]] = counts.get(result["severity"], 0) + 1
        lines.append(
            f"| {pack['label']} | {pack['jar_count']} | {counts['critical']} | {counts['high']} | "
            f"{counts['medium']} | {counts['api']} | {counts['none']} |"
        )
    lines.append("")

    lines.append("## Baseline conclusion")
    lines.append("")
    all_results = [result for pack in packs for result in pack["results"]]
    energy_hits = [result for result in all_results if result["energy"]]
    high_hits = [result for result in all_results if result["severity"] == "high"]
    if energy_hits:
        lines.append(
            "* `ic2/core/energy` references found in: "
            + ", ".join(f"`{result['jar']}`" for result in energy_hits)
            + ". Review before enabling the packet model."
        )
    else:
        lines.append(
            "* No jar references `ic2/core/energy` in this snapshot. The packet-model hooks "
            "(`Grid`, `EnergyNetLocal.processChanges`, `getNodeStats`) have no static conflict in these packs."
        )
    if high_hits:
        lines.append(
            "* Cabling-internal modifications to inspect: "
            + ", ".join(f"`{result['jar']}`" for result in high_hits)
            + "."
        )
    else:
        lines.append("* No jar was classified as modifying `ic2/core/block/wiring`.")
    modifiers = sorted({result["jar"] for result in all_results if result["modifying_ic2"]})
    if modifiers:
        lines.append(
            "* Jars with mixin/transformer/hook classes that reference IC2 internals: "
            + ", ".join(f"`{jar}`" for jar in modifiers)
            + ". These are the ones to re-check against our hooks and delivery changes."
        )
    lines.append(
        "* Medium rows are addons/mixins that reference IC2 machines, reactors, items or cables; treat them "
        "as delivery-change canaries. Plain class references are not hook conflicts."
    )
    lines.append("")

    for pack in packs:
        lines.append(f"## {pack['label']}")
        lines.append("")
        lines.append(f"Directory: `{pack['directory']}` — {pack['jar_count']} jars.")
        lines.append("")
        for severity, title in (
            ("critical", "Critical findings"),
            ("high", "High findings"),
            ("medium", "Medium findings"),
        ):
            hits = [result for result in pack["results"] if result["severity"] == severity]
            if not hits:
                continue
            lines.append(f"### {title}")
            lines.append("")
            lines.append("| Jar | Mechanisms | IC2 internals referenced | Notes |")
            lines.append("|---|---|---|---|")
            for result in hits:
                refs = short(result["energy"] or result["wiring"] or result["any_core"] or result["machines"])
                notes = []
                if result["injected_core"]:
                    notes.append(f"injects {len(result['injected_core'])} ic2/core classes")
                if result["at_files"]:
                    notes.append("AT: " + short(result["at_files"], 2))
                if result["coremod"]:
                    notes.append(f"coremod `{result['coremod']}`")
                if result["mixin_classes"]:
                    notes.append(f"{len(result['mixin_classes'])} mixin classes")
                lines.append(
                    f"| `{result['jar']}` | {', '.join(result['mechanisms']) or 'bytecode'} | {refs} | "
                    f"{'; '.join(notes)} |"
                )
            lines.append("")

        api_hits = [result for result in pack["results"] if result["severity"] == "api"]
        if api_hits:
            lines.append("### API consumers (informational)")
            lines.append("")
            lines.append(
                "These jars only reference `ic2/api/energy`; no hook conflict is expected, but they are "
                "subject to the packet model's delivery changes (multi-injection, demand caps, source "
                "throttling, `NodeStats` semantics)."
            )
            lines.append("")
            lines.append(", ".join(f"`{result['jar']}`" for result in api_hits))
            lines.append("")

    lines.append("## Scan limitations")
    lines.append("")
    lines.append(
        "* Static analysis only: a coremod that matches classes/methods by obfuscated name or computes "
        "target names at runtime can be missed. ASM transformers are detected only through literal IC2 "
        "class-name references."
    )
    lines.append(
        "* Nested jars are listed in the JSON but not scanned recursively; none were expected to matter "
        "for IC2 internals, but check the list before release."
    )
    lines.append(
        "* This pack snapshot is authoritative only for this snapshot; re-run the scanner after every "
        "pack update and before the Phase 4 default flip."
    )
    lines.append("")
    lines.append("## Follow-up")
    lines.append("")
    lines.append("* Inspect every critical/high hit by hand and add a row to the compatibility matrix.")
    lines.append("* For medium hits, decide whether the mod is an IC2 addon (delivery-change canary) or only "
                 "uses internals for its own classes.")
    lines.append("* Re-run on the secondary packs; compare the JSON (hashes identify the same jar).")
    lines.append("* Re-check after Phase 2 lands and again at Phase 4.")
    lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", action="append", required=True, metavar="LABEL=PATH",
                        help="mods directory to scan; repeat for multiple packs")
    parser.add_argument("--json", default="build/recon/ic2-scan.json", help="raw JSON output")
    parser.add_argument("--md", default="COMPAT_RECON.md", help="Markdown report output")
    parser.add_argument("--skip", action="append", default=["industrialcraft-2"],
                        help="jar name prefix to skip (default: industrialcraft-2); repeatable")
    args = parser.parse_args()

    packs = []
    for spec in args.pack:
        if "=" not in spec:
            parser.error(f"--pack must be LABEL=PATH, got {spec!r}")
        label, directory = spec.split("=", 1)
        if not os.path.isdir(directory):
            parser.error(f"not a directory: {directory}")
        packs.append((label, directory))

    generated = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
    scanned = []
    for label, directory in packs:
        scanned.append(scan_pack(label, directory, set(args.skip)))

    for output in (args.json, args.md):
        parent = os.path.dirname(output)
        if parent:
            os.makedirs(parent, exist_ok=True)

    payload = {
        "tool_version": TOOL_VERSION,
        "generated": generated,
        "packs": scanned,
    }
    with open(args.json, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, sort_keys=True)
    with open(args.md, "w", encoding="utf-8") as handle:
        handle.write(render_markdown(scanned, generated))

    print(f"wrote {args.json} and {args.md}")


if __name__ == "__main__":
    main()
