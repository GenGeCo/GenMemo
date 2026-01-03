-- Study Sessions Table
-- Tracks when users open/close the app and study packages

CREATE TABLE IF NOT EXISTS study_sessions (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    package_id INT UNSIGNED NULL COMMENT 'NULL if general app session',

    -- Session timing
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    duration_seconds INT UNSIGNED NULL COMMENT 'Calculated on end',

    -- Activity stats during session
    questions_answered INT UNSIGNED NOT NULL DEFAULT 0,
    correct_answers INT UNSIGNED NOT NULL DEFAULT 0,

    -- Device info
    device_info VARCHAR(255) NULL,
    app_version VARCHAR(50) NULL,

    -- Status
    status ENUM('active', 'completed', 'abandoned') NOT NULL DEFAULT 'active',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE SET NULL,
    INDEX idx_user (user_id),
    INDEX idx_package (package_id),
    INDEX idx_started (started_at),
    INDEX idx_user_date (user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Daily study summary (aggregated for faster dashboard queries)
CREATE TABLE IF NOT EXISTS daily_study_stats (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    package_id INT UNSIGNED NULL,
    study_date DATE NOT NULL,

    -- Aggregated stats
    total_sessions INT UNSIGNED NOT NULL DEFAULT 0,
    total_duration_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    total_questions INT UNSIGNED NOT NULL DEFAULT 0,
    total_correct INT UNSIGNED NOT NULL DEFAULT 0,

    -- First and last activity times
    first_session_at TIME NULL,
    last_session_at TIME NULL,

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE SET NULL,
    UNIQUE INDEX idx_user_package_date (user_id, package_id, study_date),
    INDEX idx_date (study_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
