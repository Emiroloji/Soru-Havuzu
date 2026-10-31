package app.soruhavuzu

/** 400: istemcinin gönderdiği veri kurallara uymuyor. */
class ValidationException(message: String) : RuntimeException(message)

/** 404: kayıt yok ya da kullanıcıya gösterilemez durumda. */
class NotFoundException(message: String) : RuntimeException(message)

/** 403: kayıt var ama bu kullanıcı bu işlemi yapamaz. */
class ForbiddenException(message: String) : RuntimeException(message)

/** 409: istek mevcut bir kayıtla çakışıyor (ör. aynı soruya ikinci açık şikayet). */
class ConflictException(message: String) : RuntimeException(message)

inline fun validate(condition: Boolean, message: () -> String) {
    if (!condition) throw ValidationException(message())
}
