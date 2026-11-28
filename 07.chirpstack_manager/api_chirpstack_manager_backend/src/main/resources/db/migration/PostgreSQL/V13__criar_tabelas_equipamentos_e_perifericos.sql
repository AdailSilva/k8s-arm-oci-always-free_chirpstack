-- =====================================================================
--  V13 - Equipamentos (modelo de firmware) e periféricos
--
--  tb_equipamento ...... um por dispositivo que recebe comandos. "modelo"
--                        diz como falar com o firmware:
--                          ADAILSILVA_V1 ....... protocolo binário próprio
--                                                (portas 10/11/20), com
--                                                resposta por "seq";
--                          RADIOENGE_ORIGINAL .. comandos AT (porta 103),
--                                                binários (102) e serial
--                                                transparente (4);
--                          ESP32_ORIGINAL ...... só o reset remoto (101).
--                        "ultimo_seq" é o contador de comandos V1 (1..255).
--                        "reconciliar" reaplica o estado desejado dos relés
--                        quando o estado reportado diverge (ex.: reboot).
--                        "tarifa_kwh" (R$/kWh) calcula o custo da energia.
--                        "status_json" guarda a última resposta de STATUS.
--  tb_periferico ....... atuadores e sensores ligados ao equipamento.
--                        tipo RELE: "canal" (0..15) e, no Radioenge
--                        original, a GPIO do módulo; "critico" exige
--                        confirmação dupla para acionar; estado desejado
--                        (o que o painel pediu) x reportado (o que o
--                        equipamento informou).
--                        tipo PZEM004T: "canal" (índice do medidor) e
--                        endereço Modbus; "leitura_json" é a última medição.
-- =====================================================================
CREATE TABLE tb_equipamento (
    id                 BIGSERIAL     PRIMARY KEY,
    dispositivo_id     BIGINT        NOT NULL,
    modelo             VARCHAR(30)   NOT NULL,
    ultimo_seq         INTEGER       NOT NULL DEFAULT 0,
    reconciliar        BOOLEAN       NOT NULL DEFAULT TRUE,
    tarifa_kwh         NUMERIC(10,4),
    status_json        TEXT,
    status_em          TIMESTAMP WITH TIME ZONE,
    criado_em          TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por     VARCHAR(160),
    versao             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_tb_equipamento_dispositivo UNIQUE (dispositivo_id),
    CONSTRAINT fk_tb_equipamento_dispositivo FOREIGN KEY (dispositivo_id) REFERENCES tb_dispositivo (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_equipamento_modelo CHECK (modelo IN ('ADAILSILVA_V1', 'RADIOENGE_ORIGINAL', 'ESP32_ORIGINAL')),
    CONSTRAINT ck_tb_equipamento_seq CHECK (ultimo_seq BETWEEN 0 AND 255),
    CONSTRAINT ck_tb_equipamento_tarifa CHECK (tarifa_kwh IS NULL OR tarifa_kwh >= 0)
);

CREATE TABLE tb_periferico (
    id                 BIGSERIAL     PRIMARY KEY,
    equipamento_id     BIGINT        NOT NULL,
    tipo               VARCHAR(20)   NOT NULL,
    canal              INTEGER       NOT NULL,
    nome               VARCHAR(80)   NOT NULL,
    gpio               INTEGER,
    endereco_modbus    INTEGER,
    critico            BOOLEAN       NOT NULL DEFAULT FALSE,
    estado_desejado    BOOLEAN,
    estado_reportado   BOOLEAN,
    reportado_em       TIMESTAMP WITH TIME ZONE,
    leitura_json       TEXT,
    leitura_em         TIMESTAMP WITH TIME ZONE,
    criado_em          TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_tb_periferico_canal UNIQUE (equipamento_id, tipo, canal),
    CONSTRAINT fk_tb_periferico_equipamento FOREIGN KEY (equipamento_id) REFERENCES tb_equipamento (id) ON DELETE CASCADE,
    CONSTRAINT ck_tb_periferico_tipo CHECK (tipo IN ('RELE', 'PZEM004T')),
    CONSTRAINT ck_tb_periferico_canal CHECK (canal BETWEEN 0 AND 15),
    CONSTRAINT ck_tb_periferico_gpio CHECK (gpio IS NULL OR gpio BETWEEN 0 AND 39),
    CONSTRAINT ck_tb_periferico_modbus CHECK (endereco_modbus IS NULL OR endereco_modbus BETWEEN 1 AND 248)
);
-- =====================================================================
--  Fim da V13
-- =====================================================================
