# Controle Elite V4.3 — Migração segura e atualização contínua

## Principais decisões
- Identificador oficial: `br.com.controlecancelamentos`.
- Código atual no branch: `versionName=4.3.0`, `versionCode=8`.
- Uma instalação Android só pode ser atualizada sem remover o app se o novo APK
  mantiver o **mesmo applicationId e uma assinatura Android compatível**.
- APKs debug emitidos por jobs diferentes do GitHub Actions normalmente têm
  certificados distintos, a menos que usem deliberadamente uma keystore fixa.
  Portanto, um APK V4.2 debug não é uma base segura de atualização contínua.
- O fluxo de distribuição oficial usa um keystore de release permanente sob
  controle do proprietário. Segredos são inseridos em GitHub Settings, jamais no repo.

## Não desinstale o aplicativo com dados importantes
Se a versão instalada for uma versão de testes assinada com outra chave, será
necessário migrar por exportação/restauração. O arquivo `.ccel` legado é cifrado
mas **não inclui imagens**, somente registros, lojas e identidade. Antes de
desinstalar o app antigo, salve também as fotos dos cupons por um meio seguro.
O novo `.ccf3` inclui fotos, porém só consegue salvar os dados já acessíveis
à instalação em que ele está sendo executado. Ele não extrai os dados de
outro app ou pacote Android.

## Backup com fotos — novo formato .ccf3
- Painel Administração > Backup completo e restauração > Backup + fotos.
- Informa senha de 10+ caracteres e escolhe onde salvar (SAF).
- Arquivo ZIP cifrado via AES-256-GCM e PBKDF2, com header CCF3 e autenticação.
- Armazena metadados de lojas, registros e identidade, e arquivos dos cupons
  vinculados por ID. Não transfere contas de login nem senhas.
- Importação pede senha, valida autenticidade, tamanho e nomes dos anexos,
  mostra prévia de quantidades e exige confirmação manual.
- A restauração adiciona registros não presentes e pode repor anexos ausentes
  de registros já existentes pelo mesmo ID, preservando dados do usuário.
- Limite conservador de 512 MB de fotos por arquivo; se ultrapassado,
  a exportação falha com aviso em vez de omitir fotos silenciosamente.

## Publicação de APK oficial
1. Gerar ou recuperar a keystore permanente de produção em equipamento de
   confiança. Não compartilhar a senha nem o arquivo em conversas.
2. Em Settings > Secrets and variables > Actions, cadastrar:
   `ELITE_KEYSTORE_BASE64`, `ELITE_KEYSTORE_PASSWORD`,
   `ELITE_KEY_ALIAS`, `ELITE_KEY_PASSWORD`.
3. Confirmar testes verdes, fluxo em emulador e migração dos dados.
4. Aprovar merge para `main` e publicar tag `v4.3.0`
   apontando para o commit que contém `versionName=4.3.0`.
5. O workflow publica um APK release assinado em GitHub Releases.
6. Atualizações seguintes deverão reutilizar a mesma chave para sempre.

## Testes necessários antes de produção
- Instalação limpa, primeiro administrador, cadastro, OCR e histórico.
- Criar registros com fotos e conferir o backup .ccf3 em armazenamento externo.
- Restaurar num aparelho de teste e verificar fotos e registros.
- Confirmar certificado APK, packageId e versionCode crescente.
- Publicar release seguinte e executar a atualização dentro do app.
- Testar também a restauração de .ccel legado (sem fotos).

Nenhuma versão é considerada validada no aparelho real até o teste com
um celular Android e arquivos de backup reais.
