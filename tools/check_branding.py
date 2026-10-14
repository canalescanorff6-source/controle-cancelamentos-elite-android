#!/usr/bin/env python3
"""Barreira de regressão: a arte exibida no Android deve usar a mesma fonte visual."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/res"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
app = manifest.find("application")
assert app is not None
assert app.get(android + "icon") == "@mipmap/ic_launcher"
assert app.get(android + "roundIcon") == "@mipmap/ic_launcher_round"
assert (assets/"drawable-nodpi/elite_brand.webp").stat().st_size > 1000
for launcher in ["ic_launcher.xml", "ic_launcher_round.xml"]:
    root_icon = ET.parse(assets/"mipmap-anydpi-v26"/launcher).getroot()
    fg = root_icon.find("foreground")
    assert fg is not None and fg.get(android+"drawable") == "@drawable/elite_brand_safe", launcher
safe = ET.parse(assets/"drawable/elite_brand_safe.xml").getroot()
assert safe.get(android+"drawable") == "@drawable/elite_brand"
styles = (assets/"values/styles.xml").read_text(encoding="utf-8")
assert '<item name="windowSplashScreenAnimatedIcon">@drawable/elite_brand_safe</item>' in styles
ui = (root/"app/src/main/java/br/com/controlecancelamentos/ui/AppScreens.kt").read_text(encoding="utf-8")
assert ui.count("painterResource(R.drawable.elite_brand)") >= 4
assert "painterResource(R.drawable.ic_logo)" not in ui
build = (root/"app/build.gradle.kts").read_text(encoding="utf-8")
assert re.search(r'versionCode\s*=\s*6\b',build)
assert re.search(r'versionName\s*=\s*"3\.2\.1"',build)
assert 'applicationIdSuffix = ".teste.visual"' in build
label=(root/"app/src/debug/res/values/strings.xml").read_text(encoding="utf-8")
assert "Visual (Teste)" in label
print("PASS: launcher, ícone redondo, splash, login, cabeçalho e versão do app alinhados.")
