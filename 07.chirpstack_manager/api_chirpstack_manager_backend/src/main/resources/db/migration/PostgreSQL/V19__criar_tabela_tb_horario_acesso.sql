-- =====================================================================
--  V19 - Horário de acesso por dia da semana
--  Mesmo padrão dos demais sistemas: definido pelo ADMIN em
--  Sistema > Horário de acesso; vale para OPERADOR e VISUALIZADOR.
--
--  dia_semana  : ISO, 1 = segunda ... 7 = domingo.
--  permitido   : FALSE = sem acesso no dia inteiro.
--  hora_inicio / hora_fim : janela do dia (permitido = TRUE).
--  Dia sem linha é livre; tabela vazia = nenhuma restrição.
--
--  Fora da janela o login é recusado com o motivo e quando o acesso volta;
--  5 minutos antes do fim o navegador avisa e, no fim, a sessão é
--  encerrada com o motivo FORA_HORARIO.
-- =====================================================================
CREATE TABLE tb_horario_acesso (
    dia_semana      SMALLINT      NOT NULL,
    permitido       BOOLEAN       NOT NULL DEFAULT TRUE,
    hora_inicio     TIME,
    hora_fim        TIME,
    atualizado_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_por  VARCHAR(160),
    CONSTRAINT pk_tb_horario_acesso PRIMARY KEY (dia_semana),
    CONSTRAINT ck_tb_horario_acesso_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_tb_horario_acesso_janela CHECK (
        (permitido = FALSE AND hora_inicio IS NULL AND hora_fim IS NULL)
        OR (permitido = TRUE AND hora_inicio IS NOT NULL AND hora_fim IS NOT NULL AND hora_fim > hora_inicio))
);
-- =====================================================================
--  Fim da V19
-- =====================================================================
