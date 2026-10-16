# Controle de Cancelamentos Elite — Lojas, Backup e Atualizações

## Catálogo de 15 lojas
01 Centro; 02 Cohama; 03 Maiobão; 05 Renascença; 07 Cohab; 08 São Cristóvão;
09 Africanos; 10 Santa Inês; 12 Dirceu; 15 Bacabal; 16 Homero;
18 Vila Isabel; 19 Caxias; 22 Parque Piauí; 23 Araçagy.

O catálogo é criado automaticamente quando o aplicativo inicia e pode ser verificado no painel
de Administração. A operação é idempotente: preserva IDs, nomes anteriores, vínculos dos
cancelamentos e não adiciona outro registro com o mesmo código de loja.
O OCR consegue associar cabeçalhos como "LOJA 10" e rodapés "LJ:0010" à loja 10.

## Backup criptografado existente
Administração > Backup criptografado e restauração. Exporta lojas, cancelamentos ativos
e identidade visual em arquivo .ccel protegido por AES-256-GCM e senha de pelo menos 10
caracteres. A importação solicita senha e confirmação, preservando registros existentes.
**Importante: neste formato não entram fotos, usuários nem senhas de login.**
Guarde as imagens em lugar seguro separadamente, faça cópias do arquivo .ccel e teste a
restauração antes de desinstalar. Não restaura arquivos de fotografias.

## Atualizar dentro do aplicativo
Administração > Atualizações > Verificar atualizações.
O aplicativo consulta o GitHub Releases oficial, compara a versão, baixa o APK por HTTPS
e verifica o digest SHA-256, o identificador do pacote, a versão e a assinatura Android.
Em seguida, abre a instalação com consentimento explícito do usuário.
O Android pode exigir permissão para instalar aplicativos desta origem.

Não há atualização silenciosa. Não se deve desinstalar o app antigo ao atualizar.
A atualização direta só funciona se ambos os APKs usarem o mesmo applicationId e
**a mesma chave de assinatura**. Um APK de testes (debug) geralmente NÃO é atualizável
por uma release assinada com chave de produção. Faça backup antes de migrar.

## Publicar o próximo APK no GitHub
O workflow .github/workflows/release-android.yml produz um APK release assinado quando
uma tag vX.Y.Z é publicada e a versão em app/build.gradle.kts é X.Y.Z.
Antes do primeiro release, criar e guardar permanentemente uma chave Android e configurar
os segredos do repositório:
- ELITE_KEYSTORE_BASE64: keystore em base64
- ELITE_KEYSTORE_PASSWORD
- ELITE_KEY_ALIAS
- ELITE_KEY_PASSWORD

Nunca publicar a chave no repositório ou no chat. Perder a chave impede atualizações
diretas de uma instalação assinada anteriormente. Verifique também políticas de
distribuição de APKs fora da Play Store.

A publicação no GitHub deve ocorrer apenas depois de aprovação de testes e autorização.
A cada versão, faça merge de código aprovado e publique uma nova tag assinada na
branch main. A página de Releases deverá ter **um** APK oficial nomeado com "Elite"
e sem "debug". O GitHub fornece digest SHA-256 de assets por API para verificação.
