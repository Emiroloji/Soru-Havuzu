package app.soruhavuzu.users

/**
 * İtibar kapıları: ≥50 doğrudan yayın, ≥200 doğrulayıcı (şikayeti 2 kat sayılır), ≥1000 topluluk moderatörü.
 * Editör/moderatör/yönetici rolleri itibardan bağımsız olarak bu yetkilere sahiptir.
 */
object Permissions {
    const val DIRECT_PUBLISH_REPUTATION = 50
    const val VERIFIER_REPUTATION = 200
    const val MODERATOR_REPUTATION = 1000

    fun canPublishDirectly(role: String, reputation: Int): Boolean =
        reputation >= DIRECT_PUBLISH_REPUTATION || role in setOf("EDITOR", "MODERATOR", "ADMIN")

    fun isVerifier(role: String, reputation: Int): Boolean =
        reputation >= VERIFIER_REPUTATION || role in setOf("MODERATOR", "ADMIN")

    fun canModerate(role: String, reputation: Int): Boolean =
        reputation >= MODERATOR_REPUTATION || role in setOf("MODERATOR", "ADMIN")
}
