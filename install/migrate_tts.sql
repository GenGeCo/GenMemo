-- Migration: TTS language detection is automatic
-- No database changes needed - the app will auto-detect language from text
-- This file is kept for reference only

-- If you previously added tts_lang column, you can remove it with:
-- ALTER TABLE packages DROP COLUMN tts_lang;
