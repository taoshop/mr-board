ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed TINYINT(1) NOT NULL DEFAULT 0;

UPDATE users SET password_changed = 1 WHERE username = 'admin';
