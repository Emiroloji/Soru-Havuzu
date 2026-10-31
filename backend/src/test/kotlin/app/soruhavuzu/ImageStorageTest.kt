package app.soruhavuzu

import app.soruhavuzu.questions.ImageStorage
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageStorageTest {

    private val dir = Files.createTempDirectory("img-test")
    private val storage = ImageStorage(dir)

    private fun image(width: Int, height: Int, format: String): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = Color.ORANGE; fillRect(0, 0, width / 2, height); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(img, format, it) }.toByteArray()
    }

    /** JPEG'in SOI işaretinden hemen sonra konum bilgisi taşıyan sahte bir EXIF (APP1) bölümü ekler. */
    private fun withExif(jpeg: ByteArray): ByteArray {
        val payload = "Exif\u0000\u0000GPS-41.0082N-28.9784E".toByteArray()
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte()) +
            ByteBuffer.allocate(2).putShort((payload.size + 2).toShort()).array() + payload
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }

    private fun ByteArray.containsAscii(text: String) = String(this, Charsets.ISO_8859_1).contains(text)

    @Test
    fun `exif ve konum bilgisi kaydedilen dosyada kalmaz`() {
        val input = withExif(image(200, 100, "jpg"))
        assertTrue(input.containsAscii("GPS-41.0082N"), "test girdisi EXIF içermeli")

        val stored = storage.save(input)

        val saved = Files.readAllBytes(dir.resolve(stored.fileName))
        assertFalse(saved.containsAscii("Exif"))
        assertFalse(saved.containsAscii("GPS-41.0082N"))
        assertEquals(16, stored.perceptualHash.length)
    }

    @Test
    fun `buyuk gorsel kucultulur, png jpeg'e cevrilir`() {
        val stored = storage.save(image(3200, 800, "png"))

        val saved = ImageIO.read(dir.resolve(stored.fileName).toFile())
        assertEquals(ImageStorage.MAX_SIDE, saved.width)
        assertEquals(400, saved.height)
        assertTrue(stored.fileName.endsWith(".jpg"))
    }

    @Test
    fun `gorsel olmayan ve asiri buyuk dosyalar reddedilir`() {
        assertFailsWith<ValidationException> { storage.save("bu bir resim değil".toByteArray()) }
        assertFailsWith<ValidationException> { storage.save(ByteArray(ImageStorage.MAX_BYTES + 1)) }
    }

    @Test
    fun `kucuk dosyada dev cozunurluk iddia eden png piksel acilmadan reddedilir`() {
        // Geçerli bir PNG başlığı: 50.000 x 50.000 piksel (açılsa ~10 GB bellek ister)
        fun chunk(type: String, data: ByteArray): ByteArray {
            val typeBytes = type.toByteArray()
            val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
            return ByteBuffer.allocate(4).putInt(data.size).array() + typeBytes + data + ByteBuffer.allocate(4).putInt(crc).array()
        }
        val ihdr = ByteBuffer.allocate(13).putInt(50_000).putInt(50_000).put(8).put(2).put(0).put(0).put(0).array()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", ihdr) + chunk("IEND", ByteArray(0))

        val error = assertFailsWith<ValidationException> { storage.save(png) }
        assertTrue(error.message!!.contains("çözünürlüğü"))
    }

    @Test
    fun `sadece uretilen bicimdeki dosya adlari cozulur`() {
        val stored = storage.save(image(50, 50, "jpg"))

        assertNotNull(storage.resolve(stored.fileName))
        assertNull(storage.resolve("../../etc/passwd"))
        assertNull(storage.resolve("q_00000000-0000-0000-0000-000000000000.jpg"))
    }
}
