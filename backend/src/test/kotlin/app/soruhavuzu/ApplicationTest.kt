package app.soruhavuzu

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health veritabani ayaktaysa 200 doner`() = testApplication {
        application { module(testServices(healthCheck = { true })) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"database\":\"up\""))
    }

    @Test
    fun `health veritabani duserse 503 doner`() = testApplication {
        application { module(testServices(healthCheck = { false })) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(response.bodyAsText().contains("\"database\":\"down\""))
    }
}
