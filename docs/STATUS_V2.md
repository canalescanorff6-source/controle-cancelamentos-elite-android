# STATUS V2 — CONTROLE DE CANCELAMENTOS ELITE

Data: 10/10/2026

## Implementado no código nesta iteração

- Paleta e ícone nativos próprios; marca, assinatura e crédito da idealização editáveis localmente.
- Sair da sessão acessível a administrador e operador.
- Bloqueio transacional de registros potencialmente duplicados.
- Novos tratamentos de OCR para total a pagar, valor total e exclusão de subtotal/tributos.
- Exigência de conferência manual após leitura de foto.
- Validação explícita de filtros; bloqueio de exportação quando filtros inválidos.
- Limite de imagem 12 MB, miniatura amostrada e limpeza do anexo em falhas de gravação.
- Paginação visual de histórico (50 por lote).
- PDF com título e assinatura personalizados.
- Exportação e restauração de backup AES-256-GCM de registros ativos, lojas e marca.

## Verificações realmente realizadas sem SDK Android

- PASSOU: testes JVM de 4 exemplos de reconhecimento de valores com recibo fiscal, incluindo exclusão de tributos e subtotal.
- PASSOU: AES-256-GCM, cifragem e decifragem com senha correta e falha com senha diferente.
- PASSOU: análise sintática Kotlin do serializador/validador JSON com classes de suporte simuladas.
- INSPECIONADO: estrutura do projeto Android, manifesto, workflow CI e documentação.

## Ainda NÃO validado

- Compilação Gradle Android (falta SDK/repositórios de dependências neste ambiente).
- Testes unitários oficiais no Android Gradle (precisam de CI).
- Fluxos Room/SQLite reais, Camera ActivityResult, armazenamento e OCR ML Kit em dispositivo.
- Layout responsivo em tamanhos de telas variados, desempenho em aparelhos modestos.
- PDF e impressão no Android; leitura/gravação de documentos por permissões do sistema.
- Criptografia de backup rodada diretamente no Android.
- Segurança em ambientes adversariais e conformidade jurídica/contábil.

## Limitações conhecidas

- Sem servidor: dados e usuários somente neste dispositivo; convites não sincronizam aparelhos.
- Fotos, contas, senhas e registros excluídos não são transferidos no backup.
- Senha do backup perdida é irrecuperável.
- SQLite do dispositivo não está criptografado; bloqueio de tela recomendado.
- Nome exibido no launcher é fixo na compilação; personalização dentro do app é dinâmica.
- Sistema não substitui escrituração fiscal oficial ou validação jurídica.

## Próximo critério de aceite

Actions GREEN (testDebugUnitTest/assembleDebug), APK instalado, execução manual de todas as etapas do TESTE_DE_ACEITE.md, correção de falhas identificadas e geração de APK assinado para distribuição.

## Nota para atualizações futuras

A compilação `app-debug.apk` no GitHub Actions é de teste. A chave de assinatura de debug de executores temporários pode variar entre builds; um Android pode recusar instalar a nova versão por cima da anterior. Faça backup cifrado antes de instalar/reinstalar versões de teste. Para distribuição estável, configurar assinatura de release persistente protegida por segredos no GitHub.
