-- =====================================================================
--  V06 - Conta de e-mail (linha única, id = 1)
--
--  Senha de app do SMTP, CIFRADA (AES-256-GCM, chave fora do banco,
--  APP_CHAVE_CIFRAGEM). A carga geral grava a "semente" v0:BASE64; na
--  primeira subida o sistema troca pela cifra v1: com a chave do cluster.
--  Sem linha aqui, vale a senha da instalação (spring.mail.password).
-- =====================================================================
CREATE TABLE tb_configuracao_email (
    id                 SMALLINT      NOT NULL DEFAULT 1,
    senha_app_cifrada  VARCHAR(512)  NOT NULL,
    atualizado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por     VARCHAR(160),
    CONSTRAINT pk_tb_configuracao_email PRIMARY KEY (id),
    CONSTRAINT ck_tb_configuracao_email_unica CHECK (id = 1)
);
-- =====================================================================
--  Fim da V06
-- =====================================================================
