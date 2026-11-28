-- =============================================================================
-- CARGA GERAL -- C02__regras_alerta_padrao.sql
--
-- Regras de alerta iniciais, valendo para TODOS os dispositivos (dispositivo_id NULO),
-- ativas e sem e-mail (ligue a notificacao e os destinatarios pela tela Alertas -> Regras):
--   * Sem comunicacao ........ nenhuma mensagem ha mais de 60 minutos
--   * Bateria baixa .......... bateria (evento status) abaixo de 20 %
--   * Sinal fraco ............ RSSI do melhor gateway abaixo de -115 dBm
-- So entram se ainda nao existir uma regra com o mesmo nome.
--
-- DIALETO: neutro (H2 e PostgreSQL). Idempotente.
-- =============================================================================

INSERT INTO tb_regra_alerta (nome, tipo, dispositivo_id, campo, operador, limite, severidade, notificar_email,
                             destinatarios, ativo, criado_em, atualizado_em, atualizado_por)
SELECT 'Sem comunicação', 'SEM_COMUNICACAO', NULL, NULL, NULL, 60, 'AVISO', FALSE, NULL, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'carga'
 WHERE NOT EXISTS (SELECT 1 FROM tb_regra_alerta r WHERE r.nome = 'Sem comunicação');

INSERT INTO tb_regra_alerta (nome, tipo, dispositivo_id, campo, operador, limite, severidade, notificar_email,
                             destinatarios, ativo, criado_em, atualizado_em, atualizado_por)
SELECT 'Bateria baixa', 'VALOR', NULL, 'bateria', 'LT', 20, 'AVISO', FALSE, NULL, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'carga'
 WHERE NOT EXISTS (SELECT 1 FROM tb_regra_alerta r WHERE r.nome = 'Bateria baixa');

INSERT INTO tb_regra_alerta (nome, tipo, dispositivo_id, campo, operador, limite, severidade, notificar_email,
                             destinatarios, ativo, criado_em, atualizado_em, atualizado_por)
SELECT 'Sinal fraco', 'VALOR', NULL, 'rssi', 'LT', -115, 'INFO', FALSE, NULL, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'carga'
 WHERE NOT EXISTS (SELECT 1 FROM tb_regra_alerta r WHERE r.nome = 'Sinal fraco');
