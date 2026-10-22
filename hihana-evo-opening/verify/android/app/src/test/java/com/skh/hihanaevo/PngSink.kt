package com.skh.hihanaevo

import android.graphics.Bitmap
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Copies a Bitmap's pixels on the calling (Robolectric main) thread, then PNG-encodes them on a
 * small pool of plain JVM threads (javax.imageio, no Android code off the main thread), so encoding
 * overlaps with rendering the next frame.
 */
internal class PngSink(threads: Int = 3, private val maxInFlight: Int = 24) : AutoCloseable {
    private val pool: ExecutorService = Executors.newFixedThreadPool(threads) { r ->
        Thread(r, "png-encoder").apply { isDaemon = true }
    }
    private val inFlight = ArrayDeque<Future<*>>()
    var written = 0
        private set

    fun submit(bitmap: Bitmap, file: File) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h) // unpremultiplied ARGB
        submit(px, w, h, file)
    }

    fun submit(argb: IntArray, w: Int, h: Int, file: File) {
        while (inFlight.size >= maxInFlight) inFlight.removeFirst().get()
        inFlight.addLast(pool.submit { writePng(argb, w, h, file) })
        written++
    }

    override fun close() {
        while (inFlight.isNotEmpty()) inFlight.removeFirst().get()
        pool.shutdown()
    }

    companion object {
        fun writePng(argb: IntArray, w: Int, h: Int, file: File) {
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            System.arraycopy(argb, 0, (img.raster.dataBuffer as DataBufferInt).data, 0, argb.size)
            val writer = ImageIO.getImageWritersByFormatName("png").next()
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f // low deflate level: faster, slightly bigger files
            }
            file.parentFile?.mkdirs()
            file.delete() // ImageIO's file stream does not truncate an existing file
            ImageIO.createImageOutputStream(file).use { out ->
                writer.output = out
                writer.write(null, IIOImage(img, null, null), param)
            }
            writer.dispose()
        }
    }
}

internal fun sysProp(name: String): String? = System.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
