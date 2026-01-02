-- Migration: Add TTS (Text-to-Speech) support
-- Run this in phpMyAdmin on Netsons to add TTS language column

-- Add tts_lang column to packages table
ALTER TABLE packages
ADD COLUMN tts_lang VARCHAR(10) NOT NULL DEFAULT 'it-IT' COMMENT 'Text-to-Speech language code'
AFTER total_questions;
