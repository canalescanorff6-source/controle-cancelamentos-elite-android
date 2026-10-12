# Controle de Cancelamentos Elite — atualizações Android assinadas

## Estado da assinatura

O APK de teste V3.2 usa applicationId `br.com.controlecancelamentos.teste` (instalação separada).
O aplicativo de produção usa `br.com.controlecancelamentos`.
**Os builds debug anteriores não compartilham uma chave de assinatura permanente.**
Uma nova assinatura de produção não pode atualizar automaticamente um APK antigo
assinado com outra chave, ainda que o nome e o `applicationId` sejam iguais.

**Não desinstale nenhuma instalação que tenha registros importantes.** O backup atual é
parcial: exporta lojas, cancelamentos ativos e identidade visual, mas não exporta
imagens dos cupons, usuários, senhas nem registros excluídos.

## Preparar a assinatura permanente (uma única vez, fora do GitHub)

É necessário um computador com JDK e `keytool`. Guarde o arquivo em um
diretório fora do repositório, de preferência com duas cópias offline protegidas.

Exemplo (macOS/Linux; o comando solicitará as senhas interativamente):

```bash
mkdir -p ~/elite-signing
chmod 700 ~/elite-signing
keytool -genkeypair -v \
  -keystore ~/elite-signing/elite-signing.p12 -storetype PKCS12 \
  -alias elite-android -keyalg RSA -keysize 3072 -validity 9125 \
  -dname "CN=Controle de Cancelamentos Elite, OU=Android"
```

**Nunca** adicione o arquivo `.p12`/`.jks` ao Git, não compartilhe senhas no
chat e não deixe cópias em pastas públicas.

## Configurar os quatro GitHub Actions Secrets

No GitHub: **Settings → Secrets and variables → Actions → New repository secret**.
Configure os nomes exatos:

- `ELITE_SIGNING_KEYSTORE_BASE64`: conteúdo Base64 *do arquivo binário inteiro*.
  No macOS/Linux: `base64 < ~/elite-signing/elite-signing.p12 | tr -d '\n'`.
  Base64 não é criptografia; cole apenas no campo protegido do GitHub.
- `ELITE_SIGNING_STORE_PASSWORD`: senha do arquivo.
- `ELITE_SIGNING_KEY_ALIAS`: `elite-android` (ou alias escolhido).
- `ELITE_SIGNING_KEY_PASSWORD`: senha da chave (em PKCS12 normalmente igual à senha do arquivo).

Não publique screenshots desses segredos. A chave deve ser preservada para todas
as futuras atualizações. A perda da chave pode impedir novas atualizações in-place.

## Gerar o APK pela primeira vez

Depois de aprovar e integrar a implementação na `main`, acesse
**Actions → Android | APK de produção assinado (manual) → Run workflow**,
selecione a branch `main`. O workflow:

1. Faz a auditoria do código, roda testes e compila o APK release.
2. Recupera a chave apenas dos Secrets no runner efêmero.
3. Assina, confere o certificado e o applicationId.
4. Publica somente em **Artifacts**. Não cria release e não distribui
   automaticamente a APK aos aparelhos.

Baixe o Artifact (o GitHub entrega ZIP), extraia
`Controle-Cancelamentos-Elite-assinado.apk` e confira o hash SHA-256.
Guarde o certificado/fingerprint da primeira versão e compare com as próximas.

## Regras permanentes para atualizações

- Manter o **mesmo** `applicationId` e o mesmo certificado de assinatura.
- Incrementar `versionCode` em todas as versões publicadas.
- Exigir CI GREEN, testes Android e backup antes de atualização de dados.
- Assinar apenas a partir da branch `main`, após autorização explícita.
- Não afirmar que atualizará a V3.1 debug: sem o certificado original isso
  não é tecnicamente possível. Não desinstalar a versão antiga antes de exportar
  e conferir os dados e fotografias.
- Para o teste V3.2 separado, só a combinação
  `br.com.controlecancelamentos.teste` + assinatura correspondente permitiria
  atualizar mantendo os dados.
- Futuramente poderemos criar backups completos e migração com validação em
  aparelho físico, antes da transição definitiva.
