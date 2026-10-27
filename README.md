# מִלּוֹן · Milon

Dictionnaire **hébreu (לשון הקודש) et araméen → français**, pour Android, utilisable entièrement **hors ligne**.

- Hébreu biblique, hébreu rabbinique (Michna, Talmud, Midrach, Kabbale) et araméen (Targoum Onkelos, araméen biblique, Talmud, Zohar).
- Pour chaque mot : vocalisation, racine, grammaire (binyan), plusieurs sens avec leur contexte, citations sourcées, équivalent hébreu ↔ araméen.
- Recherche en hébreu avec ou sans nikoud (écriture pleine ou défective), par racine, ou en français ; clavier hébreu intégré.
- Propositions d'ajouts par les utilisateurs, validées par l'éditeur, puis distribuées à tous par une mise à jour.

## Installer l'application

1. Ouvrir la page [Releases](../../releases/latest) depuis le téléphone.
2. Télécharger `milon.apk` et l'ouvrir. Android demande d'autoriser l'installation depuis le navigateur : accepter.
3. Pour partager l'application, envoyer le lien de cette page ou le fichier `milon.apk` (WhatsApp, e-mail…).

Le dictionnaire est inclus dans l'application. Quand le téléphone est connecté, l'application vérifie s'il existe une version plus récente et propose « Mettre à jour » en un clic.

## Contributions

**Utilisateurs** : menu ⋮ → *Ajouter un mot*, ou sur une fiche : ⋮ → *Proposer une correction*. Puis *Contributions* → *Envoyer* : un fichier est partagé à l'éditeur (WhatsApp, e-mail…).

**Éditeur** : *Réglages* → activer *Mode éditeur*.
1. Ouvrir le fichier reçu avec Milon (ou *Contributions* → *Importer*).
2. Valider ✓ ou refuser ✗ chaque proposition (on peut la corriger avant).
3. *Publier* : les mots validés sont envoyés dans ce dépôt (`data/contrib/`). La construction automatique produit une nouvelle version du dictionnaire en quelques minutes, et tous les utilisateurs la reçoivent.

La publication depuis le téléphone nécessite un jeton GitHub, à saisir une seule fois dans les Réglages : sur GitHub, *Settings → Developer settings → Fine-grained tokens → Generate new token*, accès limité au dépôt `milon`, autorisation **Contents : Read and write**.

## Organisation du dépôt

| Dossier | Contenu |
|---|---|
| `data/entries/*.jsonl` | Lexique de base, une entrée JSON par ligne |
| `data/contrib/*.jsonl` | Ajouts et corrections publiés depuis l'application (remplacent les entrées de même `id`) |
| `tools/build_db.py` | Vérifie les données et construit la base SQLite de l'application |
| `app/` | Application Android (Kotlin, Jetpack Compose) |
| `.github/workflows/build.yml` | À chaque modification : vérification, base, APK, publication de la release |

### Format d'une entrée

```json
{"id": "hb-bara", "lemma": "בָּרָא", "lang": "hb", "root": "ברא", "pos": "verbe", "binyan": "qal",
 "translit": "bara", "forms": ["בּוֹרֵא"],
 "senses": [{"fr": "créer", "ctx": "sens premier", "note": "…"}],
 "examples": [{"ref": "Berechit 1:1", "src": "בְּרֵאשִׁית בָּרָא אֱלֹהִים…", "fr": "Au commencement, Dieu créa…"}],
 "equiv": ["ar-bera"], "notes": "…", "source": "Milon", "verified": true}
```

- `lang` : `hb` (hébreu biblique), `hr` (hébreu rabbinique), `ar` (araméen).
- `dialect` (facultatif) : corpus, par exemple `Targoum`, `Talmud de Babylone`, `Zohar`.
- `equiv` : `id` des équivalents ; le lien est rendu réciproque automatiquement.
- `verified` : `true` une fois l'entrée relue. Les entrées non relues portent la mention ⓘ dans l'application.
- `"_deleted": true` dans `data/contrib/` supprime l'entrée de même `id`.

Vérifier les données : `python3 tools/build_db.py --check`

## Sources

- Hébreu biblique : Brown–Driver–Briggs (BDB), édition numérique Open Scriptures (CC BY 4.0).
- Araméen et hébreu rabbinique : M. Jastrow, *Dictionary of the Targumim, the Talmud Babli and Yerushalmi, and the Midrashic Literature* (1903, domaine public).
- Vocabulaire du Zohar et premier lot d'entrées : rédaction Milon.

Les traductions françaises sont préparées à partir de ces sources puis relues ; l'import complet du BDB et du Jastrow se fait par lots successifs.
