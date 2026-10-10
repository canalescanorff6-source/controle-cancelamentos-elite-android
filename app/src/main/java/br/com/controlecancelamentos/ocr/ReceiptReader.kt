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
            val raw = suspendCancellableCoroutine<String> { continuation ->
                scanner.process(image)
                    .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text) }
                    .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            }
            if (raw.isBlank()) throw IllegalArgumentException("Nenhum texto legível encontrado na foto.")
            ReceiptParser.parse(raw)
        } finally {
            scanner.close()
        }
    }
}
