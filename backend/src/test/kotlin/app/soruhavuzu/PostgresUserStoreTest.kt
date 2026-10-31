package app.soruhavuzu

import app.soruhavuzu.users.DuplicateUserException
import app.soruhavuzu.users.NewUser
import app.soruhavuzu.users.PostgresUserStore
import kotlinx.coroutines.runBlocking
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gerçek Postgres'e karşı çalışır; Docker yoksa atlanır. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresUserStoreTest {

    private val store = PostgresUserStore(TestDatabase.dataSource)

    private fun newUser(email: String, nickname: String) =
        NewUser(email = email, passwordHash = "hash", nickname = nickname, birthYear = 2000, targetExamId = "exam_kpss")

    @Test
    fun `kullanici olusturulur ve varsayilan degerleri gelir`() = runBlocking {
        val user = store.create(newUser("ayse@ornek.com", "ayse"))

        assertEquals("USER", user.role)
        assertEquals(0, user.reputation)
        assertEquals(2000, user.birthYear)
        assertEquals(user, store.findById(user.id))

        val (found, hash) = assertNotNull(store.findByEmailWithPassword("AYSE@ornek.com"))
        assertEquals(user.id, found.id)
        assertEquals("hash", hash)
    }

    @Test
    fun `buyuk kucuk harf farkiyla ayni e-posta ve takma ad reddedilir`() = runBlocking {
        store.create(newUser("mehmet@ornek.com", "mehmet"))

        val email = assertFailsWith<DuplicateUserException> { store.create(newUser("MEHMET@ornek.com", "mehmet2")) }
        assertEquals("email", email.field)
        val nick = assertFailsWith<DuplicateUserException> { store.create(newUser("m2@ornek.com", "MEHMET")) }
        assertEquals("nickname", nick.field)
    }

    @Test
    fun `sinav kontrolu katalog migration'ini kullanir`() = runBlocking {
        assertTrue(store.examExists("exam_kpss"))
        assertFalse(store.examExists("exam_yok"))
        assertNull(store.findByEmailWithPassword("kimse@ornek.com"))
    }
}
