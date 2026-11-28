-- =====================================================================
--  V18 - Cadastro completo do usuário, convite por e-mail e confirmação
--
--  Mesmo padrão dos demais sistemas:
--  - tb_usuario ganha contato, foto de perfil (miniatura JPEG em data URI,
--    reduzida no navegador), cargo, endereço com CEP e observações;
--  - email_verificado: FALSE enquanto o e-mail não for confirmado pelo link
--    (o login recusa com EMAIL_NAO_CONFIRMADO). Usuários já existentes
--    nascem confirmados (DEFAULT TRUE);
--  - convite_pendente: usuário criado por convite que ainda não definiu a
--    senha pelo link (a senha guardada é aleatória e ninguém a conhece);
--  - tb_token_redefinicao.tipo separa os links de uso único:
--    RECUPERACAO_SENHA ("Esqueci a senha"), CONVITE (definir a senha no
--    primeiro acesso) e VERIFICACAO_EMAIL (confirmar o endereço).
-- =====================================================================
ALTER TABLE tb_usuario ADD COLUMN telefone VARCHAR(20);
ALTER TABLE tb_usuario ADD COLUMN cargo VARCHAR(80);
ALTER TABLE tb_usuario ADD COLUMN foto_perfil TEXT;
ALTER TABLE tb_usuario ADD COLUMN cep VARCHAR(9);
ALTER TABLE tb_usuario ADD COLUMN endereco_tipo VARCHAR(20);
ALTER TABLE tb_usuario ADD COLUMN endereco VARCHAR(255);
ALTER TABLE tb_usuario ADD COLUMN numero VARCHAR(20);
ALTER TABLE tb_usuario ADD COLUMN complemento VARCHAR(255);
ALTER TABLE tb_usuario ADD COLUMN bairro VARCHAR(150);
ALTER TABLE tb_usuario ADD COLUMN cidade VARCHAR(100);
ALTER TABLE tb_usuario ADD COLUMN uf VARCHAR(2);
ALTER TABLE tb_usuario ADD COLUMN observacoes TEXT;
ALTER TABLE tb_usuario ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tb_usuario ADD COLUMN email_verificado_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE tb_usuario ADD COLUMN convite_pendente BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tb_token_redefinicao ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'RECUPERACAO_SENHA';
ALTER TABLE tb_token_redefinicao ADD CONSTRAINT ck_tb_token_redefinicao_tipo
    CHECK (tipo IN ('RECUPERACAO_SENHA', 'CONVITE', 'VERIFICACAO_EMAIL'));
-- =====================================================================
--  Fim da V18
-- =====================================================================
