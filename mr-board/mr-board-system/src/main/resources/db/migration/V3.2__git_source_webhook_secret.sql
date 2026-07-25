ALTER TABLE git_sources ADD COLUMN webhook_secret VARCHAR(256) COMMENT 'Webhook签名密钥';
