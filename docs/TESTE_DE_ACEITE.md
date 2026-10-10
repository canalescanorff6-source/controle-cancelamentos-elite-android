# Plano de aceite — Controle de Cancelamentos Android

## Etapa A — compilação

- [ ] Workflow `Android - CI / APK` executa `testDebugUnitTest` sem falhas.
- [ ] Workflow executa `assembleDebug` sem falhas.
- [ ] Artefato `app-debug.apk` é produzido.
- [ ] APK instala e abre em Android 8+ e Android 14/15/16.

## Etapa B — autenticação local

- [ ] Instalação limpa pede cadastro do administrador e não possui credenciais padrão.
- [ ] Senha com menos de 8 caracteres é rejeitada.
- [ ] Login incorreto é rejeitado e saída exige login.
- [ ] Login correto recupera dados após fechar/reabrir o aplicativo.
- [ ] Admin cria convite, sai, novo operador aceita no **mesmo aparelho**.
- [ ] Operador não vê seção Gestão; administrador pode desativar conta de operador.

## Etapa C — cupons

- [ ] Cadastro manual persiste com todos os campos exigidos.
- [ ] Valor `R$ 1.234,56` resulta em 123456 centavos.
- [ ] Registro não aceita data inválida ou valor com três casas decimais.
- [ ] Captura câmera retorna imagem e preenche campos reconhecidos.
- [ ] Galeria retorna imagem e o OCR falho não impede digitação manual.
- [ ] Dados de foto são revisáveis antes de salvar.
- [ ] Edição atualiza campos e permanece após reinicialização.
- [ ] Exclusão lógica retira registro da lista e dos totais.
- [ ] Operador não edita registros criados por outro usuário.

## Etapa D — relatório e indicadores

- [ ] Busca por cupom ou operador funciona.
- [ ] Filtro por período, loja, motivo e valores funciona em conjunto.
- [ ] Resumo total, média, maior cupom e rankings batem com registros.
- [ ] Exportação CSV abre corretamente com números e acentuação.
- [ ] PDF A4 mantém cabeçalho e não corta registros na virada de página.
- [ ] Imprimir abre o diálogo de impressão do Android.
- [ ] Anexo salvo abre pelo visualizador de imagens instalado.

## Pendências de produção

- [ ] Infraestrutura remota selecionada e provisionada, se o uso for multiaparelho.
- [ ] Autenticação centralizada, separação por loja/organização, backups e recuperação de senha.
- [ ] Segurança de dados avaliada à luz de política interna e LGPD.
- [ ] Testes instrumentados e revisão de acessibilidade.
- [ ] Assinatura de release configurada com keystore privado (fora do GitHub).

**Nota:** esta lista é uma matriz de testes a executar, não uma afirmação de que todos já foram realizados.


## Verificações V2 adicionais (a executar no Android)

- [ ] Nome, assinatura e crédito de idealização persistem após fechar/abrir o app.
- [ ] Operador consegue sair da conta pelo botão no topo.
- [ ] Registro idêntico (cupom+data+loja+PDV) é bloqueado com mensagem clara.
- [ ] Fotos acima de 12 MB são recusadas sem salvar registro/anexo parcial.
- [ ] Foto de resolução alta não encerra a tela de cadastro durante a pré-visualização.
- [ ] OCR exige marcar "Conferi no cupom original" antes de salvar.
- [ ] Datas ou valores inválidos nos filtros impedem a geração de relatórios errados.
- [ ] Histórico com 200 cupons carrega mais 50 por vez, sem travar.
- [ ] Exportar backup com senha de 10+ caracteres, abrir em outro aparelho e restaurar.
- [ ] Senha errada falha sem alterar os dados no banco.
- [ ] Restaurar o mesmo backup duas vezes não duplica registros.
- [ ] Após importação, usuário e fotos não são restaurados (comportamento documentado).
- [ ] PDF mostra marca e assinatura configuradas.


## Novos testes V3 (pendentes no Android)

- [ ] Tema automático acompanha claro/escuro do sistema; escolhas manuais persistem após reiniciar.
- [ ] Campos e textos têm contraste legível no modo escuro.
- [ ] A mudança de aba com cadastro alterado pede confirmação; cancelar conserva os dados.
- [ ] A saída da conta com cadastro alterado pede confirmação.
- [ ] Cupom com data 31/02/2026 não salva nem é normalizado silenciosamente.
- [ ] Recusa OCR maior que 12 MB, 16 MP ou lado maior que 5.500 px sem travar.
- [ ] OCR falho (texto ilegível) ainda permite digitação e foto válida; imagem inválida não é anexada.
- [ ] Arquivo de câmera cancelada é removido do cache; foto registrada abre normalmente.
- [ ] Gráfico mensal bate com os filtros e com o total dos registros por mês.
- [ ] Backup válido exibe prévia sem importar; cancelar não altera banco; confirmar importa e registra auditoria.
- [ ] PDF corta textos longos dentro da largura e não extrapola margens.
