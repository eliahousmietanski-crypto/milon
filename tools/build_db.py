#!/usr/bin/env python3
"""Construit la base SQLite du dictionnaire à partir des fichiers JSONL.

Sources lues, dans cet ordre (une entrée plus tardive remplace une entrée de
même « id ») :
  data/entries/*.jsonl   lexique de base
  data/contrib/*.jsonl   ajouts et corrections validés (publiés depuis l'appli)

Usage :
  python3 tools/build_db.py --out out --data-version 12
  python3 tools/build_db.py --check          # valide seulement les données
"""
import argparse
import gzip
import hashlib
import json
import os
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from textnorm import fr_words, hebrew_plain, hebrew_skeleton, latin_fold  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
LANGS = {"hb", "hr", "ar"}

# Types de clés de recherche (même valeurs que SearchKind dans l'appli)
K_LEMMA, K_SKELETON, K_FORM, K_ROOT, K_FR, K_TRANSLIT = 1, 2, 3, 4, 5, 6

ENTRY_FIELDS = [
    "id", "lemma", "lang", "dialect", "root", "pos", "binyan", "gender",
    "translit", "forms", "senses", "examples", "equiv", "notes", "source",
    "verified", "author",
]


class DataError(Exception):
    pass


def load_entries():
    files = sorted((ROOT / "data" / "entries").glob("*.jsonl")) + sorted(
        (ROOT / "data" / "contrib").glob("*.jsonl")
    )
    entries = {}
    errors = []
    for f in files:
        for n, line in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            line = line.strip()
            if not line or line.startswith("//"):
                continue
            where = f"{f.relative_to(ROOT)}:{n}"
            try:
                e = json.loads(line)
            except json.JSONDecodeError as ex:
                errors.append(f"{where}: JSON invalide ({ex})")
                continue
            uid = e.get("id")
            if not uid:
                errors.append(f"{where}: champ « id » manquant")
                continue
            if e.get("_deleted"):
                entries.pop(uid, None)
                continue
            try:
                entries[uid] = clean_entry(e)
            except DataError as ex:
                errors.append(f"{where} ({uid}): {ex}")
    if errors:
        raise DataError("\n".join(errors))
    return entries


def clean_entry(e):
    for k in ("lemma", "lang"):
        if not isinstance(e.get(k), str) or not e[k].strip():
            raise DataError(f"champ « {k} » manquant")
    if e["lang"] not in LANGS:
        raise DataError(f"langue inconnue « {e['lang']} » (attendu : hb, hr, ar)")
    senses = e.get("senses") or []
    if not senses or not all(isinstance(s, dict) and s.get("fr", "").strip() for s in senses):
        raise DataError("au moins un sens avec une traduction « fr » est requis")
    unknown = set(e) - set(ENTRY_FIELDS) - {"_deleted"}
    if unknown:
        raise DataError(f"champs inconnus : {', '.join(sorted(unknown))}")
    out = {k: e[k] for k in ENTRY_FIELDS if e.get(k) not in (None, "", [])}
    out["senses"] = [{k: v.strip() for k, v in s.items() if isinstance(v, str) and v.strip()} for s in senses]
    out["examples"] = [
        {k: v.strip() for k, v in x.items() if isinstance(v, str) and v.strip()}
        for x in e.get("examples") or []
        if isinstance(x, dict)
    ]
    if not out["examples"]:
        del out["examples"]
    return out


def resolve_equivalents(entries):
    """Rend les équivalences symétriques et remplace les id par
    {id, lemma, lang} pour l'affichage."""
    links = {uid: set() for uid in entries}
    missing = []
    for uid, e in entries.items():
        for other in e.get("equiv", []):
            if other in entries and other != uid:
                links[uid].add(other)
                links[other].add(uid)
            else:
                missing.append(f"{uid} → {other}")
    for uid, e in entries.items():
        if links[uid]:
            e["equiv"] = [
                {"id": o, "lemma": entries[o]["lemma"], "lang": entries[o]["lang"]}
                for o in sorted(links[uid], key=lambda o: (entries[o]["lang"], entries[o]["lemma"]))
            ]
        else:
            e.pop("equiv", None)
    return missing


def search_keys(e):
    keys = set()
    lemma = hebrew_plain(e["lemma"])
    keys.add((lemma, K_LEMMA, 0))
    keys.add((hebrew_skeleton(lemma), K_SKELETON, 0))
    for form in e.get("forms", []):
        p = hebrew_plain(form)
        if p:
            keys.add((p, K_FORM, 0))
            keys.add((hebrew_skeleton(p), K_SKELETON, 1))
    if e.get("root"):
        keys.add((hebrew_plain(e["root"]).replace(" ", ""), K_ROOT, 0))
    for i, s in enumerate(e["senses"]):
        words = fr_words(s["fr"])
        for pos, w in enumerate(words):
            # Poids : un mot seul en tête du premier sens est le meilleur résultat
            weight = min(i, 5) * 4 + min(pos, 3) + (0 if len(words) <= 2 else 1)
            keys.add((w, K_FR, weight))
    for w in latin_fold(e.get("translit", "")).split(" "):
        if w:
            keys.add((w, K_TRANSLIT, 0))
    # Une même clé ne garde que son meilleur poids
    best = {}
    for key, kind, w in keys:
        if key and ((key, kind) not in best or w < best[(key, kind)]):
            best[(key, kind)] = w
    return [(k, kind, w) for (k, kind), w in best.items()]


def build(out_dir: Path, data_version: int):
    entries = load_entries()
    missing = resolve_equivalents(entries)
    for m in missing:
        print(f"avertissement : équivalence introuvable {m}", file=sys.stderr)

    out_dir.mkdir(parents=True, exist_ok=True)
    db_path = out_dir / "milon.db"
    if db_path.exists():
        db_path.unlink()
    con = sqlite3.connect(db_path)
    con.executescript(
        """
        PRAGMA page_size = 4096;
        CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT);
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY,
            uid TEXT NOT NULL UNIQUE,
            lemma TEXT NOT NULL,
            plain TEXT NOT NULL,
            lang TEXT NOT NULL,
            root TEXT,
            root_plain TEXT,
            pos TEXT,
            gloss TEXT NOT NULL,
            verified INTEGER NOT NULL,
            json TEXT NOT NULL
        );
        CREATE TABLE keys (key TEXT NOT NULL, kind INTEGER NOT NULL, entry INTEGER NOT NULL, w INTEGER NOT NULL);
        """
    )
    order = sorted(entries.values(), key=lambda e: (hebrew_plain(e["lemma"]), e["lang"], e["id"]))
    for rowid, e in enumerate(order, 1):
        root_plain = hebrew_plain(e["root"]).replace(" ", "") if e.get("root") else None
        gloss = " ; ".join(s["fr"] for s in e["senses"][:3])
        con.execute(
            "INSERT INTO entries VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            (
                rowid, e["id"], e["lemma"], hebrew_plain(e["lemma"]), e["lang"],
                e.get("root"), root_plain, e.get("pos"), gloss,
                1 if e.get("verified") else 0,
                json.dumps(e, ensure_ascii=False, separators=(",", ":")),
            ),
        )
        con.executemany(
            "INSERT INTO keys VALUES (?,?,?,?)", [(k, kind, rowid, w) for k, kind, w in search_keys(e)]
        )
    con.executescript(
        """
        CREATE INDEX keys_key ON keys (key, kind);
        CREATE INDEX entries_root ON entries (root_plain);
        """
    )
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    con.executemany(
        "INSERT INTO meta VALUES (?,?)",
        [("data_version", str(data_version)), ("entries", str(len(order))), ("built", now)],
    )
    con.commit()
    con.execute("VACUUM")
    con.close()

    gz_path = out_dir / "milon.db.gz"
    with open(db_path, "rb") as src, gzip.open(gz_path, "wb", compresslevel=9) as dst:
        dst.write(src.read())
    sha = hashlib.sha256(gz_path.read_bytes()).hexdigest()
    counts = {lang: sum(1 for e in order if e["lang"] == lang) for lang in sorted(LANGS)}
    print(f"{len(order)} entrées ({counts}) → {db_path} ({db_path.stat().st_size // 1024} Ko)")
    return {"entries": len(order), "sha256": sha, "built": now, "counts": counts}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="out")
    ap.add_argument("--data-version", type=int, default=1)
    ap.add_argument("--check", action="store_true", help="valider les données sans construire")
    ap.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", "eliahousmietanski-crypto/milon"))
    ap.add_argument("--tag", help="tag de la release (pour les URL du manifeste)")
    ap.add_argument("--app-version-code", type=int)
    ap.add_argument("--app-version-name")
    args = ap.parse_args()

    try:
        if args.check:
            entries = load_entries()
            for m in resolve_equivalents(entries):
                print(f"avertissement : équivalence introuvable {m}", file=sys.stderr)
            print(f"OK : {len(entries)} entrées valides")
            return
        info = build(Path(args.out), args.data_version)
    except DataError as ex:
        print(f"Erreurs dans les données :\n{ex}", file=sys.stderr)
        sys.exit(1)

    if args.tag:
        base = f"https://github.com/{args.repo}/releases/download/{args.tag}"
        manifest = {
            "dataVersion": args.data_version,
            "entries": info["entries"],
            "counts": info["counts"],
            "built": info["built"],
            "dbUrl": f"{base}/milon.db.gz",
            "dbSha256": info["sha256"],
            "appVersionCode": args.app_version_code,
            "appVersionName": args.app_version_name,
            "apkUrl": f"{base}/milon.apk",
        }
        (Path(args.out) / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
        )


if __name__ == "__main__":
    main()
