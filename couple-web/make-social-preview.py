"""Generate a sharp 1200x630 Open Graph image for LoveRoll link previews.
Only standard Pillow and available system fonts; deterministic and no secrets.
The output is a static public asset, never a personalized invitation URL.
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, os

WIDTH, HEIGHT, SCALE = 1200, 630, 2
w, h = WIDTH * SCALE, HEIGHT * SCALE
image = Image.new("RGB", (w, h), "#1a0b1b")
draw = ImageDraw.Draw(image)
for y in range(h):
    t = y / max(1, h - 1)
    draw.line((0, y, w, y), fill=(int(25 + 15*t), int(10 + 8*t), int(25 + 14*t)))

lights = Image.new("RGBA", (w, h), (0, 0, 0, 0))
ld = ImageDraw.Draw(lights, "RGBA")
for cx, cy, r, color in [
    (400, 275, 440, (230, 28, 103, 135)),
    (95, 590, 290, (119, 36, 100, 125)),
    (1080, 40, 350, (99, 22, 70, 90)),
]:
    ld.ellipse(((cx-r)*SCALE, (cy-r)*SCALE,
                (cx+r)*SCALE, (cy+r)*SCALE), fill=color)
image = Image.alpha_composite(image.convert("RGBA"),
                              lights.filter(ImageFilter.GaussianBlur(105*SCALE)))
draw = ImageDraw.Draw(image, "RGBA")

for i in range(8):
    pts = [(x*SCALE, int((392 + 110*math.sin(x/175 + i*.16) + i*16)*SCALE))
           for x in range(0, 565, 6)]
    draw.line(pts, fill=(255, 75, 155, max(9, 34-i*3)),
              width=3*SCALE, joint="curve")

draw.rounded_rectangle((19*SCALE,19*SCALE,(WIDTH-19)*SCALE,(HEIGHT-19)*SCALE),
                       radius=29*SCALE, outline=(255,142,195,95), width=2*SCALE)

def heart_points(cx, cy, size):
    result = []
    for angle in range(0, 361, 2):
        t = math.radians(angle)
        x = 16 * math.sin(t)**3
        y = (13*math.cos(t) - 5*math.cos(2*t)
             - 2*math.cos(3*t) - math.cos(4*t))
        result.append(((cx+size*x/17)*SCALE, (cy-size*y/17)*SCALE))
    return result

halo = Image.new("RGBA", (w, h), (0,0,0,0))
hd = ImageDraw.Draw(halo, "RGBA")
hd.polygon(heart_points(317, 283, 164), fill=(249,42,118,105))
image = Image.alpha_composite(image, halo.filter(ImageFilter.GaussianBlur(24*SCALE)))
draw = ImageDraw.Draw(image, "RGBA")
draw.polygon(heart_points(317, 283, 132), fill=(171,14,73,245))
draw.line(heart_points(317,283,132),
          fill=(255,145,190,235),width=5*SCALE,joint="curve")
draw.line(heart_points(317,283,124),
          fill=(255,84,143,145),width=3*SCALE,joint="curve")
draw.ellipse((258*SCALE,178*SCALE,300*SCALE,205*SCALE),
             fill=(255,213,225,65))

def draw_die(cx, cy, angle, kind):
    patch = Image.new("RGBA", (126*SCALE,126*SCALE), (0,0,0,0))
    pd = ImageDraw.Draw(patch, "RGBA")
    b=(19*SCALE,18*SCALE,107*SCALE,106*SCALE)
    pd.rounded_rectangle((b[0]+7*SCALE,b[1]+8*SCALE,b[2]+7*SCALE,b[3]+8*SCALE),
                         radius=17*SCALE,fill=(0,0,0,145))
    pd.rounded_rectangle(b,radius=17*SCALE,fill=(15,13,22,255),
                         outline=(255,155,205,205),width=2*SCALE)
    pd.line((35*SCALE,24*SCALE,90*SCALE,24*SCALE),
            fill=(255,231,244,155),width=3*SCALE)
    if kind=="heart":
        pd.polygon(heart_points(63,62,23),fill=(255,73,155,255))
    else:
        for x,y in [(44,44),(80,44),(44,80),(80,80)]:
            pd.ellipse(((x-6)*SCALE,(y-6)*SCALE,(x+6)*SCALE,(y+6)*SCALE),
                       fill=(255,92,165,255))
    patch=patch.rotate(angle,expand=True,resample=Image.Resampling.BICUBIC)
    image.alpha_composite(patch, (cx*SCALE-patch.width//2,cy*SCALE-patch.height//2))

draw_die(230,478,-13,"heart")
draw_die(350,478,17,"dots")

def font(size,bold=True):
    candidates=(
      ["/usr/share/fonts/truetype/lato/Lato-Heavy.ttf",
       "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]
      if bold else
      ["/usr/share/fonts/truetype/lato/Lato-Regular.ttf",
       "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]
    )
    path=next((p for p in candidates if os.path.exists(p)),None)
    return ImageFont.truetype(path,size*SCALE) if path else ImageFont.load_default()

d=ImageDraw.Draw(image)
d.text((615*SCALE,112*SCALE),"LOVEROLL",font=font(61),fill=(255,243,250))
d.rounded_rectangle((614*SCALE,203*SCALE,943*SCALE,244*SCALE),
                    radius=13*SCALE,fill=(116,37,88,145),
                    outline=(205,94,147,155),width=2*SCALE)
d.text((634*SCALE,210*SCALE),"INVITATION PRIVÉE",
       font=font(20),fill=(255,185,218))
d.text((615*SCALE,280*SCALE),"Une aventure",
       font=font(55),fill=(255,241,249))
d.text((615*SCALE,344*SCALE),"à deux.",
       font=font(64),fill=(255,103,170))
d.text((617*SCALE,434*SCALE),"Complicité   •   Surprises   •   Tendresse",
       font=font(21,False),fill=(235,195,214))
d.line((618*SCALE,495*SCALE,1113*SCALE,495*SCALE),
       fill=(159,82,128),width=2*SCALE)
d.text((617*SCALE,519*SCALE),"Ouvrez votre invitation pour nous rejoindre",
       font=font(21,False),fill=(243,213,231))

output=os.path.join(os.path.dirname(__file__),"assets","loveroll-invite-og.png")
os.makedirs(os.path.dirname(output),exist_ok=True)
image.convert("RGB").resize((WIDTH,HEIGHT),Image.Resampling.LANCZOS).save(output,optimize=True)
print("LoveRoll OG:",output,os.path.getsize(output),"bytes")
