-- =====================================================================
--  V20 - Backup: destino SFTP (mesmo padrão dos demais sistemas)
--
--  tb_backup_sftp (linha única, id = 1; sem linha = não configurado):
--    computador, NAS ou servidor da rede do cliente que recebe os
--    backups por SFTP. Senha, chave privada e frase-senha ficam cifradas
--    (AES-GCM). fingerprint = chave do servidor (SHA256:...) confirmada
--    pelo ADMIN no "Testar conexão"; sem ela nenhum backup é enviado.
--    retencao_dias / manter_minimo: retenção própria na pasta remota.
--
--  tb_backup_job: resultado do envio SFTP de cada execução.
--  tb_backup_agendamento: SFTP como destino do backup automático.
-- =====================================================================
CREATE TABLE tb_backup_sftp (
    id                     SMALLINT       NOT NULL DEFAULT 1,
    host                   VARCHAR(255),
    porta                  INTEGER        NOT NULL DEFAULT 22,
    usuario                VARCHAR(120),
    autenticacao           VARCHAR(10)    NOT NULL DEFAULT 'SENHA',
    senha_cifrada          VARCHAR(1024),
    chave_privada_cifrada  TEXT,
    frase_chave_cifrada    VARCHAR(1024),
    pasta_remota           VARCHAR(255),
    fingerprint            VARCHAR(100),
    tipo_chave             VARCHAR(40),
    enviar_manuais         BOOLEAN        NOT NULL DEFAULT FALSE,
    retencao_dias          INTEGER        NOT NULL DEFAULT 30,
    manter_minimo          INTEGER        NOT NULL DEFAULT 3,
    ultimo_envio_em        TIMESTAMP WITH TIME ZONE,
    ultimo_envio_status    VARCHAR(20),
    ultimo_envio_mensagem  VARCHAR(1000),
    atualizado_em          TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por         VARCHAR(160),
    CONSTRAINT pk_tb_backup_sftp PRIMARY KEY (id),
    CONSTRAINT ck_tb_backup_sftp_unico CHECK (id = 1),
    CONSTRAINT ck_tb_backup_sftp_porta CHECK (porta BETWEEN 1 AND 65535),
    CONSTRAINT ck_tb_backup_sftp_autenticacao CHECK (autenticacao IN ('SENHA', 'CHAVE')),
    CONSTRAINT ck_tb_backup_sftp_retencao CHECK (retencao_dias BETWEEN 0 AND 3650),
    CONSTRAINT ck_tb_backup_sftp_minimo CHECK (manter_minimo BETWEEN 1 AND 100)
);

ALTER TABLE tb_backup_job ADD COLUMN enviar_sftp BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_backup_job ADD COLUMN sftp_status VARCHAR(20);
ALTER TABLE tb_backup_job ADD COLUMN sftp_mensagem VARCHAR(1000);

ALTER TABLE tb_backup_agendamento ADD COLUMN destino_sftp BOOLEAN NOT NULL DEFAULT FALSE;
-- =====================================================================
--  Fim da V20
-- =====================================================================
