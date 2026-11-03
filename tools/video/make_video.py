#!/usr/bin/env python3
"""
Builds the TV Sahayak portfolio video from real app renders.

1. Render the app screens:  ./gradlew :app:testDebugUnitTest --tests '*VideoFramesTest*'
2. Build the video:         python3 tools/video/make_video.py
Output: tv-sahayak-demo.mp4 (1920x1080, 30 fps, with a generated tanpura drone).
"""
import math
import multiprocessing
import os
import subprocess
import sys
import wave
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
FRAMES = os.path.join(ROOT, "app", "build", "video_frames")
OUT = os.path.join(ROOT, "tv-sahayak-demo.mp4")
W, H, FPS = 1920, 1080, 30

GOLD = (255, 210, 122)
SAFFRON = (255, 153, 51)
WHITE = (255, 255, 255)
MUTED = (225, 215, 235)

FONT_DIR = "/usr/share/fonts/truetype/noto/"


@lru_cache(None)
def font(size, bold=True, kind="latin"):
    if kind == "symbol":  # arrows, ticks and similar glyphs that Noto Sans lacks
        return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans" + ("-Bold" if bold else "") + ".ttf", size)
    name = ("NotoSansDevanagari-" if kind == "deva" else "NotoSans-") + ("Bold" if bold else "Regular") + ".ttf"
    return ImageFont.truetype(FONT_DIR + name, size, layout_engine=ImageFont.Layout.RAQM)


def runs(s):
    """Splits mixed text ("Anupamaa खोल दिया") into same-script runs, each drawn with its own font."""
    out, cur, buf = [], None, ""
    for c in s:
        o = ord(c)
        k = "deva" if 0x0900 <= o <= 0x097F else "symbol" if 0x2190 <= o <= 0x27BF else None if (c.isspace() or c in ".,“”\"'·—-") else "latin"
        k = k or cur or "latin"
        if k != cur and buf:
            out.append((cur, buf)); buf = ""
        cur = k; buf += c
    if buf:
        out.append((cur, buf))
    return out


def kind_of(s):
    if any(0x0900 <= ord(c) <= 0x097F for c in s):
        return "deva"
    if any(0x2190 <= ord(c) <= 0x27BF for c in s):
        return "symbol"
    return "latin"


# ---------------------------------------------------------------- easing / helpers
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def ease(x):  # easeOutCubic
    x = clamp(x)
    return 1 - (1 - x) ** 3


def ease_io(x):
    x = clamp(x)
    return 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2


def prog(t, start, dur):
    return clamp((t - start) / dur)


def lerp(a, b, x):
    return a + (b - a) * x


# ---------------------------------------------------------------- background
def make_background():
    y = np.linspace(0, 1, H)[:, None]
    x = np.linspace(0, 1, W)[None, :]
    top, mid, bot = np.array([20, 8, 46]), np.array([59, 13, 74]), np.array([110, 23, 57])
    t = (y * 0.85 + x * 0.15)
    col = np.where(t[..., None] < 0.5, top + (mid - top) * (t[..., None] / 0.5), mid + (bot - mid) * ((t[..., None] - 0.5) / 0.5))
    # soft saffron and gold glows
    for cx, cy, r, c, a in [(0.12, 0.2, 0.55, SAFFRON, 0.28), (0.9, 0.85, 0.6, GOLD, 0.16)]:
        d = np.sqrt(((x - cx) * W / H) ** 2 + (y - cy) ** 2) / r
        g = np.clip(1 - d, 0, 1) ** 2 * a
        col = col * (1 - g[..., None]) + np.array(c) * g[..., None]
    return Image.fromarray(col.astype(np.uint8), "RGB")


def make_rangoli(size, color=GOLD, alpha=60):
    s = size
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = s / 2
    col = color + (alpha,)
    for f in (0.25, 0.5, 0.78, 1.0):
        r = c * f * 0.98
        d.ellipse([c - r, c - r, c + r, c + r], outline=col, width=3)
    petal = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    pd = ImageDraw.Draw(petal)
    pw, ph = s * 0.07, s * 0.21
    pd.ellipse([c - pw / 2, c - s * 0.37 - ph / 2, c + pw / 2, c - s * 0.37 + ph / 2], outline=col, width=3)
    for i in range(16):
        im.alpha_composite(petal.rotate(360 / 16 * i, resample=Image.BICUBIC, center=(c, c)))
    inner = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    idr = ImageDraw.Draw(inner)
    iw, ih = s * 0.05, s * 0.11
    idr.ellipse([c - iw / 2, c - s * 0.12 - ih / 2, c + iw / 2, c - s * 0.12 + ih / 2], fill=col)
    for i in range(8):
        im.alpha_composite(inner.rotate(45 * i, resample=Image.BICUBIC, center=(c, c)))
    for i in range(32):
        a = 2 * math.pi * i / 32
        x, y = c + c * 0.9 * math.cos(a), c + c * 0.9 * math.sin(a)
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=col)
    return im


BG = None
RANGOLI = None
SPARKS = np.random.default_rng(7).random((40, 3))


def background(t):
    img = BG.copy().convert("RGBA")
    r1 = RANGOLI.rotate(t * 6, resample=Image.BICUBIC)
    img.alpha_composite(r1, (W - 640, -380))
    r2 = RANGOLI.resize((800, 800)).rotate(-t * 5, resample=Image.BICUBIC)
    img.alpha_composite(r2, (-330, H - 420))
    d = ImageDraw.Draw(img)
    for sx, sy, ph in SPARKS:  # twinkling, slowly rising sparkles
        y = (sy * H - t * 12 * (0.5 + ph)) % H
        a = int(90 + 90 * math.sin(t * 2 + ph * 6))
        rr = 2 + ph * 2
        d.ellipse([sx * W - rr, y - rr, sx * W + rr, y + rr], fill=GOLD + (a,))
    return img


# ---------------------------------------------------------------- drawing primitives
def text(img, xy, s, size, color=WHITE, anchor="la", alpha=1.0, bold=True, shadow=True):
    if alpha <= 0 or not s:
        return
    parts = [(font(size, bold, k), t) for k, t in runs(s)]
    total = sum(f.getlength(t) for f, t in parts)
    x = xy[0] - {"l": 0, "m": total / 2, "r": total}[anchor[0]]
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for f, t in parts:
        if shadow:
            d.text((x + 2, xy[1] + 3), t, font=f, fill=(0, 0, 0, int(110 * alpha)), anchor="l" + anchor[1])
        d.text((x, xy[1]), t, font=f, fill=color[:3] + (int(255 * alpha),), anchor="l" + anchor[1])
        x += f.getlength(t)
    img.alpha_composite(layer)


def text_width(s, size, bold=True):
    return sum(font(size, bold, k).getlength(t) for k, t in runs(s))


def glass(img, box, radius=28, alpha=1.0, fill=(255, 255, 255, 30), outline=(255, 255, 255, 60)):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    f = fill[:3] + (int(fill[3] * alpha),)
    o = outline[:3] + (int(outline[3] * alpha),)
    d.rounded_rectangle(box, radius, fill=f, outline=o, width=2)
    img.alpha_composite(layer)


def shadow_under(img, box, radius, blur=30, opacity=120, offset=(0, 18)):
    x0, y0, x1, y1 = box
    pad = blur * 3
    sh = Image.new("RGBA", (int(x1 - x0) + pad * 2, int(y1 - y0) + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle([pad, pad, pad + x1 - x0, pad + y1 - y0], radius, fill=(0, 0, 0, opacity))
    sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.alpha_composite(sh, (int(x0 - pad + offset[0]), int(y0 - pad + offset[1])))


@lru_cache(None)
def screen(name, height):
    im = Image.open(os.path.join(FRAMES, name + ".png")).convert("RGBA")
    w = round(im.width * height / im.height)
    im = im.resize((w, height), Image.LANCZOS)
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, height - 1], int(height * 0.045), fill=255)
    im.putalpha(mask)
    return im


@lru_cache(None)
def phone_shell(sw, sh):
    bz = max(10, sw // 28)
    w, h = sw + bz * 2, sh + bz * 2
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, w - 1, h - 1], int(h * 0.055), fill=(18, 18, 22, 255), outline=(90, 80, 110, 255), width=3)
    return im, bz


def phone(img, cx, cy, height, frames, mix=0.0, alpha=1.0, rot=0.0, scale=1.0):
    """Draws a phone centred at (cx, cy) showing frames[0], cross-faded into frames[1] by `mix`."""
    height = int(height * scale)
    a = screen(frames[0], height)
    scr = a.copy()
    if len(frames) > 1 and mix > 0:
        b = screen(frames[1], height)
        scr = Image.blend(a, b, mix)
        scr.putalpha(a.getchannel("A"))
    shell, bz = phone_shell(scr.width, scr.height)
    ph = shell.copy()
    ph.alpha_composite(scr, (bz, bz))
    # camera dot
    d = ImageDraw.Draw(ph)
    d.ellipse([ph.width / 2 - 7, bz + 10, ph.width / 2 + 7, bz + 24], fill=(10, 10, 12, 255))
    if rot:
        ph = ph.rotate(rot, resample=Image.BICUBIC, expand=True)
    if alpha < 1:
        ph.putalpha(ph.getchannel("A").point(lambda v: int(v * alpha)))
    x0, y0 = int(cx - ph.width / 2), int(cy - ph.height / 2)
    shadow_under(img, (x0 + 20, y0 + 30, x0 + ph.width - 20, y0 + ph.height - 10), 60, opacity=int(140 * alpha))
    img.alpha_composite(ph, (x0, y0))
    return (x0, y0, x0 + ph.width, y0 + ph.height)


def bubble(img, x, y, s, size=44, alpha=1.0, typed=1.0, tail="right"):
    """Speech bubble with typing effect; (x, y) is the bubble's left/top corner."""
    shown = s[: max(0, int(round(len(s) * typed)))]
    tw = text_width(s, size)
    bw, bh = tw + 80, size + 60
    glass(img, (x, y, x + bw, y + bh), radius=bh // 2, alpha=alpha, fill=(255, 255, 255, 235), outline=(255, 210, 122, 255))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if tail == "right":
        d.polygon([(x + bw - 70, y + bh - 4), (x + bw - 20, y + bh + 30), (x + bw - 110, y + bh - 4)], fill=(255, 255, 255, int(235 * alpha)))
    img.alpha_composite(layer)
    text(img, (x + 40, y + bh / 2), shown, size, (60, 20, 70), anchor="lm", alpha=alpha, shadow=False)
    if typed < 1 and int(typed * 30) % 2 == 0:  # caret
        cx = x + 40 + text_width(shown, size) + 4
        ImageDraw.Draw(img).rectangle([cx, y + bh / 2 - size / 2, cx + 4, y + bh / 2 + size / 2], fill=(60, 20, 70, int(255 * alpha)))
    return bw, bh


def mic_badge(img, cx, cy, t, alpha=1.0):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for i in range(3):  # sound waves
        ph = (t * 1.2 + i / 3) % 1
        r = 40 + ph * 70
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=SAFFRON + (int(200 * (1 - ph) * alpha),), width=4)
    d.ellipse([cx - 40, cy - 40, cx + 40, cy + 40], fill=SAFFRON + (int(255 * alpha),))
    d.rounded_rectangle([cx - 11, cy - 24, cx + 11, cy + 8], 11, fill=(255, 255, 255, int(255 * alpha)))
    d.arc([cx - 20, cy - 12, cx + 20, cy + 20], 0, 180, fill=(255, 255, 255, int(255 * alpha)), width=4)
    d.line([cx, cy + 20, cx, cy + 28], fill=(255, 255, 255, int(255 * alpha)), width=4)
    img.alpha_composite(layer)


def heading(img, t, start, title, sub=None, x=140, y=180, size=72):
    a = ease(prog(t, start, 0.6))
    dy = (1 - a) * 40
    text(img, (x, y + dy), title, size, GOLD, alpha=a)
    if sub:
        b = ease(prog(t, start + 0.25, 0.6))
        text(img, (x, y + size + 30 + (1 - b) * 30), sub, 36, MUTED, alpha=b, bold=False)


def tv(img, cx, cy, w, t, on, alpha=1.0):
    """A wall TV showing a generic show page (no third-party branding)."""
    h = int(w * 9 / 16)
    x0, y0 = int(cx - w / 2), int(cy - h / 2)
    shadow_under(img, (x0, y0, x0 + w, y0 + h), 20, opacity=int(150 * alpha))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    A = lambda c, a=255: c + (int(a * alpha),)
    d.rounded_rectangle([x0 - 14, y0 - 14, x0 + w + 14, y0 + h + 14], 18, fill=A((15, 15, 18)))
    d.polygon([(cx - 60, y0 + h + 14), (cx + 60, y0 + h + 14), (cx + 110, y0 + h + 70), (cx - 110, y0 + h + 70)], fill=A((30, 30, 36)))
    if on <= 0:
        d.rectangle([x0, y0, x0 + w, y0 + h], fill=A((8, 8, 10)))
        img.alpha_composite(layer)
        return
    # show page: hero gradient, title, button, episode row
    grad = Image.new("RGBA", (w, h))
    gd = ImageDraw.Draw(grad)
    for i in range(h):
        k = i / h
        gd.line([(0, i), (w, i)], fill=(int(lerp(70, 15, k)), int(lerp(20, 10, k)), int(lerp(60, 30, k)), 255))
    for i in range(w // 2, w):  # warm light on the right, like a poster
        k = (i - w / 2) / (w / 2)
        gd.line([(i, 0), (i, h * 0.7)], fill=(int(lerp(70, 200, k * 0.6)), int(lerp(20, 90, k * 0.5)), 60, int(255 * 0.5 * k)))
    grad.putalpha(int(255 * alpha * ease(on)))
    layer.alpha_composite(grad, (x0, y0))
    img.alpha_composite(layer)
    k = ease(on)
    text(img, (x0 + w * 0.06, y0 + h * 0.18), "अनुपमा", int(h * 0.12), WHITE, alpha=alpha * k)
    text(img, (x0 + w * 0.06, y0 + h * 0.36), "Anupamaa  ·  Hindi  ·  Drama", int(h * 0.045), MUTED, alpha=alpha * k, bold=False)
    bx, by = x0 + w * 0.06, y0 + h * 0.48
    pulse = 1 + 0.04 * math.sin(t * 6)
    bw, bh = w * 0.3 * pulse, h * 0.1 * pulse
    glass(img, (bx, by, bx + bw, by + bh), radius=int(bh / 2), alpha=alpha * k, fill=SAFFRON + (255,), outline=GOLD + (255,))
    text(img, (bx + bw / 2, by + bh / 2), "▶  Latest episode", int(h * 0.045), WHITE, anchor="mm", alpha=alpha * k)
    for i in range(4):
        ex = x0 + w * 0.06 + i * w * 0.225
        glass(img, (ex, y0 + h * 0.68, ex + w * 0.2, y0 + h * 0.92), radius=10, alpha=alpha * k * 0.9,
              fill=(255, 255, 255, 40 if i else 90), outline=(255, 210, 122, 120 if i == 0 else 40))
        text(img, (ex + 12, y0 + h * 0.88), f"Ep {2186 - i}", int(h * 0.035), WHITE, anchor="ls", alpha=alpha * k)


def wifi(img, x0, y0, x1, y1, t, alpha=1.0):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    n = 14
    for i in range(n):  # dotted arc with a travelling highlight
        k = i / (n - 1)
        x = lerp(x0, x1, k)
        y = lerp(y0, y1, k) - math.sin(k * math.pi) * 90
        glow = max(0, 1 - abs(((t * 1.4) % 1.4) - k) * 5)
        r = 5 + glow * 7
        d.ellipse([x - r, y - r, x + r, y + r], fill=GOLD + (int((90 + 165 * glow) * alpha),))
    img.alpha_composite(layer)


def feature_card(img, x, y, w, h, icon, title, sub, a):
    if a <= 0:
        return
    dy = (1 - ease(a)) * 50
    glass(img, (x, y + dy, x + w, y + h + dy), radius=30, alpha=ease(a), fill=(255, 255, 255, 34), outline=(255, 210, 122, 110))
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse([x + 30, y + 34 + dy, x + 110, y + 114 + dy], fill=SAFFRON + (int(255 * ease(a)),))
    img.alpha_composite(layer)
    text(img, (x + 70, y + 74 + dy), icon, 40, WHITE, anchor="mm", alpha=ease(a), shadow=False)
    text(img, (x + 135, y + 42 + dy), title, 36, WHITE, alpha=ease(a))
    text(img, (x + 135, y + 92 + dy), sub, 26, MUTED, alpha=ease(a), bold=False)


# ---------------------------------------------------------------- scenes
SCROLL = [f"10_scroll_{i:02d}" for i in range(91)]
LISTEN = [f"02_listen_{i}" for i in range(8)]

SCENES = []  # (start, end, fn)


def scene(start, end):
    def deco(fn):
        SCENES.append((start, end, fn))
        return fn
    return deco


@scene(0, 5.5)
def intro(img, t):
    a = ease(prog(t, 0.3, 0.9))
    s = 1 + (1 - a) * 0.15
    # glowing title
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.text((W / 2, 420), "TV Sahayak", font=font(int(150 * s)), fill=SAFFRON + (int(160 * a),), anchor="mm")
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(18)))
    text(img, (W / 2, 420), "TV Sahayak", int(150 * s), GOLD, anchor="mm", alpha=a)
    b = ease(prog(t, 0.9, 0.8))
    text(img, (W / 2, 540 + (1 - b) * 20), "टीवी सहायक", 64, WHITE, anchor="mm", alpha=b)
    c = ease(prog(t, 1.6, 0.8))
    text(img, (W / 2, 650 + (1 - c) * 20), "A Hindi voice remote, built for my grandmother", 40, MUTED, anchor="mm", alpha=c, bold=False)
    d = ease(prog(t, 2.4, 0.8))
    lw = 420 * d
    ImageDraw.Draw(img).line([W / 2 - lw / 2, 720, W / 2 + lw / 2, 720], fill=GOLD + (200,), width=3)
    mic_badge(img, W / 2, 820, t, alpha=ease(prog(t, 2.8, 0.6)))


@scene(5.5, 16)
def voice_demo(img, t):
    # phone slides in from the right
    p = ease_io(prog(t, 0, 1.0))
    cx = lerp(W + 400, 1380, p)
    if t < 1.6:
        frames, mix = ["01_idle"], 0
    elif t < 4.6:
        i = int((t - 1.6) * 5) % len(LISTEN)
        frames, mix = [LISTEN[i]], 0
        if t > 3.6:
            frames, mix = [LISTEN[i], "03_partial"], prog(t, 3.6, 0.4)
    elif t < 5.4:
        frames, mix = ["03_partial", "04_working"], prog(t, 4.6, 0.3)
    else:
        frames, mix = ["04_working", "05_done_anupama"], prog(t, 5.4, 0.4)
    phone(img, cx, 560, 900, frames, mix)

    heading(img, t, 0.4, "Just say it.", "Hindi, English or Hinglish")
    if t > 1.4:
        mic_badge(img, 210, 520, t, alpha=ease(prog(t, 1.4, 0.4)) * (1 - prog(t, 5.0, 0.5)))
        bubble(img, 290, 470, "Anupama ka latest episode chalao", 42, alpha=ease(prog(t, 1.5, 0.3)), typed=prog(t, 1.7, 2.2))
    if t > 5.6:
        a = ease(prog(t, 5.6, 0.6))
        text(img, (140, 700 + (1 - a) * 20), "It understands — and replies in Hindi.", 40, WHITE, alpha=a)
        text(img, (140, 760 + (1 - a) * 20), "“Anupamaa खोल दिया। चलाने के लिए OK दबाइए।”", 34, GOLD, alpha=a, bold=False)


@scene(16, 23.5)
def to_tv(img, t):
    p = ease_io(prog(t, 0, 1.0))
    pcx = lerp(1380, 330, p)
    phone(img, pcx, 560, lerp(900, 760, p), ["05_done_anupama"])
    a = ease(prog(t, 0.6, 0.8))
    tv(img, 1180, 470, int(lerp(900, 1050, a)), t, on=prog(t, 2.2, 0.8), alpha=a)
    if 1.2 < t:
        wifi(img, pcx + 230, 380, 680, 380, t, alpha=ease(prog(t, 1.2, 0.5)))
    b = ease(prog(t, 2.6, 0.6))
    text(img, (1180, 900 + (1 - b) * 20), "Opens the exact show on the TV — one OK to play", 40, WHITE, anchor="mm", alpha=b)
    c = ease(prog(t, 3.2, 0.6))
    text(img, (1180, 960 + (1 - c) * 20), "Android TV Remote protocol · no searching, no typing", 30, MUTED, anchor="mm", alpha=c, bold=False)


@scene(23.5, 32.5)
def quick_fire(img, t):
    cmds = [("Star Plus lagao", "Live channels by name", "06_done_starplus"),
            ("Netflix par Stranger Things chalao", "Any app, any show", "07_done_netflix"),
            ("Volume badhao", "Volume, power, play / pause", "08_done_volume")]
    seg = 3.0
    i = min(2, int(t // seg))
    lt = t - i * seg
    prev = cmds[i - 1][2] if i else "05_done_anupama"
    p = ease_io(prog(t, 0, 0.8))
    phone(img, lerp(330, 1400, p), 560, lerp(760, 900, p), [prev, cmds[i][2]], prog(lt, 1.4, 0.4))
    heading(img, t, 0.3, "Everyday commands", "Spoken the way the family talks", y=170)
    for j, (cmd, sub, _) in enumerate(cmds[: i + 1]):
        y = 430 + j * 170
        a = ease(prog(t, j * seg + 0.2, 0.5))
        bubble(img, 140, y, cmd, 38, alpha=a, typed=prog(t, j * seg + 0.3, 0.9))
        text(img, (170, y + 118), "✓  " + sub, 30, GOLD, alpha=ease(prog(t, j * seg + 1.5, 0.4)), bold=False)


@scene(32.5, 41)
def scroll_tour(img, t):
    p = ease_io(prog(t, 0, 0.8))
    # real scroll positions from the app, eased like a finger scroll (no blending, which would double the image)
    k = ease_io(clamp((t - 1.0) / 6.2)) * (len(SCROLL) - 1)
    phone(img, lerp(1400, 1360, p), 560, 940, [SCROLL[int(round(k))]])
    heading(img, t, 0.2, "Big buttons, Hindi labels", "Made for older eyes and hands", y=170)
    items = [("चैनल", "Star Plus · Colors · Zee · Sony", 1.2), ("ऐप्स", "Hotstar · YouTube · ZEE5 · SonyLIV · Netflix", 2.8),
             ("रिमोट", "OK pad · volume · channel · number pad", 4.4)]
    for j, (title, sub, st) in enumerate(items):
        a = ease(prog(t, st, 0.6))
        y = 430 + j * 150
        glass(img, (140, y + (1 - a) * 30, 860, y + 120 + (1 - a) * 30), radius=26, alpha=a, fill=(255, 255, 255, 28), outline=(255, 210, 122, 100))
        text(img, (180, y + 16 + (1 - a) * 30), title, 40, GOLD, alpha=a)
        text(img, (180, y + 72 + (1 - a) * 30), sub, 28, MUTED, alpha=a, bold=False)


@scene(41, 47.5)
def wallpapers(img, t):
    names = [("21_wall_royal_top", "शाही"), ("21_wall_peacock_top", "मोर"), ("21_wall_sunset_top", "संध्या")]
    for j, (n, label) in enumerate(names):
        a = ease(prog(t, 0.2 + j * 0.35, 0.9))
        x = W / 2 + (j - 1) * 520
        rot = (1 - j) * 6 * a
        phone(img, lerp(x, x, a), lerp(H + 600, 520, a), 760, [n], rot=rot, alpha=a)
        text(img, (x, 980), label, 44, GOLD, anchor="mm", alpha=ease(prog(t, 1.4 + j * 0.2, 0.5)))
    b = ease(prog(t, 2.2, 0.6))
    text(img, (W / 2, 70 + (1 - b) * 20), "Three wallpapers with a gold rangoli", 52, WHITE, anchor="mm", alpha=b)


@scene(47.5, 55)
def features(img, t):
    heading(img, t, 0.1, "Under the hood", "Kotlin · Jetpack Compose · runs entirely on the phone", y=110)
    cards = [("⇄", "Talks to the TV directly", "Android TV Remote v2 (TLS) + LG webOS — no server"),
             ("अ", "Understands Hinglish offline", "Hindi transliteration + rules; Claude only as fallback"),
             ("✓", "Safe by design", "No root, no firmware changes — Airtel remote still works"),
             ("▶", "Opens exact shows", "Verified show links, live channels by number")]
    for j, (ic, title, sub) in enumerate(cards):
        x = 140 + (j % 2) * 840
        y = 380 + (j // 2) * 260
        feature_card(img, x, y, 800, 200, ic, title, sub, prog(t, 0.6 + j * 0.35, 0.7))


@scene(55, 61)
def outro(img, t):
    a = ease(prog(t, 0.2, 0.9))
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text((W / 2, 440), "Made by Abhinav Jain", font=font(110), fill=SAFFRON + (int(150 * a),), anchor="mm")
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(16)))
    text(img, (W / 2, 440 + (1 - a) * 30), "Made by Abhinav Jain", 110, GOLD, anchor="mm", alpha=a)
    b = ease(prog(t, 0.8, 0.8))
    text(img, (W / 2, 560), "अभिनव जैन द्वारा बनाया गया", 48, WHITE, anchor="mm", alpha=b)
    c = ease(prog(t, 1.4, 0.8))
    text(img, (W / 2, 660), "TV Sahayak  ·  a voice remote for the family", 36, MUTED, anchor="mm", alpha=c, bold=False)
    mic_badge(img, W / 2, 800, t, alpha=ease(prog(t, 1.8, 0.6)))


TOTAL = SCENES[-1][1]
XFADE = 0.5


def render(t):
    img = background(t)
    active = [(s, e, fn) for s, e, fn in SCENES if s - XFADE <= t < e + XFADE]
    for s, e, fn in active:
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        fn(layer, t - s)
        a = min(prog(t, s - XFADE, XFADE) if s > 0 else 1, 1 - prog(t, e, XFADE) if e < TOTAL else 1)
        if a < 1:
            layer.putalpha(layer.getchannel("A").point(lambda v, a=a: int(v * a)))
        img.alpha_composite(layer)
    # fade from / to black
    k = min(prog(t, 0, 0.8), 1 - prog(t, TOTAL - 1.0, 1.0))
    if k < 1:
        img = Image.blend(Image.new("RGBA", img.size, (0, 0, 0, 255)), img, k)
    return img.convert("RGB")


# ---------------------------------------------------------------- music: generated tanpura drone (no copyright)
def tanpura(seconds, sr=44100):
    rng = np.random.default_rng(1)
    out = np.zeros(int(seconds * sr) + sr)

    def pluck(freq, dur):  # Karplus-Strong string with a buzzing bridge (jawari) flavour
        n = int(sr / freq)
        buf = rng.uniform(-1, 1, n)
        res = np.empty(int(dur * sr))
        for i in range(len(res)):
            res[i] = buf[i % n]
            buf[i % n] = 0.4985 * (buf[i % n] + buf[(i + 1) % n])
        return np.tanh(res * 1.6) * np.exp(-np.linspace(0, 2.2, len(res)))

    sa, pa = 130.81, 196.0  # C3 (Sa), G3 (Pa)
    strings = [pluck(pa, 4.0), pluck(sa * 2, 4.0), pluck(sa * 2, 4.0), pluck(sa, 4.5)]
    t, step, k = 0.0, 0.55, 0
    while t < seconds:
        s = strings[k % 4]
        i = int(t * sr)
        seg = s[: len(out) - i]
        out[i:i + len(seg)] += seg * (0.9 if k % 4 == 3 else 0.6)
        t += step if k % 4 != 3 else step * 1.6
        k += 1
    out = out[: int(seconds * sr)]
    # soft pad underneath and gentle fades
    tt = np.arange(len(out)) / sr
    pad = 0.05 * (np.sin(2 * np.pi * sa * tt) + 0.6 * np.sin(2 * np.pi * pa * tt) + 0.4 * np.sin(2 * np.pi * sa * 2 * tt))
    out = out * 0.5 + pad * (0.6 + 0.4 * np.sin(2 * np.pi * tt / 8))
    env = np.minimum(1, np.minimum(tt / 2.5, (seconds - tt) / 3.0))
    out *= np.clip(env, 0, 1)
    out /= np.max(np.abs(out)) + 1e-9
    return (out * 0.6 * 32767).astype(np.int16), sr


def frame_bytes(f):
    return render(f / FPS).tobytes()


def main():
    global BG, RANGOLI
    if not os.path.exists(os.path.join(FRAMES, "01_idle.png")):
        sys.exit("Render the app screens first: ./gradlew :app:testDebugUnitTest --tests '*VideoFramesTest*'")
    BG = make_background()
    RANGOLI = make_rangoli(1000)

    only = sys.argv[1:]  # e.g. "--still 12.5" renders one frame for checking
    if only and only[0] == "--still":
        for ts in only[1:]:
            render(float(ts)).save(os.path.join(ROOT, "tools", "video", f"still_{ts}.png"))
        return

    wav = os.path.join(ROOT, "tools", "video", "tanpura.wav")
    pcm, sr = tanpura(TOTAL)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())

    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
           "-i", wav, "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k",
           "-shortest", "-movflags", "+faststart", OUT]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = int(TOTAL * FPS)
    # Frames are independent, so render them on several cores (fork shares BG/RANGOLI with the workers).
    with multiprocessing.get_context("fork").Pool(max(1, (os.cpu_count() or 2) - 2)) as pool:
        for f, frame in enumerate(pool.imap(frame_bytes, range(n), chunksize=4)):
            ff.stdin.write(frame)
            if f % 150 == 0:
                print(f"frame {f}/{n}", flush=True)
    ff.stdin.close()
    ff.wait()
    print("wrote", OUT)


if __name__ == "__main__":
    main()
