> **Fonte V3 em desenvolvimento, sem APK validado.** Consulte [`docs/STATUS_V3.md`](docs/STATUS_V3.md) antes de distribuir.

# Controle de Cancelamentos Elite — Android nativo (V3 / pré-build)

Aplicativo **Android nativo (Kotlin + Jetpack Compose)** para registrar e analisar cancelamentos de cupons fiscais. **Não é WebView, site encapsulado, PWA ou aplicativo web.**

## Evolução V3

- Tema premium claro, escuro e automático.
- Confirmação antes de descartar cadastro.
- Datas validadas estritamente, OCR protegido contra imagens excessivas.
- Evolução mensal do painel e backup com confirmação antes da restauração.
- Workflow para testes + APK debug condicionado a compilação bem-sucedida.
- Consulte [`docs/COMO_GERAR_APK_GITHUB.md`](docs/COMO_GERAR_APK_GITHUB.md) para publicar e obter o APK.

## Personalização e melhorias da V2

- Identidade própria em grafite-azulado, safira e dourado, com novo ícone nativo.
- **Nome do app, assinatura de personalização e crédito de idealização editáveis** em Administração > Identidade. A etiqueta fixa no launcher continua "Cancelamentos Elite" até recompilar o APK.
- PDF com nome/assinatura personalizáveis e logomarca visual coerente.
- Saída de conta no topo, incluindo operadores.
- Bloqueio transacional de cupons duplicados por número + data + loja + PDV (um novo registro com a mesma chave deverá ser conferido).
- Revisão explícita após OCR, validação de períodos e valores dos filtros, imagens de pré-visualização reduzidas e limite de 12 MB por anexo.
- Histórico exibe registros em lotes de 50 para evitar carregar tudo na tela.
- **Backup local com senha (AES-256-GCM)**: lojas, registros ativos e identidade visual; restauração acrescenta os registros ausentes sem apagar os existentes. Não inclui fotos, credenciais ou registros excluídos.
- Auditoria de importação de backups e testes adicionais do OCR e da criptografia.

Esses itens estão **implementados no código-fonte, não homologados em um APK físico**. A compilação no SDK e a validação no celular permanecem obrigatórias.

## Funcionalidades já implementadas no código

- Configuração do primeiro administrador, login local com senha protegida por PBKDF2-HMAC-SHA256 (salt individual) e sessão persistente.
- Cadastro de usuários operadores no **mesmo aparelho**, mediante código de convite, expiração de 7 dias e controle de conta ativa.
- Cadastro e ativação/desativação de lojas.
- Registro completo: data, hora, valor exato em centavos, cupom, motivo e detalhe, operador, fiscal, gerente, Prevenção de Perdas, loja, PDV, CCF, observações.
- Foto pela câmera e arquivo da galeria; OCR nativo no Android com ML Kit Latin Text Recognition **modelo integrado ao aplicativo**. Preenchimento sugerido com confirmação antes do salvamento.
- Banco de dados Room persistente no aparelho (SQLite), edição, exclusão lógica, rastreabilidade de criação/edição/exclusão.
- Histórico pesquisável, filtros combinados por período, loja, operador, motivo e valor.
- Dashboard: total, quantidade, média, maior cupom e rankings por operador, motivo e loja.
- Relatórios A4 PDF, exportação CSV com UTF-8 (BOM) e impressão pelo Android Print Framework.
- Interface mobile premium em Jetpack Compose e Android 8.0+ (`minSdk=26`).

## Limitações atuais importantes

O projeto é uma **primeira versão nativa local-first**. Não há servidor contratado/configurado e, por isso:

1. Os registros, usuários, lojas e imagens **não sincronizam entre aparelhos**.
2. Convites só criam contas no banco **deste mesmo aparelho**; compartilhar um código não configura acesso remoto.
3. Não há redefinição remota de senha por e-mail; a recuperação administrativa em múltiplos aparelhos exigirá backend/autenticação remota.
4. Não há backup na nuvem nem criptografia transparente do SQLite. Há **exportação manual de backup criptografado AES-GCM**, sem as imagens e senhas de login. Se esquecer a senha do arquivo, não será possível restaurá-lo. O banco e fotos ainda são removidos na desinstalação.
5. A qualidade do OCR depende do modelo e da legibilidade da foto; o usuário sempre precisa revisar. O parser identifica padrões comuns, não todos os modelos de cupom.
6. O código **ainda precisa ser compilado e testado no Android SDK e em dispositivo físico**; a entrega deste repositório não é garantia de funcionamento até passar por esses testes.

Uma versão corporativa multiaparelho necessita API/backend hospedado, isolamento de organizações/lojas, políticas de acesso no servidor, backup e fluxo de convite remoto. Nunca exponha informações fiscais em armazenamento público ou em repositórios GitHub.

## Compilação (GitHub Actions)

1. Crie um repositório GitHub e envie **o conteúdo** da pasta (mantendo `.github/workflows/android.yml`).
2. Vá a **Actions > Android | Testes e APK V3** e execute `Run workflow` (ou faça `push` na `main`).
3. O fluxo executará testes e compilará `app-debug.apk`. **O APK só é disponibilizado se o workflow finalizar sem erros.**
4. Abra o workflow concluído e baixe o artefato `Controle-Cancelamentos-Elite-V3-debug`.
5. Transfira o APK ao seu Android e instale-o. O aparelho poderá pedir permissão para instalar aplicativos dessa fonte.

APK de depuração é para testes. Para distribuição formal é necessário configurar uma chave de assinatura de release segura, versionamento e testes adicionais. Não publique um APK de depuração na Play Store.

## Compilar no Android Studio

- Requer Android Studio com JDK 17, SDK Android 36 e acesso aos repositórios `google()` e `mavenCentral()`.
- Use Gradle 8.13 (o workflow instala a versão automaticamente).
- Abra a pasta do projeto, sincronize Gradle e escolha `app > Run`.
- Dependências: AGP 8.13.2, Kotlin 2.2.20, Compose BOM 2026.02.00, Room 2.8.4 e Google ML Kit 16.0.1.
- Este arquivo não inclui o arquivo binário do Gradle Wrapper. O Android Studio pode usar o Gradle local; ou execute `gradle wrapper --gradle-version 8.13` após instalar o Gradle para gerar o wrapper padrão.

## Fluxo inicial

Na primeira abertura, crie o administrador local (nome, e-mail e senha de 8+ caracteres). Depois vá a **Gestão** e adicione suas lojas. Em **Registrar**, capture uma foto ou digite os dados. Confira o OCR, preencha os responsáveis e salve. Em **Histórico**, filtre, exporte ou imprima; em **Resumo**, consulte os totais.

## Segurança e dados

- Nunca insira dados reais de clientes ou cupons em commits Git.
- O aplicativo não pede permissão de internet nem transmite dados para servidores: as fotos OCR são processadas no aparelho.
- O formato de valores internos é `Long` em centavos para evitar arredondamentos em dinheiro.
- Alterações são registradas em trilha local de auditoria, porém o administrador do dispositivo pode apagar o app e seus dados.
- Sem sincronização, **não substitui** um sistema fiscal oficial ou uma solução de auditoria empresarial com retenção obrigatória.

## Estrutura

`app/src/main/java/br/com/controlecancelamentos/` — telas Compose, ViewModel, Room, OCR, relatórios e autenticação.

`.github/workflows/android.yml` — testes unitários + build debug no GitHub Actions; o APK só é publicado como artefato de uma execução bem-sucedida.

## Autoria e créditos

Este repositório é uma implementação Android independente com base na especificação funcional informada. Ideia original e desenvolvimento/personalização podem ser creditados separadamente no menu Administração. Esse crédito não comprova registro de marca, patente ou transferência de direitos sobre o aplicativo original de terceiros.

## Importação do backup

1. Faça o cadastro do administrador no aparelho de destino.
2. Abra **Gestão > Backup seguro / troca de aparelho**.
3. Digite a mesma senha que foi usada para criar o arquivo `.ccel`.
4. Toque em **Restaurar**, escolha o arquivo e confira a mensagem de conclusão.
5. Reconfigure contas locais e adicione fotos manualmente, se necessário.
6. Se o arquivo já tiver registros no banco, a importação ignora IDs existentes e chaves duplicadas; não substitui dados atuais.

**Atenção:** arquivos de backup e exportações fiscais não devem ser enviados a repositórios públicos ou chats sem autorização da empresa titular dos dados.
