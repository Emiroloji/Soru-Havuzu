-- SoruHavuzu ilk şema: Sınav -> Ders -> Konu kataloğu, kullanıcılar, sorular ve kalibrasyon kayıtları.
-- Zorluk ölçeği: difficulty_logit iç Rasch değeri (-3..3), difficulty_bucket kullanıcıya gösterilen 1..10.

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT NOT NULL,
    password_hash   TEXT NOT NULL,
    nickname        TEXT NOT NULL,
    role            TEXT NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('USER', 'EDITOR', 'MODERATOR', 'ADMIN')),
    reputation      INT NOT NULL DEFAULT 0,
    theta           DOUBLE PRECISION NOT NULL DEFAULT 0,
    uploader_bias   DOUBLE PRECISION NOT NULL DEFAULT 0,
    birth_year      INT,
    target_exam_id  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX users_email_uq ON users (lower(email));
CREATE UNIQUE INDEX users_nickname_uq ON users (lower(nickname));

CREATE TABLE exams (
    id           TEXT PRIMARY KEY,
    code         TEXT NOT NULL UNIQUE,
    title        TEXT NOT NULL,
    description  TEXT NOT NULL DEFAULT '',
    order_index  INT NOT NULL DEFAULT 0
);

CREATE TABLE subjects (
    id           TEXT PRIMARY KEY,
    exam_id      TEXT NOT NULL REFERENCES exams (id),
    name         TEXT NOT NULL,
    order_index  INT NOT NULL DEFAULT 0,
    UNIQUE (exam_id, name)
);

CREATE TABLE topics (
    id           TEXT PRIMARY KEY,
    subject_id   TEXT NOT NULL REFERENCES subjects (id),
    name         TEXT NOT NULL,
    order_index  INT NOT NULL DEFAULT 0,
    UNIQUE (subject_id, name)
);

ALTER TABLE users ADD CONSTRAINT users_target_exam_fk FOREIGN KEY (target_exam_id) REFERENCES exams (id);

CREATE TABLE questions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploader_id            UUID NOT NULL REFERENCES users (id),
    topic_id               TEXT NOT NULL REFERENCES topics (id),
    current_version_id     UUID,
    status                 TEXT NOT NULL DEFAULT 'IN_REVIEW'
                           CHECK (status IN ('IN_REVIEW', 'ACTIVE', 'FLAGGED', 'REMOVED')),
    uploader_rating        SMALLINT NOT NULL CHECK (uploader_rating BETWEEN 1 AND 10),
    difficulty_logit       DOUBLE PRECISION NOT NULL,
    difficulty_bucket      SMALLINT NOT NULL CHECK (difficulty_bucket BETWEEN 1 AND 10),
    attempt_count          INT NOT NULL DEFAULT 0,
    first_attempt_count    INT NOT NULL DEFAULT 0,
    correct_first_count    INT NOT NULL DEFAULT 0,
    key_suspect            BOOLEAN NOT NULL DEFAULT false,
    key_suspect_reason     TEXT,
    weighted_report_score  INT NOT NULL DEFAULT 0,
    content_hash           TEXT NOT NULL,
    image_phash            TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- "KPSS Tarih'ten 4 zorluğunda soru getir" sorgusunun ana indeksi
CREATE INDEX questions_pick_idx ON questions (topic_id, status, difficulty_bucket);
CREATE INDEX questions_content_hash_idx ON questions (content_hash) WHERE status <> 'REMOVED';
CREATE INDEX questions_uploader_idx ON questions (uploader_id);

-- Şık seçim sayıları versiyona bağlı tutulur: anahtar veya şık sırası değişince yeni versiyon sıfırdan sayar.
CREATE TABLE question_versions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id              UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    version_number           INT NOT NULL,
    body_text                TEXT NOT NULL,
    image_path               TEXT,
    options                  TEXT[] NOT NULL CHECK (cardinality(options) BETWEEN 2 AND 5),
    correct_option_index     SMALLINT NOT NULL,
    option_selection_counts  INT[] NOT NULL,
    solution_text            TEXT NOT NULL,
    editor_note              TEXT,
    created_by               UUID NOT NULL REFERENCES users (id),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (question_id, version_number),
    CHECK (correct_option_index >= 0 AND correct_option_index < cardinality(options)),
    CHECK (cardinality(option_selection_counts) = cardinality(options))
);

ALTER TABLE questions ADD CONSTRAINT questions_current_version_fk
    FOREIGN KEY (current_version_id) REFERENCES question_versions (id) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE attempts (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id            UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    version_id             UUID NOT NULL REFERENCES question_versions (id) ON DELETE CASCADE,
    session_type           TEXT NOT NULL CHECK (session_type IN ('PRACTICE', 'CHALLENGE', 'REVIEW')),
    selected_option_index  SMALLINT NOT NULL,
    is_correct             BOOLEAN NOT NULL,
    is_first               BOOLEAN NOT NULL,
    response_time_ms       BIGINT NOT NULL,
    user_theta_before      DOUBLE PRECISION NOT NULL,
    user_theta_after       DOUBLE PRECISION NOT NULL,
    question_logit_before  DOUBLE PRECISION NOT NULL,
    question_logit_after   DOUBLE PRECISION NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Kalibrasyonu yalnızca ilk deneme günceller; aynı soruya iki "ilk deneme" yazılmasını veritabanı engeller.
CREATE UNIQUE INDEX attempts_first_uq ON attempts (user_id, question_id) WHERE is_first;
CREATE INDEX attempts_user_idx ON attempts (user_id, created_at DESC);

CREATE TABLE topic_mastery (
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic_id       TEXT NOT NULL REFERENCES topics (id),
    theta          DOUBLE PRECISION NOT NULL,
    solved_count   INT NOT NULL DEFAULT 0,
    correct_count  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE bookmarks (
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id    UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    is_favorite    BOOLEAN NOT NULL DEFAULT false,
    is_wrong_note  BOOLEAN NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, question_id)
);

CREATE TABLE reputation_events (
    id                   BIGSERIAL PRIMARY KEY,
    user_id              UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    delta                INT NOT NULL,
    reason               TEXT NOT NULL,
    related_question_id  UUID REFERENCES questions (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX reputation_events_user_idx ON reputation_events (user_id, created_at DESC);

CREATE TABLE reports (
    id           BIGSERIAL PRIMARY KEY,
    question_id  UUID NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    reporter_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason       TEXT NOT NULL
                 CHECK (reason IN ('WRONG_KEY', 'COPYRIGHT_OSYM', 'DUPLICATE', 'UNCLEAR', 'INAPPROPRIATE', 'OTHER')),
    weight       INT NOT NULL,
    note         TEXT NOT NULL DEFAULT '',
    status       TEXT NOT NULL DEFAULT 'OPEN'
                 CHECK (status IN ('OPEN', 'RESOLVED_FIXED', 'RESOLVED_REMOVED', 'DISMISSED')),
    resolved_by  UUID REFERENCES users (id),
    resolved_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Aynı kişi aynı soruya açıkken ikinci şikayeti atıp skoru şişiremez
CREATE UNIQUE INDEX reports_open_uq ON reports (question_id, reporter_id) WHERE status = 'OPEN';
CREATE INDEX reports_status_idx ON reports (status, created_at DESC);
