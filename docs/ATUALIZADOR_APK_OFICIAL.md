# Controle de Cancelamentos Elite — Atualizador por APK oficial

## Mesmo modelo usado no Scout PRO
O projeto Scout PRO usa um segredo GitHub `ANDROID_RELEASE_SECRET` e um arquivo
criptografado de assinatura permanente; depois publica um APK em Releases.
O Controle Elite agora também suporta um segredo mestre com o mesmo **nome**,
porém a identidade da chave é **outra**, específica do pacote
`br.com.controlecancelamentos`.

**Segredos de GitHub são associados ao repositório.** O segredo do Scout PRO
não é automaticamente visível no repositório Controle Elite. Não colocar
senha, master secret, chaves ou keystore no código-fonte, prints ou chat.

## Primeiro passo do proprietário — configurar segredo
No repositório `canalescanorff6-source/controle-cancelamentos-elite-android`:
Settings > Secrets and variables > Actions > New repository secret.
Nome: `ANDROID_RELEASE_SECRET`.
Valor: segredo aleatório longo (40–90 caracteres, letras, números, `_` e `-`).
Guarde cópia em gerenciador de senhas. Não é possível visualizar o valor
depois que o GitHub Secret é salvo; perder o segredo impede recuperar
a chave criptografada para assinar futuras atualizações.

O sistema também aceita **alternativamente** quatro segredos de keystore
preexistente, caso a mesma chave privada já esteja sendo usada:
`ELITE_KEYSTORE_BASE64`, `ELITE_KEYSTORE_PASSWORD`,
`ELITE_KEY_ALIAS`, `ELITE_KEY_PASSWORD`.
Escolha um método e mantenha-o permanentemente.

## Fluxo de publicação após merge e CI GREEN
1. Aprovar e mesclar a PR #3 no branch `main` só quando a instalação e a
   migração de dados estiverem revisadas.
2. Um push aprovado em `main` inicia `Android | APK oficial e atualizador`.
3. Na **primeira execução com segredo mestre** o runner gera a chave PKCS12 e
   registra apenas a sua cópia **AES-256-GCM criptografada** em
   `.github/android-signing/elite-release.enc.json`. O certificado público
   (fingerprint) é preservado. Não são publicados keystore ou senhas em claro.
4. O workflow compila/testa o APK, atribui um `versionCode` incremental
   e publica `Controle_Cancelamentos_Elite_vX.Y.N.apk` nos GitHub Releases.
5. Novos commits aprovados em `main` repetem o processo, sempre com
   a mesma identidade criptográfica. A ferramenta exige validação de
   assinatura, SHA-256, identificador e versão antes de publicar.
6. No Android, Gestão > Atualizações > Verificar > Baixar > Instalar.
   O aplicativo confere hash, package name, versionCode e certificado
   antes de abrir o instalador oficial do Android. A confirmação é manual.

GitHub Actions Artifacts são temporários e **não são** o canal de atualização.
O atualizador consulta sempre o último GitHub Release oficial.

## Migração de APK de testes para assinatura oficial
**Nunca desinstale o app antes de salvar o histórico e as fotografias.**
APKs debug anteriores podem ter chaves diferentes, mesmo sendo do mesmo
projeto. Android bloqueia instalar um APK com assinatura diferente por cima.
Se a versão instalada for debug incompatível, faça uma migração única por
backup/restauração antes de usar o primeiro APK oficial.

A V4.3 contém backup `.ccf3` com fotos. O backup antigo `.ccel` não inclui
fotos e usuários. Se não for possível instalar V4.3 sobre o app atual,
não desinstale: exporte o que for possível e preserve os anexos separadamente.
Após a primeira instalação oficial, as atualizações seguintes mantêm os
dados sem reinstalação, desde que a assinatura permaneça estável.

## Homologação obrigatória
- Confirmar que o segredo de assinatura está configurado no **repositório Elite**.
- Aprovar instalação, backup completo e restauração de fotos em Android real.
- Publicar duas versões oficiais consecutivas com o mesmo certificado.
- Conferir atualização via botão de dentro do app, mantendo um registro
  existente, a fotografia e o login.
- Testar falta de rede e release inválida: o app deve bloquear instalação.

O workflow de build verde não comprova um update real na instalação com
assinatura diferente. Nenhuma promessa de atualização silenciosa.
