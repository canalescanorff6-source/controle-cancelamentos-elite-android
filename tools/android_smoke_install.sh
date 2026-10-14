#!/usr/bin/env bash
set -euo pipefail

package="br.com.controlecancelamentos.teste.visual"
apk="app/build/outputs/apk/debug/app-debug.apk"

test -s "$apk"
adb wait-for-device
echo "Instalando $package no emulador"
adb install -r "$apk"
adb shell pm path "$package" | grep -q "package:"
adb logcat -c
echo "Abrindo activity principal"
adb shell am start -W -n "$package/br.com.controlecancelamentos.MainActivity"
sleep 8
if ! adb shell pidof "$package" | grep -q '[0-9]'; then
  adb logcat -d -t 400 | tail -n 120
  echo "ERRO: app não permaneceu ativo após inicialização"
  exit 1
fi
echo "PASSOU: APK instalado, activity iniciada e processo ativo no Android 15/API 35"
