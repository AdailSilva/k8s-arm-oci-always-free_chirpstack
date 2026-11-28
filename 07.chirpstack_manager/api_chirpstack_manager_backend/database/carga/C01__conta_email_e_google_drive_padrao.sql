-- =============================================================================
-- CARGA GERAL -- C01__conta_email_e_google_drive_padrao.sql
--
-- As credenciais da conta Google de envio e do cliente OAuth do backup ja nascem no
-- banco, para nao precisar cadastra-las pela tela.
--
-- CONTA DE E-MAIL (Sistema -> Conta de e-mail)
--   Senha de app do SMTP (codeinserver@gmail.com). NAO e hash: o servidor de e-mail exige a
--   senha original. Vai como SEMENTE v0:BASE64 porque a chave AES do cluster
--   (APP_CHAVE_CIFRAGEM, gerada pelo 07) e aleatoria e nao existe aqui; na primeira
--   subida o RecifradorSementes a troca pela cifra v1: (AES-256-GCM).
--   Senha ja cadastrada pela tela e preservada.
--
-- GOOGLE DRIVE DO BACKUP (Backup -> Google Drive)
--   Client ID + Client Secret (semente v0:, recifrada na primeira subida). A linha id = 1
--   nasce na V09 so com a pasta; aqui ela recebe o cliente OAuth, SO se ainda nao tiver um.
--   A pasta passa a ser "ChirpStack/Manager", SO se ainda estiver no valor de nascenca e
--   nenhuma conta Google tiver sido conectada (pasta escolhida pela tela e preservada).
--   O REFRESH TOKEN nao pode vir da carga: o Google so o entrega no consentimento, entao
--   falta UM clique em "Conectar conta Google" (logado em codeinserver@gmail.com).
--   No Google Cloud Console, o cliente OAuth precisa ter o redirect do Manager:
--     https://app-chirpstack-manager.adailsilva.com.br/api/v1/backup/google-drive/callback
--     http://localhost:4201/api/v1/backup/google-drive/callback   (development)
--
-- SEGREDOS FORA DO GIT
--   Este repositorio e PUBLICO: os valores NAO ficam neste arquivo. Os marcadores
--   __SEMENTE_SENHA_SMTP__, __GOOGLE_CLIENT_ID__ e __SEMENTE_GOOGLE_CLIENT_SECRET__ sao
--   trocados pelo 05.carga_geral no momento da carga, a partir de variaveis de ambiente
--   (SMTP_SENHA_APP, GOOGLE_DRIVE_CLIENT_ID, GOOGLE_DRIVE_CLIENT_SECRET) ou das anotacoes
--   locais em ~/Apps/OracleCloud/00.Useful. Sem os valores, este arquivo e pulado.
--
-- DIALETO: neutro (H2 e PostgreSQL). Idempotente.
-- =============================================================================

INSERT INTO tb_configuracao_email (id, senha_app_cifrada, atualizado_em, atualizado_por)
SELECT 1, '__SEMENTE_SENHA_SMTP__', CURRENT_TIMESTAMP, 'carga'
 WHERE NOT EXISTS (SELECT 1 FROM tb_configuracao_email c WHERE c.id = 1);

INSERT INTO tb_backup_google_drive (id, pasta_nome, enviar_manuais, retencao_dias, manter_minimo, atualizado_em)
SELECT 1, 'ChirpStack/Manager', FALSE, 30, 5, CURRENT_TIMESTAMP
 WHERE NOT EXISTS (SELECT 1 FROM tb_backup_google_drive g WHERE g.id = 1);

UPDATE tb_backup_google_drive
   SET client_id = '__GOOGLE_CLIENT_ID__',
       client_secret_cifrado = '__SEMENTE_GOOGLE_CLIENT_SECRET__',
       atualizado_em = CURRENT_TIMESTAMP,
       atualizado_por = 'carga'
 WHERE id = 1
   AND client_id IS NULL
   AND client_secret_cifrado IS NULL;

UPDATE tb_backup_google_drive
   SET pasta_nome = 'ChirpStack/Manager',
       pasta_id = NULL
 WHERE id = 1
   AND pasta_nome = 'ChirpStack Manager/Backups'
   AND refresh_token_cifrado IS NULL;
