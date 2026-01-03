package com.example.genmemo.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageUtils {

    private const val MAX_DIMENSION = 800
    private const val JPEG_QUALITY = 80

    /**
     * Process and save an image from Uri
     * - Rotates based on EXIF
     * - Resizes to max 800x800
     * - Saves as JPEG with 80% quality
     * Returns the saved file path or null on failure
     */
    fun processAndSaveImage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Read EXIF orientation before loading bitmap
            val orientation = getOrientation(context, uri)

            // Decode with sampling to avoid OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val sizeStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(sizeStream, null, options)
            sizeStream?.close()

            // Calculate sample size
            options.inSampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)
            options.inJustDecodeBounds = false

            // Decode the actual bitmap
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            if (bitmap == null) return null

            // Rotate if needed
            val rotatedBitmap = rotateBitmap(bitmap, orientation)

            // Resize to max dimensions
            val resizedBitmap = resizeBitmap(rotatedBitmap, MAX_DIMENSION)

            // Clean up intermediate bitmap
            if (rotatedBitmap != bitmap && rotatedBitmap != resizedBitmap) {
                rotatedBitmap.recycle()
            }
            if (bitmap != rotatedBitmap && bitmap != resizedBitmap) {
                bitmap.recycle()
            }

            // Save to internal storage
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)

            FileOutputStream(file).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            resizedBitmap.recycle()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Delete an image file
     */
    fun deleteImage(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun getOrientation(context: Context, uri: Uri): Int {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use {
                val exif = ExifInterface(it)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val ratio = width.toFloat() / height.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
