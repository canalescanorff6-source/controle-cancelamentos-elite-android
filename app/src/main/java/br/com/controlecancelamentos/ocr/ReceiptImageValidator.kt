package br.com.controlecancelamentos.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InvalidReceiptImageException(message: String) : IllegalArgumentException(message)

/** Validação preventiva: nunca carregar fotografias gigantes sem verificar os limites. */
object ReceiptImageValidator {
    private const val MAX_BYTES = 12L * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L
    private const val MAX_SIDE = 5500

    suspend fun validate(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var total = 0L
        val source = context.contentResolver
        source.openInputStream(uri)?.use { input ->
            // Contar bytes com limite: ContentProvider pode não informar o tamanho real.
            val data = ByteArray(8192)
            while (true) {
                val count = input.read(data)
                if (count < 0) break
                total += count
                if (total > MAX_BYTES) throw InvalidReceiptImageException("Imagem maior que 12 MB. Selecione uma imagem menor.")
            }
        } ?: throw InvalidReceiptImageException("Imagem não disponível. Selecione novamente.")
        if (total <= 0) throw InvalidReceiptImageException("Arquivo de imagem vazio.")
        // BitmapFactory retorna null quando inJustDecodeBounds=true; verificar dimensões em Options.
        val dimensionsStream = source.openInputStream(uri)
            ?: throw InvalidReceiptImageException("Imagem não disponível para verificação.")
        dimensionsStream.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw InvalidReceiptImageException("Formato de imagem não reconhecido.")
        if (options.outWidth > MAX_SIDE || options.outHeight > MAX_SIDE ||
            options.outWidth.toLong() * options.outHeight.toLong() > MAX_PIXELS) {
            throw InvalidReceiptImageException("Imagem muito grande. Reduza a resolução e tente novamente.")
        }
    }
}
