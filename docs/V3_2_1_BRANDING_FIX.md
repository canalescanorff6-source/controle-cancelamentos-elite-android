# Controle Elite V3.2.1 — consistência visual

Correção autorizada após relato de que o APK de testes parecia usar identidade antiga.

- Fonte canônica: `drawable-nodpi/elite_brand.webp` (mesma arte premium da V3.1).
- Launcher adaptativo normal e redondo: `elite_brand_safe.xml`, com margem para máscara do Android.
- Splash nativa: mesma arte canônica. Login, cabeçalho e loading: `elite_brand`.
- `tools/check_branding.py`: evita regressões de nomes e recursos.
- Aplicação de testes independente: `br.com.controlecancelamentos.teste.visual`.
- Rótulo inequívoco na gaveta: **Controle Elite Visual (Teste)**.
- Alterações apenas visuais; OCR/câmera/galeria/arquivos e banco Room intactos.

AVISO: o novo ID de testes não acessa os dados de outra instalação.
Não desinstale as versões anteriores com dados. Assinatura debug não
garante atualização entre máquinas; assinatura permanente está em preparo.

Estados: IMPLEMENTADO em código; build e dispositivo físico dependem de verificação posterior.
