#!/bin/bash
# ==============================================================================
#  CARGA GERAL -- 07.chirpstack_manager
#  Dados iniciais do ChirpStack Manager (database/carga/C01..) sobre a estrutura
#  criada pelo Flyway (V01..) no boot da aplicação.
# ==============================================================================
#
#  USO (comando único):
#
#    bash kubernetes/00.UsefulScripts/05.carga_geral_api-chirpstack-manager_script.sh --ambiente=production
#    bash kubernetes/00.UsefulScripts/05.carga_geral_api-chirpstack-manager_script.sh --ambiente=staging
#    bash kubernetes/00.UsefulScripts/05.carga_geral_api-chirpstack-manager_script.sh --ambiente=development
#
#  O QUE ENTRA
#    C01 conta de e-mail (senha de app do Google) e cliente OAuth do Google Drive
#        (sementes v0:, recifradas pela aplicação na 1ª subida)
#        e pasta padrão do backup no Drive (ChirpStack/Manager)
#    C02 regras de alerta padrão do monitoramento (sem comunicação, bateria baixa, sinal fraco)
#    O administrador NÃO vem da carga: a aplicação o cria no 1º boot com o banco vazio
#    (admin@chirpstack.com.br / chirpstack, troca obrigatória no primeiro acesso).
#
#  SEGREDOS FORA DO GIT (o repositório é público)
#    O C01 tem marcadores no lugar dos valores. Este script os preenche, nesta ordem:
#      - senha de app do SMTP: só pela variável SMTP_SENHA_APP
#        (ex.: SMTP_SENHA_APP='abcd efgh ijkl mnop' bash 05.carga_geral... --ambiente=...);
#      - cliente OAuth: variáveis GOOGLE_DRIVE_CLIENT_ID e GOOGLE_DRIVE_CLIENT_SECRET ou,
#        na falta delas, o JSON da anotação local NOTAS_DIR/21_google_drive_backup.txt
#        (NOTAS_DIR padrão: ~/Apps/OracleCloud/00.Useful).
#    Sem os valores, o C01 é pulado (com aviso) e o resto da carga segue.
#    Os valores nunca são impressos.
#
#  PRÉ-REQUISITOS
#    production  -> kubectl com acesso ao namespace chirpstack-manager e a API já
#                   tendo subido UMA vez (o Flyway cria as tabelas no boot). O psql roda
#                   dentro do pod do PostgreSQL, pelo socket local, como postgres.
#    staging     -> psql local; banco em PG_URL_STAGING
#                   (padrão postgresql://postgres:chirpstack-manager@localhost:15435/chirpstack_manager),
#                   com a aplicação já tendo subido uma vez.
#    development -> H2 em arquivo (./data/chirpstack-manager, relativo ao backend), com a
#                   aplicação PARADA (o arquivo fica travado enquanto ela roda). java no PATH
#                   e o h2-*.jar no ~/.m2 (vem do build) ou em H2_JAR.
#
#  REGRAS
#    - Idempotente: repetir não duplica nada e não sobrescreve o que a tela já gravou.
#    - Uma ÚNICA transação: qualquer erro desfaz tudo.
#    - C*.sql de dialeto neutro (H2 e PostgreSQL); novos arquivos entram sozinhos.
# ==============================================================================
set -euo pipefail

AMBIENTE=""
for arg in "$@"; do
  case "$arg" in
    --ambiente=*) AMBIENTE="${arg#*=}" ;;
    -h|--help) sed -n '2,47p' "$0"; exit 0 ;;
    *) echo "Argumento desconhecido: $arg"; exit 1 ;;
  esac
done
case "$AMBIENTE" in production|staging|development) ;; *) echo "Informe --ambiente=production|staging|development"; exit 1 ;; esac

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$(cd "$AQUI/../.." && pwd)"
CARGA="$BACKEND/database/carga"
NS=chirpstack-manager
DB=chirpstack_manager_database
NOTAS_DIR="${NOTAS_DIR:-$HOME/Apps/OracleCloud/00.Useful}"

# ── Segredos (nunca impressos) ────────────────────────────────────────────────
SEGREDOS=$(NOTAS_DIR="$NOTAS_DIR" python3 -I - <<'PY'
import os, re, base64, json, sys
d = os.environ.get("NOTAS_DIR", "")
smtp = os.environ.get("SMTP_SENHA_APP", "").replace(" ", "")
cid = os.environ.get("GOOGLE_DRIVE_CLIENT_ID", "")
sec = os.environ.get("GOOGLE_DRIVE_CLIENT_SECRET", "")
try:
    if not cid or not sec:
        t = open(os.path.join(d, "21_google_drive_backup.txt"), encoding="utf-8").read()
        cid = cid or (re.findall(r'"client_id"\s*:\s*"([^"]+)"', t) or [""])[0]
        sec = sec or (re.findall(r'"client_secret"\s*:\s*"([^"]+)"', t) or [""])[0]
except OSError:
    pass
v0 = lambda x: "v0:" + base64.b64encode(x.encode()).decode() if x else ""
print(json.dumps({"smtp": v0(smtp), "cid": cid, "sec": v0(sec)}))
PY
)
SEMENTE_SMTP=$(printf '%s' "$SEGREDOS" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["smtp"])')
GOOGLE_CID=$(printf '%s' "$SEGREDOS" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["cid"])')
SEMENTE_SECRET=$(printf '%s' "$SEGREDOS" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["sec"])')

# ── Monta o SQL (uma transação) ───────────────────────────────────────────────
SQL=$(mktemp); trap 'rm -f "$SQL"' EXIT
chmod 600 "$SQL"
echo "BEGIN;" > "$SQL"
for F in $(ls "$CARGA"/C*.sql | sort -V); do
  NOME=$(basename "$F")
  if grep -q '__SEMENTE_\|__GOOGLE_' "$F"; then
    if [ -z "$SEMENTE_SMTP" ] || [ -z "$GOOGLE_CID" ] || [ -z "$SEMENTE_SECRET" ]; then
      echo ">> AVISO: $NOME pulado -- senha de app do SMTP (SMTP_SENHA_APP) e/ou cliente OAuth do Google nao encontrados"
      echo "           (defina SMTP_SENHA_APP, GOOGLE_DRIVE_CLIENT_ID, GOOGLE_DRIVE_CLIENT_SECRET ou NOTAS_DIR)."
      continue
    fi
    sed -e "s|__SEMENTE_SENHA_SMTP__|$SEMENTE_SMTP|g" -e "s|__GOOGLE_CLIENT_ID__|$GOOGLE_CID|g" \
        -e "s|__SEMENTE_GOOGLE_CLIENT_SECRET__|$SEMENTE_SECRET|g" "$F" >> "$SQL"
  else
    cat "$F" >> "$SQL"
  fi
  echo ">> $NOME"
  echo "" >> "$SQL"
done
echo "COMMIT;" >> "$SQL"

RESUMO="SELECT 'conta de e-mail' AS item, COUNT(*) AS qtd FROM tb_configuracao_email
UNION ALL SELECT 'cliente OAuth do Drive', COUNT(*) FROM tb_backup_google_drive WHERE client_id IS NOT NULL
UNION ALL SELECT 'regras de alerta', COUNT(*) FROM tb_regra_alerta
UNION ALL SELECT 'usuarios', COUNT(*) FROM tb_usuario;"

# ── Aplica ────────────────────────────────────────────────────────────────────
case "$AMBIENTE" in
  production)
    POD=$(kubectl -n "$NS" get pod -l app=api-chirpstack-manager-postgresql-deployment \
          -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
    [ -n "$POD" ] || { echo "ERRO: pod do PostgreSQL nao encontrado em $NS (namespace pausado?)."; exit 1; }
    kubectl -n "$NS" exec "$POD" -- psql -U postgres -d "$DB" -tAc \
      "SELECT 1 FROM information_schema.tables WHERE table_name = 'tb_regra_alerta'" | grep -q 1 \
      || { echo "ERRO: estrutura ausente -- suba a API uma vez (o Flyway cria as tabelas) e repita."; exit 1; }
    kubectl -n "$NS" exec -i "$POD" -- psql -v ON_ERROR_STOP=1 -q -U postgres -d "$DB" < "$SQL"
    kubectl -n "$NS" exec "$POD" -- psql -U postgres -d "$DB" -c "$RESUMO"
    ;;
  staging)
    URL="${PG_URL_STAGING:-postgresql://postgres:chirpstack-manager@localhost:15435/chirpstack_manager}"
    psql "$URL" -v ON_ERROR_STOP=1 -q -f "$SQL"
    psql "$URL" -c "$RESUMO"
    ;;
  development)
    H2_JAR="${H2_JAR:-$(ls "$HOME"/.m2/repository/com/h2database/h2/*/h2-*.jar 2>/dev/null | grep -v -- -sources | sort -V | tail -1)}"
    [ -f "$H2_JAR" ] || { echo "ERRO: h2-*.jar nao encontrado (rode o build do backend ou defina H2_JAR)."; exit 1; }
    URL="jdbc:h2:file:$BACKEND/data/chirpstack-manager;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;IFEXISTS=TRUE"
    sed -i -e '/^BEGIN;$/d' -e '/^COMMIT;$/d' "$SQL"
    java -cp "$H2_JAR" org.h2.tools.RunScript -url "$URL" -user sa -script "$SQL" \
      || { echo "ERRO: a aplicacao precisa estar PARADA e ter subido ao menos uma vez."; exit 1; }
    java -cp "$H2_JAR" org.h2.tools.Shell -url "$URL" -user sa -sql "$RESUMO"
    ;;
esac
echo ">> Carga geral concluida ($AMBIENTE)."
echo ">> Falta so um clique: Sistema > Backup > Google Drive > \"Conectar conta Google\" (codeinserver@gmail.com)."
