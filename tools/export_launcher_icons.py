"""Export Android adaptive/legacy icons from one vector design.

Usage: python tools/export_launcher_icons.py --resvg-module <path to @resvg/resvg-js>
Requires Pillow and @resvg/resvg-js. Outputs retain transparent launcher masks.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

BACKGROUND = [
    {"fill": "#263753", "d": "M0,0H108V108H0Z"},
    {"fill": "#304865", "d": "M0,0H108V14C69,16 30,50 0,86Z"},
]
FOREGROUND = [
    {"fill": "#182A44", "d": "M39,31H60L73,43V72Q73,78 67,78H39Q33,78 33,72V37Q33,31 39,31Z"},
    {"fill": "#FFF7E9", "d": "M38,28H58L70,40V69Q70,75 64,75H38Q32,75 32,69V34Q32,28 38,28Z"},
    {"fill": "#A8DED4", "d": "M58,28V35Q58,40 63,40H70Z"},
    {"fill": "none", "stroke": "#354B64", "stroke-width": "3", "stroke-linecap": "round", "d": "M41,41H49M41,48H47"},
    {"fill": "#FFF7E9", "stroke": "#263753", "stroke-width": "4.5", "d": "M68,59A11,11 0,1 0,46 59A11,11 0,1 0,68 59"},
    {"fill": "none", "stroke": "#65E0C3", "stroke-width": "6", "stroke-linecap": "round", "d": "M65.5,67.5L75,77"},
    {"fill": "#3CA895", "d": "M57,52L59,57L64,59L59,61L57,66L55,61L50,59L55,57Z"},
    {"fill": "#F4C879", "d": "M74,29L75.4,32.6L79,34L75.4,35.4L74,39L72.6,35.4L69,34L72.6,32.6Z"},
]
MONOCHROME = [
    {"fill": "none", "stroke": "#FFFFFF", "stroke-width": "4", "stroke-linejoin": "round", "stroke-linecap": "round", "d": "M42,75H38Q32,75 32,69V34Q32,28 38,28H58L70,40V45M58,28V40H70M41,41H49M41,48H45M68,59A11,11 0,1 0,46 59A11,11 0,1 0,68 59M65.5,67.5L75,77"},
    {"fill": "#FFFFFF", "d": "M57,52L59,57L64,59L59,61L57,66L55,61L50,59L55,57Z"},
]

def vector(paths):
    node = ET.Element("vector", {f"{{{ANDROID}}}{key}": value for key, value in
        {"width": "108dp", "height": "108dp", "viewportWidth": "108", "viewportHeight": "108"}.items()})
    names = {"fill": "fillColor", "stroke": "strokeColor", "stroke-width": "strokeWidth",
        "stroke-linecap": "strokeLineCap", "stroke-linejoin": "strokeLineJoin", "d": "pathData"}
    for shape in paths:
        ET.SubElement(node, "path", {f"{{{ANDROID}}}{names[k]}": "#00000000" if v == "none" else v for k, v in shape.items()})
    ET.indent(node)
    return ET.tostring(node, encoding="unicode") + "\n"

def svg(round_icon=False):
    mask = '<circle cx="54" cy="54" r="54"/>' if round_icon else '<rect width="108" height="108" rx="25"/>'
    paths = "".join(ET.tostring(ET.Element("path", shape), encoding="unicode") for shape in BACKGROUND + FOREGROUND)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><defs><clipPath id="mask">{mask}</clipPath></defs><g clip-path="url(#mask)">{paths}</g></svg>'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resvg-module", required=True)
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args()
    for name, paths in (("background", BACKGROUND), ("foreground", FOREGROUND), ("monochrome", MONOCHROME)):
        (RES / f"drawable/ic_launcher_{name}.xml").write_text(vector(paths), encoding="utf-8")
    for name in ("ic_launcher", "ic_launcher_round"):
        path = RES / f"mipmap-anydpi-v26/{name}.xml"
        path.write_text(path.read_text(encoding="utf-8").replace(
            '<monochrome android:drawable="@drawable/ic_launcher_foreground"',
            '<monochrome android:drawable="@drawable/ic_launcher_monochrome"'), encoding="utf-8")
    source = ROOT / "docs/branding"
    source.mkdir(parents=True, exist_ok=True)
    (source / "launcher.svg").write_text(svg(), encoding="utf-8")
    with tempfile.TemporaryDirectory(prefix="hanako-icons-") as directory:
        temporary = Path(directory)
        script = temporary / "render.cjs"
        script.write_text("const fs=require('fs'); const {Resvg}=require(process.argv[2]); "
            "const svg=fs.readFileSync(process.argv[3],'utf8'); "
            "fs.writeFileSync(process.argv[4],new Resvg(svg,{fitTo:{mode:'width',value:1024}}).render().asPng());", encoding="utf-8")
        for round_icon in (False, True):
            input_file, output_file = temporary / "icon.svg", temporary / "icon.png"
            input_file.write_text(svg(round_icon), encoding="utf-8")
            subprocess.run(["node", str(script), args.resvg_module, str(input_file), str(output_file)], check=True)
            with Image.open(output_file) as image:
                for density, size in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
                    name = "ic_launcher_round" if round_icon else "ic_launcher"
                    image.resize((size,size), Image.Resampling.LANCZOS).save(RES / f"mipmap-{density}/{name}.webp", lossless=True)
                if args.preview and not round_icon:
                    args.preview.parent.mkdir(parents=True, exist_ok=True)
                    image.save(args.preview)
    print("Exported adaptive, monochrome, and ten density-specific launcher icons.")

if __name__ == "__main__":
    main()
