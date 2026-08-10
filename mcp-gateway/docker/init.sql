CREATE DATABASE IF NOT EXISTS mcp_gateway DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE mcp_gateway;
CREATE TABLE IF NOT EXISTS biz_system (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  code VARCHAR(64) NOT NULL,
  base_url VARCHAR(512) NOT NULL,
  description VARCHAR(512) NULL,
  auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
  auth_config JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_biz_system_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS api_endpoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  description VARCHAR(1024) NULL,
  http_method VARCHAR(16) NOT NULL,
  path_template VARCHAR(512) NOT NULL,
  parameters_json JSON NULL,
  request_body_schema JSON NULL,
  headers_json JSON NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  operation_id VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_api_system_tool (system_id, tool_name),
  KEY idx_api_system (system_id),
  CONSTRAINT fk_api_system FOREIGN KEY (system_id) REFERENCES biz_system(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mcp_server (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  slug VARCHAR(64) NOT NULL,
  description VARCHAR(512) NULL,
  access_token VARCHAR(1024) NULL,
  auth_mode VARCHAR(32) NOT NULL DEFAULT 'JWT',
  jwt_secret VARCHAR(256) NULL,
  published TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mcp_server_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mcp_server_api (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  server_id BIGINT NOT NULL,
  api_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_server_api (server_id, api_id),
  KEY idx_msa_api (api_id),
  CONSTRAINT fk_msa_server FOREIGN KEY (server_id) REFERENCES mcp_server(id) ON DELETE CASCADE,
  CONSTRAINT fk_msa_api FOREIGN KEY (api_id) REFERENCES api_endpoint(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tool_call_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  slug VARCHAR(64) NOT NULL,
  caller_key_hash VARCHAR(64) NULL,
  caller_subject VARCHAR(128) NULL,
  tool_name VARCHAR(128) NOT NULL,
  arguments_summary VARCHAR(1024) NULL,
  success TINYINT(1) NOT NULL,
  error_message VARCHAR(1024) NULL,
  duration_ms INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_audit_created (created_at),
  KEY idx_audit_slug_tool (slug, tool_name),
  KEY idx_audit_caller (caller_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;