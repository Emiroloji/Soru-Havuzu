package app.soruhavuzu.users

import java.sql.Connection
import java.util.UUID

/**
 * İtibar defteri: her değişiklik hem kullanıcının toplamına hem de nedeniyle birlikte
 * reputation_events tablosuna yazılır. Çağıranın transaction'ı içinde çalışır.
 */
object ReputationLedger {

    const val QUESTION_PUBLISHED = 5
    const val VALID_REPORT = 2
    const val QUESTION_REMOVED = -15

    fun add(conn: Connection, userId: UUID, delta: Int, reason: String, questionId: UUID?) {
        conn.prepareStatement("UPDATE users SET reputation = greatest(0, reputation + ?) WHERE id = ?").use { st ->
            st.setInt(1, delta)
            st.setObject(2, userId)
            st.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO reputation_events (user_id, delta, reason, related_question_id) VALUES (?, ?, ?, ?)"
        ).use { st ->
            st.setObject(1, userId)
            st.setInt(2, delta)
            st.setString(3, reason)
            st.setObject(4, questionId)
            st.executeUpdate()
        }
    }
}
