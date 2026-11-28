-- =====================================================================
--  V01 - Tabela de usuários do ChirpStack Manager
--  Usuários que acessam o painel (independentes dos usuários do ChirpStack).
--  Perfis: ADMIN, OPERADOR, VISUALIZADOR.
-- =====================================================================
CREATE TABLE tb_usuario (
    id                 BIGSERIAL     PRIMARY KEY,
    nome               VARCHAR(120)  NOT NULL,
    email              VARCHAR(160)  NOT NULL,
    senha_hash         VARCHAR(100)  NOT NULL,
    perfil             VARCHAR(20)   NOT NULL,
    ativo              BOOLEAN       NOT NULL DEFAULT TRUE,
    tentativas_falhas  INTEGER       NOT NULL DEFAULT 0,
    bloqueado_ate      TIMESTAMP WITH TIME ZONE,
    -- Bloqueio manual feito por um ADMIN (tela de Visitantes): com motivo_bloqueio
    -- preenchido o login mostra o motivo e a data de liberacao (bloqueado_ate).
    motivo_bloqueio    VARCHAR(255),
    bloqueado_por      VARCHAR(160),
    ultimo_login       TIMESTAMP WITH TIME ZONE,
    criado_em          TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_tb_usuario_email UNIQUE (email),
    CONSTRAINT ck_tb_usuario_perfil CHECK (perfil IN ('ADMIN', 'OPERADOR', 'VISUALIZADOR'))
);
-- =====================================================================
--  Fim da V01
-- =====================================================================
