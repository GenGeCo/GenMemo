-- GenMemo Database Schema
-- Run this in phpMyAdmin on Netsons

-- Create database (if not using existing)
-- CREATE DATABASE IF NOT EXISTS genmemo_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE genmemo_db;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    INDEX idx_email (email),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Packages table
CREATE TABLE IF NOT EXISTS packages (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    uuid VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    topic VARCHAR(255),

    -- Settings
    question_types JSON NOT NULL COMMENT '["text", "tts", "image"]',
    answer_types JSON NOT NULL COMMENT '["text", "tts", "image"]',
    total_questions INT UNSIGNED NOT NULL DEFAULT 0,
    tts_lang VARCHAR(10) NOT NULL DEFAULT 'it-IT' COMMENT 'Text-to-Speech language code',

    -- Status
    status ENUM('draft', 'published', 'archived') NOT NULL DEFAULT 'draft',
    is_public TINYINT(1) NOT NULL DEFAULT 0,

    -- File references (R2)
    json_file_key VARCHAR(255) NULL COMMENT 'R2 key for package JSON',

    -- Stats
    download_count INT UNSIGNED NOT NULL DEFAULT 0,

    -- Timestamps
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    published_at DATETIME NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_status (status),
    INDEX idx_public (is_public),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Package media files (audio/images stored on R2)
CREATE TABLE IF NOT EXISTS package_media (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    package_id INT UNSIGNED NOT NULL,

    -- File info
    file_key VARCHAR(255) NOT NULL COMMENT 'R2 storage key',
    file_name VARCHAR(255) NOT NULL COMMENT 'Original filename',
    file_type ENUM('image', 'audio') NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size INT UNSIGNED NOT NULL COMMENT 'Size in bytes',

    -- Reference in JSON
    placeholder VARCHAR(100) NOT NULL COMMENT 'e.g., [INSERIRE_IMMAGINE_1]',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE CASCADE,
    INDEX idx_package (package_id),
    UNIQUE INDEX idx_placeholder (package_id, placeholder)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Package downloads log
CREATE TABLE IF NOT EXISTS package_downloads (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    package_id INT UNSIGNED NOT NULL,
    user_id INT UNSIGNED NULL COMMENT 'NULL if anonymous',
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(255),
    downloaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (package_id) REFERENCES packages(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_package (package_id),
    INDEX idx_date (downloaded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Password reset tokens
CREATE TABLE IF NOT EXISTS password_resets (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_token (token),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
