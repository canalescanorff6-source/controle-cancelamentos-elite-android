#!/usr/bin/env python3
"""Early CI gate: verify expected native Android structure and reject risky files."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
required = (
    "settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/br/com/controlecancelamentos/MainActivity.kt",
    "app/src/main/java/br/com/controlecancelamentos/data/Model.kt",
    "app/src/main/java/br/com/controlecancelamentos/ocr/ReceiptReader.kt",
    ".github/workflows/android.yml",
)
errors = [f"Arquivo obrigatório ausente: {name}" for name in required if not (ROOT / name).is_file()]

gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
for regex, message in [
    (r'applicationId\s*=\s*"br\.com\.controlecancelamentos"', "Identificador do app alterado: confira a compatibilidade da atualização"),
    (r'versionCode\s*=\s*3\b', "Versão Android inesperada; revise o versionCode"),
    (r'implementation\("com\.google\.mlkit:text-recognition:', "Dependência ML Kit OCR ausente"),
]:
    if not re.search(regex, gradle):
        errors.append(message)

try:
    paths = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True, stderr=subprocess.DEVNULL).splitlines()
except (subprocess.CalledProcessError, FileNotFoundError):
    # Package validation before the initial commit.
    paths = [p.relative_to(ROOT).as_posix() for p in ROOT.rglob("*") if p.is_file()]

unsafe_patterns = (
    re.compile(r"(^|/)(local\.properties|google-services\.json)$", re.I),
    re.compile(r"\.(jks|keystore|p12|pfx|ccel|apk|aab|db|db-wal|db-shm)$", re.I),
    re.compile(r"(^|/)(cupons|capturas|relatorios|captures)/", re.I),
)
for path in paths:
    if any(pattern.search(path) for pattern in unsafe_patterns):
        errors.append(f"Arquivo sensível no código versionado: {path}")

if errors:
    for error in errors:
        print(f"ERRO: {error}", file=sys.stderr)
    sys.exit(1)
print(f"PASS: arquivos essenciais, identidade Android e {len(paths)} arquivos inspecionados")
