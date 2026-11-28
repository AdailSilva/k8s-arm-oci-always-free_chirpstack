-- =====================================================================
--  V12 - Regras de alerta e alertas disparados
--
--  tb_regra_alerta  tipo VALOR: compara um campo da leitura com o limite.
--                   "campo" é um caminho do objeto decodificado
--                   (ex.: temperatura, sensor.umidade) ou um dos campos de
--                   rádio: rssi, snr, bateria.
--                   tipo SEM_COMUNICACAO: dispara quando o dispositivo
--                   passa "limite" minutos sem mandar nada.
--                   dispositivo_id NULO = vale para todos os dispositivos.
--                   operador: GT, GTE, LT, LTE, EQ, NE (só para VALOR).
--  tb_alerta        um por disparo. Fica ABERTO até alguém reconhecer
--                   (RECONHECIDO) ou o valor voltar ao normal (RESOLVIDO).
--                   Enquanto houver um ABERTO/RECONHECIDO para a mesma
--                   regra e dispositivo, não nasce outro.
-- =====================================================================
CREATE TABLE tb_regra_alerta (
    id                 BIGSERIAL     PRIMARY KEY,
    nome               VARCHAR(120)  NOT NULL,
    tipo               VARCHAR(20)   NOT NULL,
    dispositivo_id     BIGINT,
    campo              VARCHAR(120),
    operador           VARCHAR(3),
    limite             NUMERIC(14,4) NOT NULL,
    severidade         VARCHAR(10)   NOT NULL DEFAULT 'AVISO',
    notificar_email    BOOLEAN       NOT NULL DEFAULT FALSE,
    destinatarios      VARCHAR(500),
    ativo              BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em          TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por     VARCHAR(160),
    CONSTRAINT fk_tb_regra_alerta_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES tb_dispositivo (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_regra_alerta_tipo CHECK (tipo IN ('VALOR', 'SEM_COMUNICACAO')),
    CONSTRAINT ck_tb_regra_alerta_operador CHECK (operador IS NULL OR operador IN ('GT', 'GTE', 'LT', 'LTE', 'EQ', 'NE')),
    CONSTRAINT ck_tb_regra_alerta_severidade CHECK (severidade IN ('INFO', 'AVISO', 'CRITICO'))
);

CREATE TABLE tb_alerta (
    id                 BIGSERIAL     PRIMARY KEY,
    regra_id           BIGINT        NOT NULL,
    dispositivo_id     BIGINT        NOT NULL,
    status             VARCHAR(12)   NOT NULL DEFAULT 'ABERTO',
    valor              NUMERIC(14,4),
    mensagem           VARCHAR(300)  NOT NULL,
    disparado_em       TIMESTAMP WITH TIME ZONE NOT NULL,
    reconhecido_em     TIMESTAMP WITH TIME ZONE,
    reconhecido_por    VARCHAR(160),
    resolvido_em       TIMESTAMP WITH TIME ZONE,
    email_enviado      BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_tb_alerta_regra FOREIGN KEY (regra_id) REFERENCES tb_regra_alerta (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_alerta_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES tb_dispositivo (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_alerta_status CHECK (status IN ('ABERTO', 'RECONHECIDO', 'RESOLVIDO'))
);
CREATE INDEX idx_tb_alerta_status ON tb_alerta (status, disparado_em);
CREATE INDEX idx_tb_alerta_regra_disp ON tb_alerta (regra_id, dispositivo_id, status);
-- =====================================================================
--  Fim da V12
-- =====================================================================
