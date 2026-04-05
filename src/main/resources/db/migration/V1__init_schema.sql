-- pgcrypto only needed if PostgreSQL version is below 13
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==================== USERS ====================
CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,               -- enum: STUDENT, TEACHER, ADMIN
    verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(), -- set once on insert, never changes
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ==================== REFRESH TOKENS ====================
CREATE TABLE refresh_tokens
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ==================== EXAMS ====================
CREATE TABLE exams
(
    id                 UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    name               VARCHAR(200) NOT NULL,
    description        TEXT         NOT NULL,
    time_limit_minutes INT          NOT NULL,
    start_time         TIMESTAMP    NOT NULL,
    end_time           TIMESTAMP    NOT NULL,
    created_by         UUID         NOT NULL REFERENCES users (id), -- no CASCADE: deleting teacher should not delete exams
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT now()
);

-- ==================== EXAM SETTINGS ====================
CREATE TABLE exam_settings
(
    id                    UUID PRIMARY KEY   DEFAULT gen_random_uuid(),
    exam_id               UUID      NOT NULL UNIQUE REFERENCES exams (id) ON DELETE CASCADE,
    require_camera        BOOLEAN   NOT NULL DEFAULT FALSE,
    require_microphone    BOOLEAN   NOT NULL DEFAULT FALSE,
    allow_copy_paste      BOOLEAN   NOT NULL DEFAULT TRUE,
    allow_tab_switch      BOOLEAN   NOT NULL DEFAULT FALSE,
    max_idle_seconds      INT,
    max_violations        INT,
    random_photo_interval INT, -- how often (seconds) to take a screenshot; null means disabled
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- ==================== QUESTIONS ====================
CREATE TABLE questions
(
    id          UUID PRIMARY KEY       DEFAULT gen_random_uuid(),
    exam_id     UUID          NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    type        VARCHAR(30)   NOT NULL, -- enum: MULTIPLE_CHOICE, TEXT, FILE_UPLOAD
    content     TEXT          NOT NULL,
    max_points  NUMERIC(5, 2) NOT NULL DEFAULT 0,
    order_index INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP     NOT NULL DEFAULT now()
);

-- ==================== OPTIONS ====================
CREATE TABLE options
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID    NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    text        TEXT    NOT NULL,
    correct     BOOLEAN NOT NULL DEFAULT FALSE
);

-- ==================== EXAM SESSIONS ====================
CREATE TABLE exam_sessions
(
    id                    UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    exam_id               UUID        NOT NULL REFERENCES exams (id), -- no CASCADE: session history is preserved
    student_id            UUID        NOT NULL REFERENCES users (id), -- no CASCADE: session history is preserved
    started_at            TIMESTAMP   NOT NULL DEFAULT now(),
    submitted_at          TIMESTAMP,
    updated_at            TIMESTAMP   NOT NULL DEFAULT now(),         -- no default: always set explicitly by app
    status                VARCHAR(20) NOT NULL,                       -- enum: PENDING, IN_PROGRESS, SUBMITTED, FLAGGED
    trust_score           INT,
    random_photo_location VARCHAR(500)                                -- folder path/url where session screenshots are stored; null if camera not required
);

-- ==================== ANSWERS ====================
CREATE TABLE answers
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id         UUID NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    question_id        UUID NOT NULL REFERENCES questions (id), -- no CASCADE: answer record preserved even if question changes
    selected_option_id UUID REFERENCES options (id),            -- nullable: only for MULTIPLE_CHOICE questions
    text_answer        TEXT,                                    -- nullable: only for TEXT questions
    file_url           VARCHAR(500),                            -- nullable: only for FILE_UPLOAD questions
    points_awarded     NUMERIC(5, 2)                            -- nullable: set after grading
);

-- ==================== VIOLATIONS ====================
CREATE TABLE violations
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    session_id       UUID        NOT NULL REFERENCES exam_sessions (id) ON DELETE CASCADE,
    type             VARCHAR(50) NOT NULL, -- enum: TAB_SWITCH, IDLE, COPY_PASTE, etc.
    occurred_at      TIMESTAMP   NOT NULL DEFAULT now(),
    duration_seconds INT,
    evidence_url     VARCHAR(500),
    penalty_score    NUMERIC(5, 2)
);

-- ==================== EXAM INVITATIONS ====================
CREATE TABLE exam_invitations
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    exam_id     UUID        NOT NULL REFERENCES exams (id) ON DELETE CASCADE,
    student_id  UUID        NOT NULL REFERENCES users (id), -- no CASCADE: invitation history preserved
    sent_by     UUID        NOT NULL REFERENCES users (id), -- no CASCADE: invitation history preserved
    status      VARCHAR(20) NOT NULL,                       -- enum: SENT, ACCEPTED, DECLINED, EXPIRED
    sent_at     TIMESTAMP   NOT NULL DEFAULT now(),
    accepted_at TIMESTAMP                                   -- nullable: set when student accepts
);

-- ==================== INDEXES ====================
-- PostgreSQL auto-indexes PKs but NOT foreign keys
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_exams_created_by ON exams (created_by);
CREATE INDEX idx_questions_exam_id ON questions (exam_id);
CREATE INDEX idx_options_question_id ON options (question_id);
CREATE INDEX idx_exam_sessions_exam_id ON exam_sessions (exam_id);
CREATE INDEX idx_exam_sessions_student_id ON exam_sessions (student_id);
CREATE INDEX idx_answers_session_id ON answers (session_id);
CREATE INDEX idx_answers_question_id ON answers (question_id);
CREATE INDEX idx_violations_session_id ON violations (session_id);
CREATE INDEX idx_invitations_exam_id ON exam_invitations (exam_id);
CREATE INDEX idx_invitations_student_id ON exam_invitations (student_id);