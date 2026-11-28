-- =====================================================================
--  V10 - Tokens de redefinição de senha ("Esqueci a senha")
--
--  O link enviado por e-mail leva o token; aqui fica só o SHA-256 dele.
--  Vale uma vez e por pouco tempo (expira_em). Pedir de novo invalida os
--  anteriores do mesmo usuário.
-- =====================================================================
CREATE TABLE tb_token_redefinicao (
    id           BIGSERIAL     PRIMARY KEY,
    usuario_id   BIGINT        NOT NULL,
    token_hash   VARCHAR(64)   NOT NULL,
    criado_em    TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em    TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_em     TIMESTAMP WITH TIME ZONE,
    ip           VARCHAR(64),
    CONSTRAINT uk_tb_token_redefinicao_hash UNIQUE (token_hash),
    CONSTRAINT fk_tb_token_redefinicao_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuario (id) ON DELETE CASCADE
);
CREATE INDEX idx_tb_token_redefinicao_usuario ON tb_token_redefinicao (usuario_id);
-- =====================================================================
--  Fim da V10
-- =====================================================================
