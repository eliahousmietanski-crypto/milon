#!/usr/bin/env python3
"""Régénère tools/norm_vectors.json : cas de test partagés entre textnorm.py et TextNorm.kt."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from textnorm import fr_words, hebrew_plain, hebrew_skeleton, latin_fold  # noqa: E402

SAMPLES = [
    "בְּרֵאשִׁית", "בָּרָא", "אֱלֹהִים", "שָׁמַיִם", "אֶרֶץ", "בֹּרֵא", "בורא", "קוּדְשָׁא בְּרִיךְ הוּא",
    "תָּא חֲזֵי", "סִטְרָא־אַחֲרָא", "עַתִּיק יוֹמִין", "שְׁכִינְתָּא", "ז״ל", "ר׳ שמעון", "שׁ", "יִשְׂרָאֵל",
    "וַיְהִי", "מִצְוָה", "מצוות", "ײִדיש", "שּׁ", "Œuvre", "créer, façonner", "l'âme (souffle de vie)",
    "Être SAINT", "à l'écart", "ÉLÉVATION", "naïf", "ex nihilo", "ki-tov", "Zohar I 15a",
]

vectors = [
    {
        "in": s,
        "plain": hebrew_plain(s),
        "skeleton": hebrew_skeleton(hebrew_plain(s)),
        "latin": latin_fold(s),
        "fr": fr_words(s),
    }
    for s in SAMPLES
]
out = Path(__file__).resolve().parent / "norm_vectors.json"
out.write_text(json.dumps(vectors, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(f"{len(vectors)} cas → {out}")
