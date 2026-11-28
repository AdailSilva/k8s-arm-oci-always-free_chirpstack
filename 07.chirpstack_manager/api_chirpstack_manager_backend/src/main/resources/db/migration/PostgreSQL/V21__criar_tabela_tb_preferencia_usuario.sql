-- =====================================================================
--  V21 - Preferências de aparência por usuário (mesmo padrão dos demais
--  sistemas): tema, cor primária, superfície, modo escuro e modo do
--  menu ficam no servidor e valem em qualquer navegador ou dispositivo.
--  Sem linha = o usuário ainda não escolheu (vale o padrão do sistema).
-- =====================================================================
CREATE TABLE tb_preferencia_usuario (
    usuario_id     BIGINT        NOT NULL,
    preset         VARCHAR(20)   NOT NULL DEFAULT 'Aura',
    cor_primaria   VARCHAR(30)   NOT NULL DEFAULT 'blue',
    superficie     VARCHAR(30),
    tema_escuro    BOOLEAN       NOT NULL DEFAULT FALSE,
    modo_menu      VARCHAR(20)   NOT NULL DEFAULT 'static',
    atualizado_em  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tb_preferencia_usuario PRIMARY KEY (usuario_id),
    CONSTRAINT fk_tb_preferencia_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_preferencia_usuario_preset CHECK (preset IN ('Aura', 'Lara', 'Nora'))
);
-- =====================================================================
--  Fim da V21
-- =====================================================================
