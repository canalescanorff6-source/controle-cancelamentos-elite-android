#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/check_source.py
if ! command -v git >/dev/null 2>&1; then
  echo 'ERRO: Git não instalado.' >&2
  exit 1
fi
if [[ ! -d .git ]]; then
  git init -b main
fi
if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin https://github.com/canalescanorff6-source/controle-cancelamentos-elite-android.git
fi
git add .
git diff --cached --check
echo 'Arquivos prontos para commit:'
git diff --cached --stat
echo
echo 'Confira as alterações e, com sua autorização, execute:'
echo '  git commit -m "feat(android): iniciar Controle de Cancelamentos Elite V3"'
echo '  git push -u origin main'
echo 'O script NÃO publica, NÃO faz commit e NÃO envia dados ao GitHub.'
