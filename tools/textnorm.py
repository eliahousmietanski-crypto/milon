"""Normalisation du texte pour la recherche.

Ces règles DOIVENT rester identiques à celles de
app/src/main/java/com/milon/app/data/TextNorm.kt : la base est indexée ici,
et l'application normalise la saisie de l'utilisateur de la même façon.
Le fichier tools/norm_vectors.json sert de test commun aux deux.
"""
import re
import unicodedata

FINALS = {"ך": "כ", "ם": "מ", "ן": "נ", "ף": "פ", "ץ": "צ"}
LIGATURES = {"װ": "וו", "ױ": "וי", "ײ": "יי"}

FR_STOPWORDS = set(
    """a au aux avec ce ces cet cette d de des du en et l la le les leur leurs
    lui n ne ou par pas pour qu que qui s sa se ses son sur un une y etc qqch
    qqn quelque chose quelqu""".split()
)


def has_hebrew(s: str) -> bool:
    return any("א" <= c <= "ת" or c in LIGATURES for c in s)


def hebrew_plain(s: str) -> str:
    """Lettres hébraïques seules, sans nikoud ni téamim, finales ramenées à la
    forme normale, mots séparés par une espace."""
    s = unicodedata.normalize("NFKD", s)
    out = []
    for c in s:
        if "א" <= c <= "ת":
            out.append(FINALS.get(c, c))
        elif c in LIGATURES:
            out.append(LIGATURES[c])
        elif c.isspace() or c in "־-":
            out.append(" ")
    return re.sub(" +", " ", "".join(out)).strip()


def hebrew_skeleton(plain: str) -> str:
    """Retire les matres lectionis ו et י (sauf en début de mot) pour que
    l'écriture pleine (כתיב מלא) et défective se retrouvent."""
    words = []
    for w in plain.split(" "):
        if not w:
            continue
        words.append(w[0] + "".join(c for c in w[1:] if c not in "וי"))
    return " ".join(words)


def latin_fold(s: str) -> str:
    s = s.lower().replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
    s = unicodedata.normalize("NFD", s)
    out = []
    for c in s:
        if unicodedata.category(c) == "Mn":
            continue
        out.append(c if ("a" <= c <= "z" or "0" <= c <= "9") else " ")
    return re.sub(" +", " ", "".join(out)).strip()


def fr_words(s: str, keep_stopwords: bool = False):
    words = [w for w in latin_fold(s).split(" ") if w]
    if keep_stopwords:
        return words
    return [w for w in words if w not in FR_STOPWORDS]
