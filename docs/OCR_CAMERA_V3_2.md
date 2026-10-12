# V3.2 — Correções de câmera, galeria, arquivos e OCR

- Foto por câmera externa com URI privada de FileProvider, ClipData e permissões temporárias explícitas.
- Seletor de galeria pelo Photo Picker nativo e seleção independente pelo Android DocumentsUI.
- Erros de abertura passam a ser comunicados na tela, sem fechar o cadastro.
- Leitura ML Kit com imagem reduzida em memória, orientação EXIF e segunda passagem em contraste aumentado para papel térmico desbotado.
- Aceita resolução alta (até 130 MP) respeitando o limite de 12 MB por foto, mesmo limite já utilizado para anexos.
- Reconhece COO como número do cupom, sem confundi-lo com CCF; preserva zeros à esquerda.
- Testes sintéticos baseados nas duas estruturas de cupom fornecidas, sem enviar fotos fiscais reais ao GitHub.

## Proteção dos dados
O APK de CI tem identificador separado `br.com.controlecancelamentos.teste`. Pode ser instalado em paralelo à V3.1 sem exigir desinstalação do aplicativo anterior, cujo APK debug tem assinatura efêmera. O identificador de produção, o Room e as tabelas não foram modificados. 

## Pendências
Testes automatizados Gradle e revisão dos logs no GitHub; verificar câmera, galeria, Arquivos e OCR no aparelho físico antes de tratar a V3.2 como validada no Android. Um APK de atualização sobre o aplicativo anterior requer assinatura de distribuição estável e procedimento seguro de migração de dados.
