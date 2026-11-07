"""Export Android adaptive/legacy icons from one vector design.

Usage: python tools/export_launcher_icons.py --resvg-module <path to @resvg/resvg-js>
Requires Pillow and @resvg/resvg-js. Edit docs/branding/cat.svg, then run this
exporter. All launcher assets and the in-app icon share that vector geometry.
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
SOURCE = ROOT / "docs/branding/cat.svg"
SVG = "http://www.w3.org/2000/svg"

def design_layers():
    root = ET.parse(SOURCE).getroot()
    layers = {}
    for name in ("background", "foreground", "monochrome"):
        group = root.find(f"{{{SVG}}}g[@id='{name}']")
        if group is None or not len(group):
            raise ValueError(f"Missing SVG layer: {name}")
        if any(child.tag != f"{{{SVG}}}path" for child in group):
            raise ValueError("Icon layers must contain explicit path elements")
        layers[name] = [dict(child.attrib) for child in group]
    return layers

def vector(paths, cropped=False):
    size = "72" if cropped else "108"
    node = ET.Element("vector", {f"{{{ANDROID}}}{key}": value for key, value in
        {"width": f"{size}dp", "height": f"{size}dp", "viewportWidth": size, "viewportHeight": size}.items()})
    parent = ET.SubElement(node, "group", {
        f"{{{ANDROID}}}translateX": "-18", f"{{{ANDROID}}}translateY": "-18"
    }) if cropped else node
    names = {"fill": "fillColor", "stroke": "strokeColor", "stroke-width": "strokeWidth",
        "stroke-linecap": "strokeLineCap", "stroke-linejoin": "strokeLineJoin", "d": "pathData", "fill-rule": "fillType"}
    for shape in paths:
        attrs = {f"{{{ANDROID}}}{names[k]}": {"none": "#00000000", "evenodd": "evenOdd", "nonzero": "nonZero"}.get(v, v)
                 for k, v in shape.items()}
        ET.SubElement(parent, "path", attrs)
    ET.indent(node)
    return ET.tostring(node, encoding="unicode") + "\n"

def svg(layers, round_icon=False):
    # Adaptive launchers expose the middle 72dp of the 108dp layers. Match that
    # scale for legacy launchers and the app bar instead of shrinking the mark.
    mask = '<circle cx="54" cy="54" r="36"/>' if round_icon else '<rect x="18" y="18" width="72" height="72" rx="18"/>'
    paths = "\n".join(ET.tostring(ET.Element("path", shape), encoding="unicode") for shape in layers["background"] + layers["foreground"])
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72"><defs><clipPath id="mask">{mask}</clipPath></defs><g clip-path="url(#mask)">\n{paths}\n</g></svg>\n'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resvg-module", required=True)
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args()
    layers = design_layers()
    for name, paths in layers.items():
        (RES / f"drawable/ic_launcher_{name}.xml").write_text(vector(paths), encoding="utf-8")
    (RES / "drawable/ic_app_icon.xml").write_text(
        vector(layers["background"] + layers["foreground"], cropped=True), encoding="utf-8")
    for name in ("ic_launcher", "ic_launcher_round"):
        path = RES / f"mipmap-anydpi-v26/{name}.xml"
        path.write_text(path.read_text(encoding="utf-8").replace(
            '<monochrome android:drawable="@drawable/ic_launcher_foreground"',
            '<monochrome android:drawable="@drawable/ic_launcher_monochrome"'), encoding="utf-8")
    source = ROOT / "docs/branding"
    source.mkdir(parents=True, exist_ok=True)
    (source / "launcher.svg").write_text(svg(layers), encoding="utf-8")
    with tempfile.TemporaryDirectory(prefix="zuoti-icons-") as directory:
        temporary = Path(directory)
        script = temporary / "render.cjs"
        script.write_text("const fs=require('fs'); const {Resvg}=require(process.argv[2]); "
            "const svg=fs.readFileSync(process.argv[3],'utf8'); "
            "fs.writeFileSync(process.argv[4],new Resvg(svg,{fitTo:{mode:'width',value:1024}}).render().asPng());", encoding="utf-8")
        for round_icon in (False, True):
            input_file, output_file = temporary / "icon.svg", temporary / "icon.png"
            input_file.write_text(svg(layers, round_icon), encoding="utf-8")
            subprocess.run(["node", str(script), args.resvg_module, str(input_file), str(output_file)], check=True)
            with Image.open(output_file) as image:
                for density, size in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
                    name = "ic_launcher_round" if round_icon else "ic_launcher"
                    image.resize((size,size), Image.Resampling.LANCZOS).save(RES / f"mipmap-{density}/{name}.webp", lossless=True)
                if args.preview and not round_icon:
                    args.preview.parent.mkdir(parents=True, exist_ok=True)
                    image.save(args.preview)
    print("Exported shared app icon, adaptive layers, monochrome, and ten legacy icons from cat.svg.")

if __name__ == "__main__":
    main()
