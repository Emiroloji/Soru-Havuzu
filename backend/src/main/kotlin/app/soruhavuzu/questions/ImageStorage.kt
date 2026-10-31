package app.soruhavuzu.questions

import app.soruhavuzu.ValidationException
import app.soruhavuzu.validate
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

data class StoredImage(val fileName: String, val perceptualHash: String)

/**
 * Soru görsellerini sunucu diskinde (Docker volume) saklar.
 * Gelen görsel çözülüp yeniden JPEG'e kodlanır: EXIF (konum, cihaz bilgisi) ve
 * görsel içine gömülü başka her şey bu sırada atılır.
 */
class ImageStorage(private val dir: Path) {

    init {
        Files.createDirectories(dir)
    }

    fun save(bytes: ByteArray): StoredImage {
        validate(bytes.size <= MAX_BYTES) { "Görsel en fazla ${MAX_BYTES / 1_000_000} MB olabilir" }

        val image = decodeSafely(bytes)
        val scaled = downscale(image, MAX_SIDE)
        val fileName = "q_${UUID.randomUUID()}.jpg"
        Files.write(dir.resolve(fileName), encodeJpeg(scaled))
        return StoredImage(fileName, averageHash(scaled))
    }

    fun delete(fileName: String) {
        resolve(fileName)?.deleteIfExists()
    }

    /** Dosya adı bizim ürettiğimiz biçimde değilse null döner (dizin dışına çıkma denemelerine karşı). */
    fun resolve(fileName: String): Path? {
        if (!FILE_NAME.matches(fileName)) return null
        return dir.resolve(fileName).takeIf { it.exists() }
    }

    private fun decodeSafely(bytes: ByteArray): BufferedImage {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = input?.let { ImageIO.getImageReaders(it).asSequence().firstOrNull() }
                ?: throw ValidationException("Görsel okunamadı; JPEG veya PNG yükle")
            try {
                reader.input = input
                // Piksel verisini açmadan önce boyutu kontrol et: küçük dosyada dev görsel (bellek bombası) olmasın
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                validate(width.toLong() * height <= MAX_PIXELS) { "Görsel çözünürlüğü çok yüksek" }
                return reader.read(0)
            } catch (e: ValidationException) {
                throw e
            } catch (e: Exception) {
                throw ValidationException("Görsel okunamadı; JPEG veya PNG yükle")
            } finally {
                reader.dispose()
            }
        }
    }

    private fun downscale(image: BufferedImage, maxSide: Int): BufferedImage {
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(image.width, image.height))
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        // Saydam PNG'ler JPEG'e beyaz zemin üzerine çizilir
        return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics()
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, width, height)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(image, 0, 0, width, height, null)
            g.dispose()
        }
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.85f
            }
            writer.write(null, IIOImage(image, null, null), params)
            writer.dispose()
        }
        return out.toByteArray()
    }

    /** 8x8 ortalama parlaklık özeti (64 bit, hex); benzer görselleri bulmak için saklanır. */
    private fun averageHash(image: BufferedImage): String {
        val tiny = downscaleExact(image, 8, 8)
        val lums = IntArray(64) { i ->
            val rgb = tiny.getRGB(i % 8, i / 8)
            ((rgb shr 16 and 0xFF) * 3 + (rgb shr 8 and 0xFF) * 4 + (rgb and 0xFF)) / 8
        }
        val avg = lums.average()
        val bits = lums.fold(0L) { acc, lum -> (acc shl 1) or (if (lum >= avg) 1L else 0L) }
        return "%016x".format(bits)
    }

    private fun downscaleExact(image: BufferedImage, width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(image, 0, 0, width, height, null)
            g.dispose()
        }

    companion object {
        const val MAX_BYTES = 5_000_000
        const val MAX_SIDE = 1600
        private const val MAX_PIXELS = 40_000_000L
        private val FILE_NAME = Regex("^q_[0-9a-f-]{36}\\.jpg$")
    }
}
