-- ==================== USERS ====================
-- Teachers require admin approval after email verification
ALTER TABLE users ADD COLUMN approved BOOLEAN NOT NULL DEFAULT FALSE;

-- Students are auto-approved, teachers require manual approval
-- This will be handled in application logic:
-- STUDENT: verified=true → approved=true automatically
-- TEACHER: verified=true → approved=false → admin sets approved=true


-- ==================== EXAM INVITATIONS ====================
-- Required for EXPIRED status to have meaning
ALTER TABLE exam_invitations ADD COLUMN expires_at TIMESTAMP;

-- Backfill existing rows if any (set to 7 days after sent_at as default)
UPDATE exam_invitations SET expires_at = sent_at + INTERVAL '7 days' WHERE expires_at IS NULL;

-- Make it NOT NULL now that existing rows are backfilled
ALTER TABLE exam_invitations ALTER COLUMN expires_at SET NOT NULL;


-- ==================== AUDIT FIELDS (created_by / updated_by) ====================
-- exams
ALTER TABLE exams ADD COLUMN updated_by UUID REFERENCES users(id);

-- exam_settings
ALTER TABLE exam_settings ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE exam_settings ADD COLUMN updated_by UUID REFERENCES users(id);

-- questions
ALTER TABLE questions ADD COLUMN created_by UUID REFERENCES users(id);
ALTER TABLE questions ADD COLUMN updated_by UUID REFERENCES users(id);

-- Note: options have no created_at/updated_at so no audit fields needed
-- Note: exam_sessions — created_by is implicitly student_id, no need to duplicate
-- Note: answers, violations are system/student generated, no audit fields needed
-- Note: exam_invitations — sent_by already serves as created_by


-- ==================== GRADING ====================
-- Session-level grading summary
ALTER TABLE exam_sessions ADD COLUMN total_score     NUMERIC(7,2);
ALTER TABLE exam_sessions ADD COLUMN graded_at       TIMESTAMP;
ALTER TABLE exam_sessions ADD COLUMN graded_by       UUID REFERENCES users(id);

-- Answer-level manual grading tracker
ALTER TABLE answers ADD COLUMN graded_by UUID REFERENCES users(id);
ALTER TABLE answers ADD COLUMN graded_at TIMESTAMP;

-- Index for grading queries
CREATE INDEX idx_exam_sessions_graded_by ON exam_sessions(graded_by);
CREATE INDEX idx_answers_session_question ON answers(session_id, question_id);
