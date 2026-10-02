"""Compact 'tz' brand mark: the first two letters of the wordmark, same outlines (Poppins ExtraBold, OFL), same corner
softening, with the wordmark's leaf notch moved to the z's lower-right terminal so the mark carries the same signature."""
import math, sys, json, pathops
from fontTools.ttLib import TTFont
from fontTools.pens.transformPen import TransformPen
FONT, OUT = sys.argv[1], sys.argv[2]
ROUND = 24; TRACK = -34
font = TTFont(FONT); gs = font.getGlyphSet(); cmap = font.getBestCmap()
def glyph(ch, dx):
    p = pathops.Path(); gs[cmap[ord(ch)]].draw(TransformPen(p.getPen(), (1,0,0,1,dx,0))); p.simplify(); return p, gs[cmap[ord(ch)]].width
def soften(p, r):
    s = pathops.Path(); s.addPath(p); s.stroke(2*r, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4); s.convertConicsToQuads()
    inner = pathops.op(p, s, pathops.PathOp.DIFFERENCE)
    s2 = pathops.Path(); s2.addPath(inner); s2.stroke(2*r, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4); s2.convertConicsToQuads()
    return pathops.op(inner, s2, pathops.PathOp.UNION)
t, wt = glyph('t', 0); z, wz = glyph('z', wt + TRACK)
mark = pathops.op(soften(t, ROUND), soften(z, ROUND), pathops.PathOp.UNION); mark.simplify()
xmin, ymin, xmax, ymax = mark.bounds; W, H = xmax-xmin, ymax-ymin; S = 0.1
def f(v): s=f"{v:.1f}"; return s[:-2] if s.endswith('.0') else s
d=[]
for verb, pts in mark.segments:
    if verb=="moveTo": (x,y),=pts; d.append(f"M{f((x-xmin)*S)} {f((ymax-y)*S)}")
    elif verb=="lineTo": (x,y),=pts; d.append(f"L{f((x-xmin)*S)} {f((ymax-y)*S)}")
    elif verb=="qCurveTo":
        ctrl=pts[:-1]; end=pts[-1]
        for k,c in enumerate(ctrl):
            on = ((c[0]+ctrl[k+1][0])/2,(c[1]+ctrl[k+1][1])/2) if k+1<len(ctrl) else end
            d.append(f"Q{f((c[0]-xmin)*S)} {f((ymax-c[1])*S)} {f((on[0]-xmin)*S)} {f((ymax-on[1])*S)}")
    elif verb=="curveTo": d.append("C"+" ".join(f"{f((q[0]-xmin)*S)} {f((ymax-q[1])*S)}" for q in pts))
    elif verb=="closePath": d.append("Z")
json.dump({"viewW": f(W*S), "viewH": f(H*S), "d": "".join(d)}, open(OUT,"w")); print("tz bounds", round(W), "x", round(H))
