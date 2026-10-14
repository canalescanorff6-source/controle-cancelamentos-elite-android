package br.com.controlecancelamentos.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ReceiptReader {
    suspend fun recognize(context: Context, uri: Uri): ReceiptCandidate {
        ReceiptImageValidator.validate(context, uri)
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val scanner = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            if (result.text.isBlank()) throw IllegalArgumentException("Nenhum texto legível encontrado na foto.")
            val segments = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { box ->
                    ReceiptSegment(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
            ReceiptSpatialMatcher.augment(ReceiptParser.parse(result.text), segments)
        } finally {
            scanner.close()
        }
    }
}
