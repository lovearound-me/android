#!/usr/bin/env python3
"""Every tr("…") of the apps has a translation in every language.

    scripts/check-i18n.py          # exit 1 and a list if not

The texts are keyed by the Russian (shared/src/main/assets/i18n/*.json, copied
from the iPhone app's i18n/); new ones go in with tools/add-i18n.py of the
lovearound folder. The watch app reads the same files.
"""
import json, os, re, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
i18n = os.path.join(root, "shared/src/main/assets/i18n")
keys = {}
for d in ("app/src/main/java", "wear/src/main/java"):
    for dirpath, _, files in os.walk(os.path.join(root, d)):
        for f in files:
            if f.endswith(".kt"):
                p = os.path.join(dirpath, f)
                for m in re.finditer(r'\btr\(\s*"((?:[^"\\$]|\\.)*)"', open(p, encoding="utf-8").read()):
                    key = m.group(1).encode().decode("unicode_escape").encode("latin-1").decode("utf-8")
                    keys.setdefault(key, os.path.relpath(p, root))
bad = 0
for f in sorted(os.listdir(i18n)):
    lang = f[:-5]
    data = json.load(open(os.path.join(i18n, f), encoding="utf-8"))
    for k, where in sorted(keys.items()):
        if k not in data:
            print(f"{lang}: {k!r} ({where})")
            bad += 1
        else:
            names = set(re.findall(r"\{(\w+)\}", k))
            got = set(re.findall(r"\{(\w+)\}", data[k] if isinstance(data[k], str) else ""))
            if isinstance(data[k], str) and names != got:
                print(f"{lang}: {k!r} has {sorted(got)}, wants {sorted(names)}")
                bad += 1
print(f"{len(keys)} texts, {bad} problems")
sys.exit(1 if bad else 0)
