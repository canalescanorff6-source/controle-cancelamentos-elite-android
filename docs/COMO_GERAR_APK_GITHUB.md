# Publicação inicial e APK — Controle de Cancelamentos Elite V3

## Antes de publicar

- O repositório está **público** na inspeção inicial. Não inclua cupons reais, fotos, CSVs, backups, bancos, senhas ou chaves. Para manter o código privado, altere a visibilidade em **Settings → General → Danger Zone → Change repository visibility**, antes do upload.
- Este pacote foi preparado para commit, mas **não foi publicado automaticamente** e **não contém APK**.
- Preserve o `applicationId` e o banco atual ao atualizar versões existentes.

## 1. Criar o commit inicial no seu repositório

URL: `https://github.com/canalescanorff6-source/controle-cancelamentos-elite-android`

Envie **o conteúdo desta pasta**, incluindo os diretórios ocultos `.github` e os arquivos `.gitignore` e `.gitattributes`; não envie o ZIP como um arquivo dentro do repositório.

No Git local (uma vez que o repositório esteja vazio):

```bash
git clone https://github.com/canalescanorff6-source/controle-cancelamentos-elite-android.git
cd controle-cancelamentos-elite-android
# Copie para esta pasta o conteúdo do pacote descompactado.
bash tools/preparar_commit_inicial.sh
git add .
git status --short
git commit -m "feat(android): iniciar Controle de Cancelamentos Elite V3 nativo"
git push origin main
```

**Nunca commite dados fiscais reais nem credenciais.**

## 2. GitHub Actions gera o APK debug

1. Abra **Actions → Android | Testes e APK V3**.
2. Após o push na `main`, o build é automático, ou use **Run workflow**.
3. Confirme o status verde da execução (**Success**); caso haja falha (**Failure**), abra os logs do passo vermelho para corrigir a causa real.
4. Na execução bem-sucedida, baixe o artefato **Controle-Cancelamentos-Elite-V3-debug**.
5. Descompacte o artefato e instale `app-debug.apk` no Android. O arquivo `app-debug.apk.sha256` permite conferir a integridade do download.

**Importante:** APK debug é assinado automaticamente pela infraestrutura de build, mas a assinatura debug **não é adequada para atualizações permanentes ou distribuição oficial**. O fluxo de release precisa de keystore persistente e regras de distribuição aprovadas.

## 3. Checklist de aceite Android (pendente até testes)

- Instalar no Android 8.0+, iniciar, cadastrar administrador.
- Criar lojas e operadores, permissões e sessão após reiniciar.
- Salvar/editar/excluir cancelamento; conferir valores e datas inválidas.
- OCR com cupons reais de formatos variados e falhas tratadas.
- PDF, CSV, impressão, gráfico mensal, tema claro/escuro.
- Backup/restore completo do escopo atual e atualização preservando dados.
- Verificar consumo de memória e acessibilidade em aparelhos intermediários.

## Diagnóstico de build

O workflow executa `testDebugUnitTest` e `assembleDebug` com JDK 17, Gradle 8.13 e SDK 36. O pacote de código pode ser submetido ao CI imediatamente, mas não declare `GREEN` até a primeira execução terminar com sucesso.
