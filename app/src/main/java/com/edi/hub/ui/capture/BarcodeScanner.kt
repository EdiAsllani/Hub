package com.edi.hub.ui.capture

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play's code scanner: no camera permission to ask for, no CameraX to hold open, and the
 * scanning UI is somebody else's to maintain. Returns null when the user backs out.
 */
suspend fun scanBarcode(context: Context): String? = suspendCancellableCoroutine { continuation ->
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
        )
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode ->
            if (continuation.isActive) continuation.resume(barcode.rawValue)
        }
        .addOnCanceledListener { if (continuation.isActive) continuation.resume(null) }
        .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
}
