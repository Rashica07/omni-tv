#!/usr/bin/env python3
"""Builds the annotated reference boards from docs/design/references/ and docs/v3 (private analysis only).
Run: python3 -I docs/design/make_boards.py"""
from PIL import Image, ImageDraw, ImageFont
R = "docs/design/references/"; OUT = "docs/design/boards/"
F = ImageFont.truetype("assets/Geist.ttf", 22); FB = ImageFont.truetype("assets/Geist.ttf", 30); FS = ImageFont.truetype("assets/Geist.ttf", 18)
try: FB.set_variation_by_axes([700]); F.set_variation_by_axes([500]); FS.set_variation_by_axes([400])
except Exception: pass
W, H = 640, 360

def load(p, crop=None):
    im = Image.open(p).convert("RGB")
    if crop: im = im.crop(crop)
    return im.resize((W, H), Image.LANCZOS)

def wrap(d, text, font, maxw):
    out, line = [], ""
    for w in text.split():
        t = (line + " " + w).strip()
        if d.textlength(t, font=font) <= maxw: line = t
        else: out.append(line); line = w
    if line: out.append(line)
    return out

def board(title, cells, name, note_h):
    pad = 20; cols = len(cells)
    img = Image.new("RGB", (cols * (W + pad) + pad, 60 + H + note_h + pad), (10, 10, 10)); d = ImageDraw.Draw(img)
    d.text((pad, 16), title, font=FB, fill=(255, 255, 255))
    for i, (label, im, notes) in enumerate(cells):
        x = pad + i * (W + pad); y = 60
        img.paste(im, (x, y)); d.rectangle([x - 1, y - 1, x + W, y + H], outline=(70, 70, 70))
        d.text((x, y + H + 8), label, font=F, fill=(255, 255, 255)); yy = y + H + 38
        for n in notes:
            for ln in wrap(d, "• " + n, FS, W): d.text((x, yy), ln, font=FS, fill=(170, 170, 170)); yy += 22
    img.save(OUT + name); print(name, img.size)

board("Board 1 - Home at initial focus", [
 ("Apple tvOS 26 (static, official)", load(R+"apple-tvos/tvos26-hero.jpg", (312, 82, 1648, 834)), ["Art IS the environment; title as a logo, no text block", "One low translucent app strip; a second row only hinted", "Almost no chrome: clock/profile pill top-right", "Tabs are absent at rest"]),
 ("Titan OS (static, review photo)", load(R+"titan-os/techradar-home.jpg", (370, 30, 1700, 960)), ["Top tabs with underline on the selected one", "Promo hero left, favourites row, service rows", "Promo/ad hero = the pattern OMNI rejects", "Dense below the fold"]),
 ("Google TV stock (OBSERVED on this TV)", load(R+"google-tv/stock-tvlauncher-home-initial.png"), ["Left-aligned top tabs, underline on Home", "Focused app tile scales up, label appears below", "Hero is a content promo, rows below are promo-driven", "Near-black #0D0D0D fading to pure black"]),
 ("OMNI v3 (current, OBSERVED)", load("docs/v3/02_home_second.png"), ["Maroon environment + tinted spines: not monochrome", "Gold headings, crimson ring: off-brief accents", "Accordion spines are readable but crowd the stage", "Blurry low-res Netflix wordmark in the wide tile"]),
], "board-1-home.png", 170)

board("Board 2 - Focus and selection", [
 ("tvOS: shelf focus (static)", load(R+"apple-tvos/tvos26-appletv-app-posters.jpg", (40, 300, 1320, 810)), ["Focused tile lifted and larger; neighbours unchanged", "Thin light edge, soft shadow; no coloured ring", "Heading gray and small; page bg measured #16171C"]),
 ("tvOS: Control Center (static)", load(R+"apple-tvos/tvos26-control-center.jpg", (1380, 40, 1920, 590)), ["The highlighted header control is a solid WHITE circle with a dark glyph", "The other controls (Power Off, Wi-Fi...) are translucent gray", "Whether this is focus or selection is not shown by a still"]),
 ("Google TV: chip focus (OBSERVED)", load(R+"google-tv/stock-tvlauncher-home-down1.png", (40, 120, 1000, 500)), ["Focused chip = solid white pill, dark text", "Unfocused chips dark gray", "Rows above dim when not active"]),
 ("Titan OS: tile focus (static)", load(R+"titan-os/techradar-recommendations.jpg", (330, 320, 1100, 560)), ["Thin blue ring, outside the tile, title beneath", "Ring colour is the only accent"]),
], "board-2-focus.png", 150)

board("Board 3 - Navigation and structure", [
 ("Titan OS top nav", load(R+"titan-os/techradar-home.jpg", (370, 40, 1250, 300)), ["Search, Home, Channels, Apps; underline marks location", "System icons + clock right"]),
 ("Google TV top nav (OBSERVED)", load(R+"google-tv/stock-tvlauncher-home-initial.png", (0, 20, 900, 110)), ["Voice search, Home, Acquista, Scopri, App", "Underline marks location; settings/account/clock right"]),
 ("OMNI v3 header (OBSERVED)", load("docs/v3/02_home_second.png", (0, 20, 1280, 110)), ["Same model: wordmark, tabs with underline, clock right", "Keep: it matches the two platforms that share this model"]),
 ("Apple tvOS: dock (static)", load(R+"apple-tvos/tvos26-hero.jpg", (330, 600, 1640, 840)), ["tvOS puts apps in a low strip over art", "Lesson: art first, navigation second"]),
], "board-3-navigation.png", 130)
