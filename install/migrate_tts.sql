-- Migration: Add answer_modes column
-- Run this in phpMyAdmin on Netsons

ALTER TABLE packages
ADD COLUMN answer_modes JSON NOT NULL DEFAULT '["multiple"]'
COMMENT '["multiple", "truefalse", "write_exact", "write_word", "write_partial"]'
AFTER answer_types;

-- If you previously added tts_lang column, you can remove it with:
-- ALTER TABLE packages DROP COLUMN tts_lang;
