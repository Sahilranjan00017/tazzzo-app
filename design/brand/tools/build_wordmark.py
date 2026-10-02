"""Builds the Tazzzo wordmark as outlined vector paths.

Construction base: Poppins ExtraBold (SIL OFL 1.1) glyph outlines for t a z z z o. Only the OUTLINES are used;
the result is plain path geometry with no font dependency.

Refinements (deliberately restrained):
  - tightened overall tracking;
  - the three z's on a slightly ACCELERATING rhythm (each gap a little shorter than the last) as the single,
    subtle motion cue — no speed lines, no overlap;
  - every convex corner softened (r = ROUND units) for the "slightly rounded" brand feel;
  - a small leaf-shaped notch cut into the upper-right shoulder of the final "o".
"""
import math, sys, json
import pathops
from fontTools.ttLib import TTFont
from fontTools.pens.transformPen import TransformPen

FONT = sys.argv[1]
OUT = sys.argv[2]
COLOR = sys.argv[3] if len(sys.argv) > 3 else "#143528"

ROUND = 24          # corner softening radius, font units (UPM 1000)
BASE_TRACK = -8    # overall tracking
Z_GAPS = [2, -8]  # extra tightening z1->z2, z2->z3 (accelerating rhythm)
Z_TO_O = -10        # z3 -> o
LEAF_POS_DEG = 42   # where on the ring (counter-clockwise from +x)
LEAF_LEN = 0.78     # leaf length, fraction of ring thickness
LEAF_WID = 0.30     # leaf width, fraction of ring thickness
LEAF_TILT = -28     # leaf axis tilt from the ring tangent

font = TTFont(FONT)
gs = font.getGlyphSet()
cmap = font.getBestCmap()


def glyph_path(ch, dx):
    name = cmap[ord(ch)]
    p = pathops.Path()
    pen = TransformPen(p.getPen(), (1, 0, 0, 1, dx, 0))
    gs[name].draw(pen)
    p.simplify()
    return p, gs[name].width


def soften(p, r):
    """Round convex corners: erode by r, then dilate by r (round joins)."""
    s = pathops.Path(); s.addPath(p)
    s.stroke(2 * r, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
    s.convertConicsToQuads()
    inner = pathops.op(p, s, pathops.PathOp.DIFFERENCE)
    s2 = pathops.Path(); s2.addPath(inner)
    s2.stroke(2 * r, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
    s2.convertConicsToQuads()
    return pathops.op(inner, s2, pathops.PathOp.UNION)


def leaf(cx, cy, length, width, angle_deg):
    """A vesica (two circular arcs) leaf centred on (cx, cy), long axis at angle_deg."""
    half = length / 2.0
    # radius of the arcs that make a lens of the given length/width
    h = width / 2.0
    R = (half * half + h * h) / (2 * h)
    p = pathops.Path()
    steps = 24
    pts = []
    for side in (1, -1):
        cyc = -side * (R - h)
        a0 = math.atan2(0 - cyc, -half)
        a1 = math.atan2(0 - cyc, half)
        for i in range(steps + 1):
            t = i / steps
            if side == 1:
                ang = a0 + (a1 - a0) * t
            else:
                ang = a1 + (a0 - a1) * t
            x = R * math.cos(ang); y = cyc + R * math.sin(ang)
            if side == -1:
                pass
            pts.append((x, y))
    th = math.radians(angle_deg)
    first = True
    for x, y in pts:
        X = cx + x * math.cos(th) - y * math.sin(th)
        Y = cy + x * math.sin(th) + y * math.cos(th)
        if first: p.moveTo(X, Y); first = False
        else: p.lineTo(X, Y)
    p.close()
    p.simplify()
    return p


letters = "tazzzo"
x = 0.0
parts = []
for i, ch in enumerate(letters):
    p, adv = glyph_path(ch, x)
    parts.append((ch, p, x, adv))
    nxt = letters[i + 1] if i + 1 < len(letters) else None
    gap = BASE_TRACK
    if ch == "z" and nxt == "z":
        zi = sum(1 for c, *_ in parts if c == "z") - 1
        gap += Z_GAPS[zi]
    if ch == "z" and nxt == "o":
        gap += Z_TO_O
    x += adv + gap

# leaf notch on the final o: a small leaf-shaped window wholly INSIDE the ring at the upper-right shoulder,
# so the outer contour stays a clean circle and the letter still reads as "o".
o_ch, o_path, o_x, o_adv = parts[-1]
cs = sorted(o_path.contours, key=lambda c: abs(c.area))
outer_b, inner_b = cs[-1].bounds, cs[0].bounds
ocx, ocy = (outer_b[0] + outer_b[2]) / 2, (outer_b[1] + outer_b[3]) / 2
r_out = ((outer_b[2] - outer_b[0]) + (outer_b[3] - outer_b[1])) / 4
r_in = ((inner_b[2] - inner_b[0]) + (inner_b[3] - inner_b[1])) / 4
ring = r_out - r_in
r_mid = r_in + ring * 0.52
pos = math.radians(LEAF_POS_DEG)
lx, ly = ocx + r_mid * math.cos(pos), ocy + r_mid * math.sin(pos)
notch = leaf(lx, ly, length=ring * LEAF_LEN, width=ring * LEAF_WID, angle_deg=LEAF_POS_DEG + 90 + LEAF_TILT)
o_cut = pathops.op(o_path, notch, pathops.PathOp.DIFFERENCE)
parts[-1] = (o_ch, o_cut, o_x, o_adv)
print("o ring", round(ring), "r_out", round(r_out))

word = pathops.Path()
for ch, p, *_ in parts:
    word = pathops.op(word, soften(p, ROUND), pathops.PathOp.UNION)
word.simplify()

xmin, ymin, xmax, ymax = word.bounds
W, H = xmax - xmin, ymax - ymin
S = 0.1  # emit in 1/10 font units


def fmt(v):
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


d = []
for verb, pts in word.segments:
    if verb == "moveTo":
        (px, py), = pts; d.append(f"M{fmt((px - xmin) * S)} {fmt((ymax - py) * S)}")
    elif verb == "lineTo":
        (px, py), = pts; d.append(f"L{fmt((px - xmin) * S)} {fmt((ymax - py) * S)}")
    elif verb == "qCurveTo":
        # TrueType implied on-curve points: expand into consecutive Q segments
        ctrl = pts[:-1]; end = pts[-1]
        for k, c in enumerate(ctrl):
            if k + 1 < len(ctrl):
                n = ctrl[k + 1]; on = ((c[0] + n[0]) / 2, (c[1] + n[1]) / 2)
            else:
                on = end
            d.append(f"Q{fmt((c[0] - xmin) * S)} {fmt((ymax - c[1]) * S)} {fmt((on[0] - xmin) * S)} {fmt((ymax - on[1]) * S)}")
    elif verb == "curveTo":
        (a, b2, c) = pts
        d.append("C" + " ".join(f"{fmt((q[0] - xmin) * S)} {fmt((ymax - q[1]) * S)}" for q in (a, b2, c)))
    elif verb == "closePath":
        d.append("Z")
    else:
        raise SystemExit("unexpected verb " + verb)
pathdata = "".join(d)
vw, vh = fmt(W * S), fmt(H * S)
json.dump({"viewW": vw, "viewH": vh, "d": pathdata, "color": COLOR}, open(OUT, "w"))
print("bounds", round(W), "x", round(H), "units; path chars", len(pathdata))
