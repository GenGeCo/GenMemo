-- Migration: Add table for spaced repetition question progress
-- Run this in phpMyAdmin on Netsons

CREATE TABLE IF NOT EXISTS user_question_progress (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    package_id INT UNSIGNED NOT NULL,
    question_index INT UNSIGNED NOT NULL COMMENT 'Indice della domanda nel pacchetto (0-based)',

    -- Spaced repetition fields
    score INT NOT NULL DEFAULT 0 COMMENT '0-100, quanto bene la conosce',
    interval_days FLOAT NOT NULL DEFAULT 1 COMMENT 'Giorni tra i ripassi',
    next_review_date DATETIME NOT NULL COMMENT 'Quando ripassare',
    streak INT NOT NULL DEFAULT 0 COMMENT 'Risposte corrette consecutive',
    correct_days INT NOT NULL DEFAULT 0 COMMENT 'Giorni diversi con risposta corretta (serve 10 per padronanza)',
    last_correct_date DATE NULL COMMENT 'Ultimo giorno risposto corretto',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_package_question (user_id, package_id, question_index),
    INDEX idx_user_review (user_id, next_review_date),
    INDEX idx_package (package_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
