package com.example.jewelrymanagement.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.ImageView
import com.example.jewelrymanagement.R
import java.io.File
import java.io.FileOutputStream

/**
 * A product's imageName is either a drawable resource name (bundled images) or
 * "file:<name>" for a photo picked from the phone and copied into app storage.
 */
object ProductImageUtils {

    private const val FILE_PREFIX = "file:"
    private const val DIR_NAME = "product_images"
    private const val MAX_SIZE = 1024

    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun isLocalFile(imageName: String?) = imageName?.startsWith(FILE_PREFIX) == true

    fun load(imageView: ImageView, imageName: String?) {
        val context = imageView.context
        if (isLocalFile(imageName)) {
            val file = fileFor(context, imageName!!)
            val bitmap = cache.get(file.path) ?: BitmapFactory.decodeFile(file.path)?.also { cache.put(file.path, it) }
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return
            }
        } else if (!imageName.isNullOrBlank()) {
            val resId = context.resources.getIdentifier(imageName, "drawable", context.packageName)
            if (resId != 0) {
                imageView.setImageResource(resId)
                return
            }
        }
        imageView.setImageResource(R.drawable.img_jewelry_placeholder)
    }

    /** Downscales the picked image, stores it as JPEG in app storage and returns its imageName. */
    fun saveFromUri(context: Context, uri: Uri): String? = try {
        val bitmap = decodeScaled(context, uri)
        val name = "prod_${System.currentTimeMillis()}.jpg"
        val file = File(dir(context), name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        FILE_PREFIX + name
    } catch (e: Exception) {
        null
    }

    fun delete(context: Context, imageName: String?) {
        if (!isLocalFile(imageName)) return
        val file = fileFor(context, imageName!!)
        cache.remove(file.path)
        file.delete()
    }

    private fun dir(context: Context) = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun fileFor(context: Context, imageName: String) =
        File(dir(context), imageName.removePrefix(FILE_PREFIX))

    private fun decodeScaled(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = MAX_SIZE.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("Cannot decode image")

        // ImageDecoder applies EXIF rotation itself; BitmapFactory does not.
        val degrees = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        bitmap.recycle()
        return rotated
    }
}
