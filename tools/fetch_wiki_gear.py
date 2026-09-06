#!/usr/bin/env python3
"""
fetch_wiki_gear.py — build-time data extractor for the Slayer Prep Check plugin.

Pulls the "Recommended equipment" tables from the OSRS Wiki for every Slayer task
category and writes them to a JSON file that gets bundled with the plugin.
The plugin itself never contacts the wiki at runtime.

Usage:
    python fetch_wiki_gear.py --out ../src/main/resources/slayerprep/gear.json
    python fetch_wiki_gear.py --selftest          # parser test, no network

Only the Python standard library is used.

Data source: Old School RuneScape Wiki (https://oldschool.runescape.wiki),
licensed CC BY-NC-SA 3.0. Keep the attribution in the plugin README.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone

API_URL = "https://oldschool.runescape.wiki/api.php"
# The wiki asks for a descriptive User-Agent with contact info. Change this.
USER_AGENT = "SlayerPrepCheck-data-extractor/0.1 (RuneLite plugin; https://github.com/billderksen/slayer-prep-check)"

# Task names exactly as RuneLite's Slayer plugin (and the in-game DB table) uses them.
TASKS = [
    "Aberrant spectres", "Abyssal demons", "Ankou", "Aquanites", "Araxytes", "Aviansies",
    "Bandits", "Banshees", "Basilisks", "Bats", "Bears", "Birds", "Black demons",
    "Black dragons", "Black Knights", "Bloodveld", "Blue dragons", "Brine rats",
    "Catablepon", "Cave bugs", "Cave crawlers", "Cave horrors", "Cave kraken",
    "Cave slimes", "Chaos druids", "Cockatrice", "Cows", "Crabs", "Crawling hands",
    "Crocodiles", "Custodian Stalkers", "Dagannoth", "Dark beasts", "Dark warriors",
    "Dogs", "Drakes", "Dust devils", "Dwarves", "Earth warriors", "Elves", "Ents",
    "Fever spiders", "Fire giants", "Fleshcrawlers", "Fossil island wyverns",
    "Frost dragons", "Gargoyles", "Ghosts", "Ghouls", "Goblins", "Greater demons",
    "Green dragons", "Gryphons", "Harpie bug swarms", "Hellhounds", "Hill giants",
    "Hobgoblins", "Hydras", "Icefiends", "Ice giants", "Ice warriors",
    "Infernal mages", "Jellies", "Jungle horrors", "Kalphites", "Killerwatts",
    "Kurask", "Lava Dragons", "Lesser demons", "Lesser Nagua", "Lizardmen", "Lizards",
    "Magic axes", "Mammoths", "Metal dragons", "Minotaurs", "Mogres", "Molanisks",
    "Monkeys", "Moss giants", "Mutated zygomites", "Nechryael", "Ogres",
    "Otherworldly beings", "Pirates", "Pyrefiends", "Rats", "Red dragons",
    "Revenants", "Rockslugs", "Rogues", "Scabarites", "Scorpions", "Sea snakes",
    "Shades", "Shadow warriors", "Skeletal wyverns", "Skeletons", "Smoke devils",
    "Sourhogs", "Spiders", "Spiritual creatures", "Suqahs", "Terror dogs", "Trolls",
    "Turoth", "Tzhaar", "Vampyres", "Venators", "Wall beasts", "Warped Creatures",
    "Waterfiends", "Werewolves", "Wolves", "Wyrms", "Zombies",
]

# Boss tasks: the wiki keeps their gear on "<Boss>/Strategies".
BOSS_TASKS = {
    "The Abyssal Sire": "Abyssal Sire", "The Alchemical Hydra": "Alchemical Hydra",
    "Araxxor": "Araxxor", "Barrows Brothers": "Barrows", "Callisto": "Callisto",
    "Cerberus": "Cerberus", "The Chaos Elemental": "Chaos Elemental",
    "The Chaos Fanatic": "Chaos Fanatic", "Crazy Archaeologists": "Crazy archaeologist",
    "Dagannoth Kings": "Dagannoth Kings", "Deranged Archaeologist": "Deranged archaeologist",
    "Duke Sucellus": "Duke Sucellus", "General Graardor": "General Graardor",
    "The Giant Mole": "Giant Mole", "The Grotesque Guardians": "Grotesque Guardians",
    "TzTok-Jad": "TzTok-Jad", "The Kalphite Queen": "Kalphite Queen",
    "The King Black Dragon": "King Black Dragon", "The Cave Kraken Boss": "Kraken",
    "Kree'arra": "Kree'arra", "K'ril Tsutsaroth": "K'ril Tsutsaroth",
    "The Maggot King": "Maggot King", "The Phantom Muspah": "Phantom Muspah",
    "Sarachnis": "Sarachnis", "Scorpia": "Scorpia", "The Shellbane Gryphon": "Shellbane Gryphon",
    "The Leviathan": "The Leviathan", "The Whisperer": "The Whisperer",
    "The Thermonuclear Smoke Devil": "Thermonuclear smoke devil", "Vardorvis": "Vardorvis",
    "Venenatis": "Venenatis", "Vet'ion": "Vet'ion", "Vorkath": "Vorkath",
    "Commander Zilyana": "Commander Zilyana", "TzKal-Zuk": "TzKal-Zuk", "Zulrah": "Zulrah",
}

# Tasks whose gear lives on a page that cannot be derived from the task name.
# Verified against Special:WhatLinksHere/Template:Recommended_equipment.
ALT_PAGES = {
    "Cave kraken": ["Kraken/Strategies"],
    "Custodian Stalkers": ["Slayer task/Custodian stalker"],
    "TzTok-Jad": ["TzHaar Fight Cave/Strategies"],
    "TzKal-Zuk": ["Inferno/Strategies"],
}

# Monsters that count for a task and have their own "<Name>/Strategies" equipment table.
# Verified against the "Monster variants" tables on the wiki's "Slayer task/<task>" pages.
VARIANTS = {
    "Abyssal demons": ["Abyssal Sire"],
    "Araxytes": ["Araxxor"],
    "Aviansies": ["Kree'arra"],
    "Basilisks": ["Basilisk Knight"],
    "Bears": ["Callisto", "Artio"],
    "Black demons": ["Demonic gorilla", "Skotizo", "K'ril Tsutsaroth"],
    "Black dragons": ["Brutal black dragon", "King Black Dragon"],
    "Blue dragons": ["Vorkath"],
    "Dagannoth": ["Dagannoth Kings"],
    "Fire giants": ["Royal Titans"],
    "Gargoyles": ["Grotesque Guardians"],
    "Ghosts": ["Revenants"],
    "Greater demons": ["Tormented Demon", "Skotizo", "K'ril Tsutsaroth"],
    "Hellhounds": ["Cerberus", "Vet'ion", "Calvar'ion"],
    "Hydras": ["Alchemical Hydra"],
    "Ice giants": ["Royal Titans"],
    "Kalphites": ["Kalphite Queen"],
    "Lesser Nagua": ["Amoxliatl"],
    "Lizardmen": ["Lizardman shaman"],
    "Metal dragons": ["Mithril dragon", "Adamant dragon", "Rune dragon"],
    "Monkeys": ["Maniacal monkey", "Demonic gorilla"],
    "Rats": ["Scurrius"],
    "Scorpions": ["Scorpia"],
    "Skeletons": ["Vet'ion", "Calvar'ion"],
    "Smoke devils": ["Thermonuclear smoke devil"],
    "Spiders": ["Sarachnis", "Venenatis", "Spindel", "Araxxor"],
    "Vampyres": ["Vyrewatch Sentinel"],
    "Zombies": ["Vorkath"],
}

# Generic slayer gear, used by the plugin when a task has no table of its own.
GENERIC_PAGE = "Slayer training"

# Task-required items from the plugin's TaskRequirements class; listed here so their item IDs
# (used for icons) end up in the data file too. Keep in sync with TaskRequirements.java.
EXTRA_ITEMS = [
    "Nose peg", "Earmuffs", "Facemask", "Spiny helmet", "Reinforced goggles", "Slayer helmet", "Slayer helmet (i)",
    "Mirror shield", "V's shield", "Ice cooler", "Bag of salt", "Rock hammer", "Granite hammer", "Rock thrownhammer",
    "Fungicide spray", "Fungicide", "Leaf-bladed spear", "Leaf-bladed sword", "Leaf-bladed battleaxe", "Broad arrows",
    "Broad bolts", "Amethyst broad bolts", "Slayer's staff", "Slayer's staff (e)", "Witchwood icon", "Bullseye lantern",
    "Kandarin headgear", "Bruma torch", "Firemaking cape", "Slayer gloves", "Bug lantern", "Insulated boots",
    "Slayer bell", "Fishing explosive", "Spade", "Boots of stone", "Granite boots", "Brimstone boots",
    "Elemental shield", "Mind shield", "Dragonfire shield", "Dragonfire ward", "Ancient wyvern shield",
    "Anti-dragon shield", "Antifire potion", "Extended antifire", "Super antifire potion", "Extended super antifire",
    "Antidote+", "Antidote++", "Anti-venom", "Anti-venom+", "Sanfew serum", "Shayzien armour", "Wolfbane",
    "Ivandis flail", "Blisterwood flail", "Blisterwood sickle", "Rod of ivandis",
]

INFOBOX_ID_RE = re.compile(r"^\|\s*id\d*\s*=\s*([0-9]+)", re.MULTILINE)

# Equipment slots the template uses. Anything else ("hidecol", "style", ...) is an option, not a slot.
SLOTS = {"head", "neck", "cape", "body", "legs", "weapon", "shield", "2h", "ammo", "hands", "feet", "ring", "special"}

TEMPLATE_RE = re.compile(r"\{\{\s*recommended[ _]equipment\b", re.IGNORECASE)
HEADING_RE = re.compile(r"^\s*(={2,6})\s*(.+?)\s*\1\s*$", re.MULTILINE)
SLOT_PARAM_RE = re.compile(r"^([a-z0-9]+?)(\d+)$")


# ---------------------------------------------------------------- wikitext parsing

def find_template_blocks(text):
    """Yield (start, end) of every {{Recommended equipment ...}} block, brace-balanced."""
    for m in TEMPLATE_RE.finditer(text):
        start = m.start()
        depth = 0
        i = start
        while i < len(text) - 1:
            two = text[i:i + 2]
            if two == "{{":
                depth += 1
                i += 2
                continue
            if two == "}}":
                depth -= 1
                i += 2
                if depth == 0:
                    yield start, i
                    break
                continue
            i += 1


def split_top_level(body):
    """Split template body on '|' that are not inside nested {{ }} or [[ ]]."""
    parts, buf, depth_t, depth_l = [], [], 0, 0
    i = 0
    while i < len(body):
        two = body[i:i + 2]
        if two == "{{":
            depth_t += 1; buf.append(two); i += 2; continue
        if two == "}}":
            depth_t -= 1; buf.append(two); i += 2; continue
        if two == "[[":
            depth_l += 1; buf.append(two); i += 2; continue
        if two == "]]":
            depth_l -= 1; buf.append(two); i += 2; continue
        c = body[i]
        if c == "|" and depth_t == 0 and depth_l == 0:
            parts.append("".join(buf)); buf = []
        else:
            buf.append(c)
        i += 1
    parts.append("".join(buf))
    return parts


def parse_params(block):
    inner = block[2:-2]  # strip {{ }}
    parts = split_top_level(inner)
    params = {}
    for p in parts[1:]:  # parts[0] is the template name
        if "=" not in p:
            continue
        k, v = p.split("=", 1)
        params[k.strip().lower()] = v.strip()
    return params


def strip_templates(s, keep):
    """Remove every {{...}} block (brace-balanced) except templates whose name is in `keep`."""
    out, i = [], 0
    while i < len(s):
        if s.startswith("{{", i):
            depth, j = 0, i
            while j < len(s) - 1:
                if s.startswith("{{", j):
                    depth += 1; j += 2; continue
                if s.startswith("}}", j):
                    depth -= 1; j += 2
                    if depth == 0:
                        break
                    continue
                j += 1
            block = s[i:j]
            name = block[2:].split("|", 1)[0].strip().lower()
            if name in keep:
                out.append(block)
            i = j
            continue
        out.append(s[i]); i += 1
    return "".join(out)


def split_top_level_on(s, sep):
    """Split on `sep` characters that are not inside {{ }} or [[ ]]."""
    parts, buf, depth_t, depth_l, i = [], [], 0, 0, 0
    while i < len(s):
        two = s[i:i + 2]
        if two == "{{": depth_t += 1; buf.append(two); i += 2; continue
        if two == "}}": depth_t -= 1; buf.append(two); i += 2; continue
        if two == "[[": depth_l += 1; buf.append(two); i += 2; continue
        if two == "]]": depth_l -= 1; buf.append(two); i += 2; continue
        c = s[i]
        if c == sep and depth_t == 0 and depth_l == 0:
            parts.append("".join(buf)); buf = []
        else:
            buf.append(c)
        i += 1
    parts.append("".join(buf))
    return parts


def page_name(name):
    """Normalise a link target the way MediaWiki does: trim, collapse spaces, capitalise first letter."""
    name = re.sub(r"\s+", " ", name.replace("_", " ")).strip()
    return name[:1].upper() + name[1:] if name else name


PLINK_TEMPLATES = {"plink", "plinkp", "plinkt", "plinki", "plinks"}
NAMED_PARAM_RE = re.compile(r"^\s*[a-z]+\s*=", re.IGNORECASE)
LINK_RE = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]*)?\]\]")


def balanced_end(s, i):
    """Index just past the {{...}} block starting at i (brace balanced)."""
    depth, j = 0, i
    while j < len(s) - 1:
        if s.startswith("{{", j):
            depth += 1; j += 2; continue
        if s.startswith("}}", j):
            depth -= 1; j += 2
            if depth == 0:
                return j
            continue
        j += 1
    return len(s)


def note_text(raw):
    """Footnote wikitext to plain text, plus the link targets it mentions."""
    items = [page_name(m.group(1).split("#", 1)[0]) for m in LINK_RE.finditer(raw)]
    text = re.sub(r"\[\[(?:[^\]|]+\|)?([^\]]+)\]\]", r"\1", raw)
    text = strip_templates(text, set())
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"'{2,3}", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text, [i for i in items if i]


class Footnotes:
    """Collects the {{efn}} notes of one equipment table: name -> {"text", "items"}."""

    def __init__(self):
        self.notes = {}
        self.counter = 0

    def add(self, block):
        parts = split_top_level(block[2:-2])[1:]
        named, positional = {}, []
        for part in parts:
            if NAMED_PARAM_RE.match(part):
                k, v = part.split("=", 1)
                named[k.strip().lower()] = v.strip()
            else:
                positional.append(part)
        name = named.get("name")
        if not name:
            self.counter += 1
            name = f"n{self.counter}"
        text, items = note_text(positional[0]) if positional else ("", [])
        entry = self.notes.setdefault(name, {"text": "", "items": []})
        if text and not entry["text"]:
            entry["text"], entry["items"] = text, items
        return name

    def resolved(self):
        return {name: note for name, note in self.notes.items() if note["text"]}


def parse_cell(raw, footnotes):
    """
    Turn one cell like
        "[[Slayer helmet (i)]] / {{plink|Black mask (i)}}{{efn|On task.|name=task}}"
    into ranked entries of (item, [note names]):
        [[("Slayer helmet (i)", []), ("Black mask (i)", ["task"])]]
    A ">" between alternatives means "better than" on the wiki, so it starts a new (lower) entry.
    We keep the *link target* (the item page name) because that is what matches in-game item names best.
    """
    s = re.sub(r"<ref[^>]*?/>", "", raw)
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.DOTALL)
    s = re.sub(r"<br\s*/?>", " ", s, flags=re.IGNORECASE)
    s = s.replace("&nbsp;", " ")

    entries = []
    for segment in split_top_level_on(s, ">"):
        items = []  # [name, [note names]]

        def take(name):
            if name.strip().startswith("#"):   # same-page anchor link, not an item
                return
            name = page_name(name)
            if name and name.lower() not in ("n/a", "none", "-", "—"):
                items.append([name, []])

        i = 0
        while i < len(segment):
            if segment.startswith("{{", i):
                j = balanced_end(segment, i)
                block = segment[i:j]
                tname = block[2:].split("|", 1)[0].strip().lower()
                if tname in PLINK_TEMPLATES:
                    positional = [p for p in split_top_level(block[2:-2])[1:] if "=" not in p]
                    if positional:
                        take(positional[0])
                elif tname == "efn":
                    note = footnotes.add(block)
                    if items:
                        items[-1][1].append(note)
                i = j
                continue
            if segment.startswith("[[", i):
                j = segment.find("]]", i)
                if j < 0:
                    break
                take(segment[i + 2:j].split("|", 1)[0])
                i = j + 2
                continue
            i += 1

        if not items:
            # plain text separated by "/" — only if nothing else matched
            plain = re.sub(r"<[^>]+>", "", strip_templates(segment, set()))
            for chunk in re.split(r"\s*/\s*|\s*,\s*", plain):
                chunk = chunk.strip(" '\"")
                if chunk:
                    take(chunk)

        seen, out = set(), []
        for name, notes in items:
            key = name.lower()
            if key not in seen:
                seen.add(key); out.append((name, notes))
        if out:
            entries.append(out)
    return entries


def clean_item_names(raw):
    """Item names only, without footnotes."""
    return [[name for name, _ in entry] for entry in parse_cell(raw, Footnotes())]


def parse_page(wikitext):
    """Return {style_name: {slot: [[alt1, alt2], [alt1], ...]}} for one page."""
    headings = [(m.start(), m.group(2).strip()) for m in HEADING_RE.finditer(wikitext)]
    styles = {}
    for start, end in find_template_blocks(wikitext):
        block = wikitext[start:end]
        params = parse_params(block)
        style = params.get("style", "").strip()
        if not style:
            # fall back to the nearest heading above the template
            above = [h for pos, h in headings if pos < start]
            style = above[-1] if above else "Unknown"
        style = re.sub(r"'''|''", "", style).strip()

        slots = {}
        notes = {}
        footnotes = Footnotes()
        for k, v in params.items():
            m = SLOT_PARAM_RE.match(k)
            if not m or m.group(1) not in SLOTS:
                continue
            slot, rank = m.group(1), int(m.group(2))
            for sub, entry in enumerate(parse_cell(v, footnotes)):
                slots.setdefault(slot, []).append(((rank, sub), [name for name, _ in entry]))
                for name, item_notes in entry:
                    if item_notes:
                        notes.setdefault(name, [])
                        notes[name] += [n for n in item_notes if n not in notes[name]]
        if not slots:
            continue
        ordered = {"slots": {slot: [alts for _, alts in sorted(ranked)] for slot, ranked in slots.items()}}
        if notes:
            ordered["notes"] = notes
            ordered["footnotes"] = footnotes.resolved()
        # if the same style appears twice (e.g. "Melee" and "Melee (budget)"), keep both
        key = style
        n = 2
        while key in styles:
            key = f"{style} #{n}"; n += 1
        styles[key] = ordered
    return styles


# ---------------------------------------------------------------- wiki API

def api_get(params):
    params = dict(params, format="json", formatversion="2")
    url = API_URL + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_wikitext(titles):
    """Fetch wikitext for up to 50 titles. Returns {resolved_title: wikitext}. Follows redirects."""
    data = api_get({
        "action": "query", "prop": "revisions", "rvprop": "content", "rvslots": "main",
        "redirects": "1", "titles": "|".join(titles),
    })
    out = {}
    for page in data.get("query", {}).get("pages", []):
        if page.get("missing"):
            continue
        revs = page.get("revisions") or []
        if not revs:
            continue
        out[page["title"]] = revs[0]["slots"]["main"]["content"]
    # map requested -> resolved (redirects / normalisation)
    alias = {}
    for r in data.get("query", {}).get("redirects", []) + data.get("query", {}).get("normalized", []):
        alias[r["from"]] = r["to"]
    resolved = {}
    for t in titles:
        real = alias.get(t, t)
        real = alias.get(real, real)
        if real in out:
            resolved[t] = (real, out[real])
    return resolved


def candidate_titles(task):
    if task in BOSS_TASKS:
        base = BOSS_TASKS[task]
        cands = [f"{base}/Strategies", base]
    else:
        singular = re.sub(r"s$", "", task)
        cands = [f"Slayer task/{task}", f"{task}/Strategies", f"{singular}/Strategies",
                 f"Slayer task/{task}s", f"Slayer task/{singular}", task, singular]
    cands = ALT_PAGES.get(task, []) + cands
    # Wiki titles are case-sensitive after the first letter ("Lava dragon", not "Lava Dragon").
    lowered = [c[:1] + c[1:].lower() for c in cands]
    out = []
    for c in cands + lowered:
        if c not in out:
            out.append(c)
    return out


def chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def collect_item_names(result, generic, aliases):
    names = set(EXTRA_ITEMS)
    def add_task(task):
        for style in task["styles"].values():
            for ranked in style["slots"].values():
                for entry in ranked:
                    names.update(entry)
    for task in result.values():
        add_task(task)
        for variant in task.get("variants", {}).values():
            add_task(variant)
    if generic:
        add_task(generic)
    for targets in aliases.values():
        names.update(targets)
    return sorted(n for n in names if "#" not in n)


def fetch_item_ids(names, verbose):
    """Item page name -> item id, read from the wiki's item infobox (first id of multi-version pages)."""
    ids = {}
    for batch in chunks(names, 50):
        for name, (real, text) in fetch_wikitext(batch).items():
            m = INFOBOX_ID_RE.search(text)
            if m:
                ids[name] = int(m.group(1))
            elif verbose:
                print(f"[noid] {name} ({real})")
        time.sleep(0.5)
    return ids


def load_aliases(out_path):
    path = os.path.join(os.path.dirname(os.path.abspath(out_path)), "aliases.json")
    try:
        with open(path, encoding="utf-8") as f:
            return {k: v for k, v in json.load(f).items() if not k.startswith("_")}
    except (OSError, ValueError):
        return {}


def run_extract(out_path, verbose):
    all_tasks = TASKS + list(BOSS_TASKS.keys())
    wanted = {task: candidate_titles(task) for task in all_tasks}
    # Try candidates in rounds: first choice for every task, then fallbacks for the misses.
    result, misses = {}, {}
    for round_idx in range(max(len(v) for v in wanted.values())):
        todo = [(task, cands[round_idx]) for task, cands in wanted.items()
                if task not in result and round_idx < len(cands)]
        if not todo:
            break
        for batch in chunks(todo, 50):
            titles = [t for _, t in batch]
            fetched = fetch_wikitext(titles)
            for task, title in batch:
                if title in fetched:
                    real, text = fetched[title]
                    styles = parse_page(text)
                    if styles:
                        result[task] = {"page": real, "styles": styles}
                        if verbose:
                            print(f"[ok]   {task:32s} <- {real} ({', '.join(styles)})")
                        continue
                misses[task] = misses.get(task, []) + [title]
            time.sleep(0.5)  # be polite
    missing = [t for t in all_tasks if t not in result]

    # variant monsters with their own strategy page
    variant_pages = sorted({f"{v}/Strategies" for vs in VARIANTS.values() for v in vs})
    fetched = {}
    for batch in chunks(variant_pages, 50):
        fetched.update(fetch_wikitext(batch))
        time.sleep(0.5)
    for task, variants in VARIANTS.items():
        for variant in variants:
            page = f"{variant}/Strategies"
            if page not in fetched:
                print(f"[warn] variant page not found: {page}", file=sys.stderr)
                continue
            real, text = fetched[page]
            styles = parse_page(text)
            if not styles:
                print(f"[warn] no equipment table on {real}", file=sys.stderr)
                continue
            entry = result.setdefault(task, {"page": None, "styles": {}})
            entry.setdefault("variants", {})[variant] = {"page": real, "styles": styles}
            if verbose:
                print(f"[var]  {task:32s} <- {variant} ({', '.join(styles)})")

    # generic slayer gear
    generic = None
    got = fetch_wikitext([GENERIC_PAGE])
    if GENERIC_PAGE in got:
        real, text = got[GENERIC_PAGE]
        styles = {name: slots for name, slots in parse_page(text).items()
                  if "#" not in name and "wilderness" not in name.lower()}
        if styles:
            generic = {"page": real, "styles": styles}
            if verbose:
                print(f"[gen]  generic <- {real} ({', '.join(styles)})")

    # item ids for icons
    names = collect_item_names(result, generic, load_aliases(out_path))
    items = fetch_item_ids(names, verbose)
    if verbose:
        print(f"[ids]  {len(items)} of {len(names)} item names resolved to an id")

    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "source": "Old School RuneScape Wiki (oldschool.runescape.wiki), CC BY-NC-SA 3.0",
        "items": items,
        "generic": generic,
        "tasks": result,
        "missing": missing,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, separators=(",", ":"), ensure_ascii=False)  # compact: the file is bundled in the jar
    n_variants = sum(len(t.get("variants", {})) for t in result.values())
    print(f"\nWrote {out_path}: {len(result) - len([t for t in result.values() if not t['styles']])} tasks with their own gear table, "
          f"{len(missing)} without, {n_variants} variant tables, generic: {'yes' if generic else 'no'}, {len(items)} item ids.")
    if missing:
        print("No equipment table found for: " + ", ".join(missing))


# ---------------------------------------------------------------- self test

SAMPLE = """
== Strategy ==
Some intro text.

=== Melee ===
{{Recommended equipment
|style = Melee
|head1 = {{plink|Slayer helmet (i)}}<ref>On task.</ref>
|head2 = [[Helm of neitiznot]] / [[Serpentine helm]]
|neck1 = [[Amulet of torture]]
|neck2 = [[Amulet of fury]]
|cape1 = [[Infernal cape]]
|cape2 = [[Fire cape]]
|body1 = [[Torva platebody]]
|body2 = [[Bandos chestplate]]
|legs1 = [[Bandos tassets]]
|weapon1 = [[Scythe of vitur]]
|weapon2 = [[Abyssal whip]]
|shield1 = [[Avernic defender]]
|shield2 = [[Dragon defender]]
|hands1 = [[Ferocious gloves]]
|feet1 = [[Primordial boots]]
|ring1 = [[Berserker ring (i)]]
|special1 = {{plink|Dragon claws}}
}}

=== Ranged ===
{{Recommended equipment
|head1 = [[Slayer helmet (i)]]
|body1 = [[Masori body (f)]]
|weapon1 = [[Toxic blowpipe]]
|ammo1 = [[Rada's blessing 4]]
|ammo2 = [[God blessing|Peaceful blessing]] / [[Rada's blessing 3|Rada's blessing 3/2]]
|cape1 = {{plink|God capes#Imbuing|txt=Imbued god cape|pic=Imbued Saradomin cape}}
|cape2 = {{plink|pic=Ardougne cloak 1|Ardougne cloak}}
|legs1 = {{plink|Masori chaps (f)}} {{efn|If these do not help you can wear [[Proselyte]] when praying [[piety]].}}
|weapon2 = {{plink|Dragon sword}} > <br/>{{plink|dual macuahuitl}}{{efn|name=x|see [[blood moon armour]]}} /<br/> [[Abyssal bludgeon]]
|hidecol = special
|feet1 = [[#Phase 1 equipment|see above]] / {{plink|Pegasian boots}}
|hands1 = {{plink|Zaryte vambraces}} / {{plink|Void knight gloves}}{{efn|name=void}}
|shield1 = {{plink|Twisted buckler}}{{efn|Only if bringing a crossbow.|name=xbow}}
|ammo3 = {{plink|Dragon arrow}}{{efn|If bringing a [[twisted bow]].|name=darrows}}
|head3 = {{plink|Void ranger helm}}{{efn|With full [[Void_Knight_equipment#Set_effects|Elite Void]]. Performs better off-task than Masori.|name=void}}
|body2 = {{plink|Masori body}} / {{plink|Elite void top}}{{efn|name=void}}
}}
"""


def selftest():
    tables = parse_page(SAMPLE)
    assert set(tables) == {"Melee", "Ranged"}, tables.keys()
    styles = {name: table["slots"] for name, table in tables.items()}
    melee = styles["Melee"]
    assert melee["head"] == [["Slayer helmet (i)"], ["Helm of neitiznot", "Serpentine helm"]], melee["head"]
    assert melee["weapon"][1] == ["Abyssal whip"]
    assert melee["special"] == [["Dragon claws"]]
    ranged = styles["Ranged"]
    assert ranged["ammo"][1] == ["God blessing", "Rada's blessing 3"], ranged["ammo"]
    assert ranged["cape"] == [["God capes#Imbuing"], ["Ardougne cloak"]], ranged["cape"]
    assert ranged["legs"] == [["Masori chaps (f)"]], ranged["legs"]
    assert ranged["weapon"] == [["Toxic blowpipe"], ["Dragon sword"], ["Dual macuahuitl", "Abyssal bludgeon"]], ranged["weapon"]
    assert "hidecol" not in ranged, ranged.keys()
    assert ranged["feet"] == [["Pegasian boots"]], ranged["feet"]
    assert candidate_titles("Lava Dragons")[-1] == "Lava dragon" and "Slayer task/Kurasks" in candidate_titles("Kurask")
    notes = tables["Ranged"]["notes"]
    assert notes["Void ranger helm"] == ["void"] and notes["Elite void top"] == ["void"], notes
    assert notes["Twisted buckler"] == ["xbow"] and notes["Dragon arrow"] == ["darrows"], notes
    fn = tables["Ranged"]["footnotes"]
    assert fn["void"]["text"] == "With full Elite Void. Performs better off-task than Masori.", fn["void"]
    assert fn["void"]["items"] == ["Void Knight equipment"], fn["void"]
    assert fn["darrows"]["items"] == ["Twisted bow"], fn["darrows"]
    assert fn["xbow"]["text"] == "Only if bringing a crossbow." and fn["xbow"]["items"] == []
    assert "notes" not in tables["Melee"] or "Slayer helmet (i)" in tables["Melee"]["notes"]
    print(json.dumps(styles, indent=1))
    print("\nselftest OK")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="gear.json")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    if args.selftest:
        selftest()
        sys.exit(0)
    if "CHANGE_ME" in USER_AGENT:
        print("Set USER_AGENT (the wiki requires contact info) before running.", file=sys.stderr)
        sys.exit(1)
    run_extract(args.out, args.verbose)
