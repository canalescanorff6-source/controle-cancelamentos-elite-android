# Controle de Cancelamentos Elite — V3 (código-fonte de desenvolvimento)

**Data:** 10/10/2026. **Build Android:** PENDENTE. **Status:** IMPLEMENTADO EM CÓDIGO; **não afirmar GREEN ou APK validado**.

## Diagnóstico da V2 antes das alterações

- A interface usava branco e texto escuro fixos em várias superfícies, sem tema escuro efetivo.
- Havia risco de descartar informações digitadas ao mudar de aba ou sair da sessão sem aviso.
- Datas no formato brasileiro usavam `DateTimeFormatter` em modo SMART, capaz de transformar datas inválidas em datas válidas silenciosamente.
- O OCR criava a imagem completa sem limitar previamente suas dimensões; fotos grandes poderiam elevar o consumo de RAM.
- A restauração de backup era aplicada imediatamente após escolher o arquivo, sem etapa de conferência.
- PDF truncava textos por quantidade de caracteres, o que não garante evitar extrapolação das margens.

## Melhorias implementadas nesta V3

1. **Identidade visual:** tema premium claro/escuro/automático, com seleção persistente no mesmo dispositivo; cores e superfícies integradas ao Material 3, sem modificar o banco Room.
2. **Proteção dos formulários:** confirmação ao sair do cadastro/edição com mudanças não salvas; confirma também antes de sair da conta; navegação para a aba atual preserva campos digitados.
3. **Integridade de dados:** datas brasileiras estritas e limites de campos de formulário harmonizados com backup.
4. **OCR mais seguro:** validação de foto antes de carregar em ML Kit; tamanho máximo 12 MB, até 16 megapixels e dimensão máxima 5.500 px. Erro ou foto inválida é explicado ao usuário; falha da leitura ainda permite cadastro manual e foto válida.
5. **Câmera:** limpeza do arquivo temporário após cancelamento ou ao sair do formulário; original é mantido se registrado com sucesso.
6. **Usabilidade mobile:** teclado numérico apropriado para valor, data e horário.
7. **Resumo:** gráfico de barras de valores mensais dos últimos seis meses, respeitando os filtros existentes, sem dependências extras.
8. **Backup:** importação em duas etapas (decriptação/validação, resumo de contagem, confirmação); não modifica dados antes de confirmação; segue com transação e auditoria. Preserve senha segura.
9. **PDF:** truncamento seguro pela largura real em pixels, respeitando a margem da página.
10. **Testes de regressão:** adicionados testes JUnit de data estrita, limites de formulário e adulteração do backup.
11. **Proteção do repositório:** padrões `.gitignore` para backups `.ccel`, bancos locais e exportações fiscais comuns, evitando publicação acidental.

## Validação realmente executada nesta entrega

- Testes isolados locais reproduzíveis com `bash tools/test_core_jvm.sh` (requer `kotlinc` e `java`, sem Android SDK).
- Testes originais JUnit executados por runner JVM com substitutos mínimos de JUnit/Room/Flow: **12 testes passaram** (`bash tools/test_junit_standalone.sh`), incluindo parser, arquivo AES-GCM e novas validações.
- Testes isolados locais do código Kotlin real (`Model.kt`, `ReceiptParser.kt`, `EncryptedArchive.kt`): **14 assertivas passaram** com dublês simples das APIs Room/Flow para permitir execução sem Android SDK. Não são os testes Android oficiais.
- Inspeção estrutural dos arquivos e manifesto; análise de sintaxe parcial de arquivos de interface, sem dependências Android/Compose disponíveis.
- **Não** executados: `gradle testDebugUnitTest`, `assembleDebug`, lint Android, instrumentação, testes em aparelho, leitura real de foto, navegação da Compose UI, relatório PDF Android ou GitHub Actions.

## Compatibilidade e limite de alterações

- Banco de dados Room permanece `version = 1` e com mesmo nome: `cancelamentos-v1.db`.
- `applicationId` permanece `br.com.controlecancelamentos`.
- Backup `.ccel` permanece no formato 2, sem remoção de recursos anteriores.
- `versionCode` passa a 3 e `versionName` a `3.0.0-dev` **apenas para identificar esta fonte não compilada**.
- Não inclui sincronização entre aparelhos, anexos no backup ou criptografia transparente do SQLite.
- Nenhum código foi enviado ao GitHub, nenhum APK foi gerado.

## Aceite obrigatório antes de distribuir

1. Instalar SDK 36, JDK 17, Gradle 8.13 (via workflow) e dependências na CI.
2. Executar `gradle --no-daemon clean testDebugUnitTest assembleDebug` e corrigir falhas antes de marcar GREEN.
3. Instalar APK de debug em aparelho e testar claro/escuro/automático, cadastro com e sem alterações, OCR de várias fotos reais (incluindo imagem rejeitada), anexos e PDF A4.
4. Testar restauração .ccel com arquivo íntegro, senha errada, arquivo adulterado, cancelamento de prévia, confirmação, repetição sem duplicação e recuperação de instalação anterior.
5. Validar banco Room/persistência em atualização V2→V3 (não desinstalar a V2 sem backup); configurar assinatura release persistente quando autorizado.
