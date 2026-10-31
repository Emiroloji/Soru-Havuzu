package app.soruhavuzu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {

    @Test
    fun `dokploy internal url jdbc bicimine cevrilir`() {
        val config = parseDatabaseUrl(
            url = "postgresql://soru:g%40izli@soruhavuzu-db-abc123:5432/soruhavuzu",
            user = null,
            password = null,
            maxPoolSize = 5
        )

        assertEquals("jdbc:postgresql://soruhavuzu-db-abc123:5432/soruhavuzu", config.jdbcUrl)
        assertEquals("soru", config.user)
        assertEquals("g@izli", config.password)
    }

    @Test
    fun `port yoksa 5432 kullanilir ve ayri verilen kullanici one gecer`() {
        val config = parseDatabaseUrl("postgres://a:b@localhost/dev", user = "baska", password = null, maxPoolSize = 5)

        assertEquals("jdbc:postgresql://localhost:5432/dev", config.jdbcUrl)
        assertEquals("baska", config.user)
        assertEquals("b", config.password)
    }

    @Test
    fun `jdbc url ayri kullanici ve sifre ister`() {
        assertFailsWith<IllegalStateException> {
            parseDatabaseUrl("jdbc:postgresql://localhost:5432/dev", user = null, password = null, maxPoolSize = 5)
        }
        val config = parseDatabaseUrl("jdbc:postgresql://localhost:5432/dev", "u", "p", 5)
        assertEquals("jdbc:postgresql://localhost:5432/dev", config.jdbcUrl)
    }

    @Test
    fun `kisa jwt anahtari reddedilir`() {
        assertFailsWith<IllegalArgumentException> { JwtConfig(secret = "kisa") }
    }

    @Test
    fun `desteklenmeyen sema reddedilir`() {
        assertFailsWith<IllegalArgumentException> {
            parseDatabaseUrl("mysql://a:b@localhost/dev", null, null, 5)
        }
    }
}
