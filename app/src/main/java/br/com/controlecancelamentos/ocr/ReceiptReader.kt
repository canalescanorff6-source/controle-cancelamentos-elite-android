package br.com.controlecancelamentos.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ReceiptReader {
    private const val MAX_OCR_SIDE = 2800

    suspend fun recognize(context: Context, uri: Uri): ReceiptCandidate {
        ReceiptImageValidator.validate(context, uri)
        val bitmap = withContext(Dispatchers.IO) { loadOrientedBitmap(context, uri) }
        val scanner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val original = ReceiptParser.parse(read(scanner, bitmap))
            if (original.recognizedFields() == 6) return original
            val enhanced = withContext(Dispatchers.Default) { enhance(bitmap) }
            val second = try { ReceiptParser.parse(read(scanner, enhanced)) }
                finally { enhanced.recycle() }
            val result = ReceiptCandidate(
                coupon = original.coupon ?: second.coupon,
                amountCents = original.amountCents ?: second.amountCents,
                date = original.date ?: second.date,
                time = original.time ?: second.time,
                pdv = original.pdv ?: second.pdv,
                ccf = original.ccf ?: second.ccf
            )
            if (result.recognizedFields() == 0) {
                throw IllegalArgumentException("Nenhum campo legível: aproxime o cupom e evite reflexos ou sombras.")
            }
            val cautions = buildList {
                addAll(original.notes)
                if (result.coupon != null && original.coupon == null) add("COO identificado na segunda leitura")
                if (result.amountCents != null && original.amountCents == null) add("TOTAL identificado na segunda leitura")
                if (original.coupon != null && second.coupon != null && original.coupon != second.coupon) {
                    add("COO diferente entre as leituras; revise no papel")
                }
                if (original.amountCents != null && second.amountCents != null && original.amountCents != second.amountCents) {
                    add("TOTAL divergente entre leituras; confira o valor")
                }
                add("Revise sempre os dados sugeridos antes de salvar")
            }
            return result.copy(notes = cautions.distinct())
        } finally {
            bitmap.recycle()
            scanner.close()
        }
    }

    private suspend fun read(scanner: TextRecognizer, bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val header = resolver.openInputStream(uri) ?: throw InvalidReceiptImageException("Imagem indisponível.")
        header.use { BitmapFactory.decodeStream(it, null, dimensions) }
        if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0) {
            throw InvalidReceiptImageException("Não foi possível identificar as dimensões da imagem.")
        }
        var sample = 1
        while (dimensions.outWidth / sample > MAX_OCR_SIDE || dimensions.outHeight / sample > MAX_OCR_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw InvalidReceiptImageException("Não foi possível abrir a foto para leitura.")
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        if (matrix.isIdentity) return decoded
        return try { Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) }
            finally { decoded.recycle() }
    }

    private fun enhance(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val strength = 1.6f
        val offset = -255f * (strength - 1f) / 2f + 14f
        gray.postConcat(ColorMatrix(floatArrayOf(
            strength, 0f, 0f, 0f, offset,
            0f, strength, 0f, 0f, offset,
            0f, 0f, strength, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )))
        Canvas(output).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(gray)
        })
        return output
    }
}
