# Evolução planejada — V1 nativa para uso corporativo

A V1 é nativa e offline. O objetivo para uma V2 multiaparelho é manter todas as telas e dados locais, acrescentando **sincronização com servidor e autenticação centralizada**, sem migrar para WebView.

Requisitos para implantação corporativa:

1. API HTTPS autenticada, perfis e organizações com identificação imutável.
2. Permissão de acesso validada **no servidor**, por organização e loja; o papel do usuário não deve ser confiado ao aplicativo cliente.
3. Mecanismo de sincronização offline/online com conflitos resolvidos, tratamento de exclusão lógica e idempotência.
4. Convites via tokens de uso único gerados pelo backend, com expiração e revogação.
5. Fotos de cupons em armazenamento privado com políticas de acesso e retenção.
6. Trilha de auditoria imutável e backups automatizados.
7. Recuperação segura de senhas e controle de sessão/revogação.
8. Exportação de dados completa e política de exclusão/anônimização conforme LGPD e obrigações fiscais aplicáveis.
9. Observabilidade de erros, crash reporting sem capturar cupons e testes E2E.

Não incluir credenciais, URL privada de backend, dados fiscais reais nem assinaturas de APK no repositório público.


## V2 — edição personalizada (10/10/2026)

- Paleta exclusiva safira/grafite/dourado, ícone e marca editável; crédito de idealização e desenvolvimento separados.
- Sair da conta acessível para operadores e administradores.
- Duplicidades: verificação transacional para cupom + data + loja + PDV.
- OCR: padrões adicionais de valor a pagar e confirmação explícita antes de salvar.
- Histórico: validação e limite inicial de 50 entradas visíveis, filtros mantidos na geração dos relatórios.
- Fotos: tamanho máximo, pré-visualização amostrada e limpeza do novo anexo se o registro falhar.
- Backup: exportação/restauração cifrada com AES-256-GCM, excluindo imagens e credenciais.
- Verificação local parcial: compilação e smoke tests JVM do módulo de reconhecimento textual e backup criptografado, sem SDK Android.
- Pendente: Android Gradle build, testes de instrumentação, instalação em aparelho, política de sincronização multiaparelho.


## V3 — premium funcional / pré-build (10/10/2026)

Consulte [STATUS_V3.md](STATUS_V3.md) para mudanças no código, testes locais, limitações e validação pendente em Android. Não houve publicação de APK, commit ou push.
