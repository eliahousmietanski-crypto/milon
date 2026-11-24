#!/usr/bin/env python3
"""Génère les entrées de l'hébreu et de l'araméen bibliques à partir du lexique
de Strong (Open Scriptures) et des traductions françaises de data/sources/strong-fr.tsv.

Entrées :
  data/sources/strong.jsonl   lexique de Strong (mot, nature, racine, fréquence, 1re occurrence)
  data/sources/strong-fr.tsv  traduction française : « H1254<TAB>sens 1 ; [contexte] sens 2 ; … »
  data/entries/0*.jsonl       entrées rédigées à la main (prioritaires)
Sorties :
  data/entries/10-bible-strong.jsonl
  data/formes/correspondances.tsv   numéro Strong → id d'une entrée rédigée à la main

Usage : python3 tools/import_strong.py
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from textnorm import hebrew_plain  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
POS = {
    "n-m": "nom masc.", "n-f": "nom fém.", "n": "nom", "n-m n-f": "nom masc. et fém.",
    "v": "verbe", "a": "adjectif", "a-m": "adjectif", "a-f": "adjectif", "adv": "adverbe",
    "prep": "préposition", "conj": "conjonction", "inj": "interjection", "i": "interjection",
    "d": "démonstratif", "p": "pronom", "r": "pronom relatif", "prt": "particule",
    "n-pr-m": "nom propre masc.", "n-pr-f": "nom propre fém.", "n-pr-loc": "nom de lieu",
    "n-pr": "nom propre", "n-pr-m n-pr-loc": "nom propre et nom de lieu",
    "n-pr-loc n-pr-m": "nom de lieu et nom propre", "n-pr-m n-pr-f": "nom propre",
    "v n-m": "verbe ; nom masc.", "a n-m": "adjectif ; nom masc.",
}
# Le Tétragramme n'est pas écrit en toutes lettres comme entrée
DIVINE_NAME = {"H3068", "H3069"}


def is_verb(pos):
    return bool(pos) and pos.split()[0] in ("v", "verbe")


def parse_senses(text):
    senses = []
    for part in text.split(" ; "):
        part = part.strip()
        if not part:
            continue
        ctx = None
        if part.startswith("[") and "]" in part:
            ctx, part = part[1:part.index("]")].strip(), part[part.index("]") + 1:].strip()
        if part:
            senses.append({"fr": part, **({"ctx": ctx} if ctx else {})})
    return senses


def main():
    strong = [json.loads(l) for l in (ROOT / "data/sources/strong.jsonl").read_text(encoding="utf-8").splitlines() if l]
    fr = {}
    tsv = ROOT / "data/sources/strong-fr.tsv"
    for n, line in enumerate(tsv.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        sid, _, text = line.partition("\t")
        if not text.strip():
            sys.exit(f"{tsv.name}:{n} : traduction vide pour {sid}")
        fr[sid.strip()] = text.strip()

    # Entrées rédigées à la main : on ne les duplique pas, on leur rattache les formes bibliques
    manual = {}
    for f in sorted((ROOT / "data/entries").glob("0*.jsonl")):
        for line in f.read_text(encoding="utf-8").splitlines():
            if line.strip():
                e = json.loads(line)
                if e["lang"] in ("hb", "ar"):
                    manual.setdefault((hebrew_plain(e["lemma"]), e["lang"], is_verb(e.get("pos"))), e["id"])

    out, links, missing, linked = [], [], 0, set()
    for s in strong:
        sid = s["id"]
        lang = "ar" if s["lang"] == "arc" else "hb"
        pos = POS.get(s["pos"], s["pos"] or None)
        key = (hebrew_plain(s["lemma"]), lang, is_verb(s["pos"]))
        # Seul l'homographe le plus fréquent est rattaché ; les autres (אוֹר « lumière » / אוּר « flamme »)
        # gardent leur propre entrée
        if key in manual and key not in linked:
            linked.add(key)
            links.append((sid, manual[key], s["count"]))
            continue
        if sid not in fr:
            missing += 1
            continue
        e = {"id": f"{lang}-{sid.lower()}", "lemma": s["lemma"], "lang": lang}
        if lang == "ar":
            e["dialect"] = "Araméen biblique"
        if s.get("root"):
            e["root"] = s["root"]
        if pos:
            e["pos"] = pos
        if s.get("translit"):
            e["translit"] = s["translit"]
        e["senses"] = parse_senses(fr[sid])
        if s.get("first"):
            e["examples"] = [s["first"]]
        if sid in DIVINE_NAME:
            e["lemma"] = "ה׳"
            e["forms"] = ["יהוה"]
            e.pop("translit", None)
            e.pop("examples", None)
            e["notes"] = "Le Tétragramme, nom propre de Dieu, ne se prononce pas : on lit Adonaï (« mon Seigneur »), ou Hachem (« le Nom »)."
        e["strong"] = sid
        e["freq"] = s["count"]
        e["source"] = "Strong (1890), Open Scriptures — traduction Milon"
        out.append(e)

    with open(ROOT / "data/entries/10-bible-strong.jsonl", "w", encoding="utf-8") as f:
        for e in out:
            f.write(json.dumps(e, ensure_ascii=False) + "\n")
    with open(ROOT / "data/formes/correspondances.tsv", "w", encoding="utf-8") as f:
        f.write("# Numéro Strong → entrée rédigée à la main, fréquence (généré par tools/import_strong.py)\n")
        for sid, eid, count in links:
            f.write(f"{sid}\t{eid}\t{count}\n")
    print(f"{len(out)} entrées générées, {len(links)} rattachées à des entrées existantes, {missing} sans traduction")


if __name__ == "__main__":
    main()
