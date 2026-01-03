-- Migration: Add infinite mode and review session tracking
-- Run this in phpMyAdmin on Netsons

-- Add session_type and infinite mode fields to study_sessions
ALTER TABLE study_sessions
    ADD COLUMN session_type ENUM('quiz', 'review', 'infinite') NOT NULL DEFAULT 'quiz'
        COMMENT 'quiz=normal, review=spaced repetition review, infinite=infinite mode' AFTER status,
    ADD COLUMN rounds_completed INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'For infinite mode: how many times cycled through questions' AFTER session_type,
    ADD COLUMN best_streak INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Longest correct streak in this session' AFTER rounds_completed,
    ADD COLUMN target_questions TEXT NULL
        COMMENT 'JSON array of question indices targeted for review' AFTER best_streak;

-- Add session type stats to daily_study_stats
ALTER TABLE daily_study_stats
    ADD COLUMN quiz_sessions INT UNSIGNED NOT NULL DEFAULT 0 AFTER total_correct,
    ADD COLUMN review_sessions INT UNSIGNED NOT NULL DEFAULT 0 AFTER quiz_sessions,
    ADD COLUMN infinite_sessions INT UNSIGNED NOT NULL DEFAULT 0 AFTER review_sessions;

-- Index for session type queries
ALTER TABLE study_sessions ADD INDEX idx_session_type (session_type);

-- View for review dashboard (questions due for review across all packages)
CREATE OR REPLACE VIEW v_questions_due_review AS
SELECT
    uqp.user_id,
    uqp.package_id,
    p.uuid as package_uuid,
    p.name as package_name,
    uqp.question_index,
    uqp.score,
    uqp.interval_days,
    uqp.next_review_date,
    uqp.streak,
    CASE
        WHEN uqp.next_review_date <= NOW() THEN 'urgent'
        WHEN uqp.score < 3 THEN 'weak'
        ELSE 'normal'
    END as priority
FROM user_question_progress uqp
JOIN packages p ON uqp.package_id = p.id
WHERE uqp.next_review_date <= DATE_ADD(NOW(), INTERVAL 1 DAY)
   OR uqp.score < 3
ORDER BY
    CASE WHEN uqp.next_review_date <= NOW() THEN 0 ELSE 1 END,
    uqp.score ASC,
    uqp.next_review_date ASC;
