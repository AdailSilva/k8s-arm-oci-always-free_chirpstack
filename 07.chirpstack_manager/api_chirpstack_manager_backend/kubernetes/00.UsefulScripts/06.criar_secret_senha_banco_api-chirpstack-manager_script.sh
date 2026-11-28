#!/usr/bin/env bash
# =============================================================================
# 06 - Senhas do banco do ChirpStack Manager
# =============================================================================
#   POSTGRESQL_PASSWORD          senha do chirpstack_manager_role
#                                  . 04 (init do volume) cria o role com ela
#                                  . Job 07 realinha o role a ela a cada execucao
#                                  . 10 (API) conecta com ela (DB_PASSWORD)
#   POSTGRES_SUPERUSER_PASSWORD  senha do superusuario postgres
#                                  . 05 (POSTGRES_PASSWORD, so na 1a subida do volume)
#                                  . Job 07 (PGPASSWORD)
#
# Uso:
#   bash 06.criar_secret_senha_banco_api-chirpstack-manager_script.sh                       # gera o que faltar
#   bash 06.criar_secret_senha_banco_api-chirpstack-manager_script.sh --mostrar             # imprime as senhas
#   bash 06.criar_secret_senha_banco_api-chirpstack-manager_script.sh --trocar              # senha NOVA do role (o Job 07 aplica)
#   bash 06.criar_secret_senha_banco_api-chirpstack-manager_script.sh --trocar-superusuario # senha NOVA do postgres, aplicada no banco
#
# POR QUE A SENHA DO SUPERUSUARIO NAO E TROCADA SOZINHA: o PostgreSQL so le
# POSTGRES_PASSWORD na primeira inicializacao do volume. Com dados no volume, a
# chave do Secret precisa continuar igual -- o --trocar-superusuario aplica a nova
# pelo socket local do pod (ALTER ROLE) antes de grava-la no Secret.
# =============================================================================
set -euo pipefail
NS=chirpstack-manager
SECRET=api-chirpstack-manager-secret
existe() { kubectl -n "$NS" get secret "$SECRET" >/dev/null 2>&1; }
ler()    { kubectl -n "$NS" get secret "$SECRET" -o jsonpath="{.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }
gravar() { [ "${GITHUB_ACTIONS:-}" = "true" ] && [ -n "$2" ] && echo "::add-mask::$2"
           kubectl -n "$NS" patch secret "$SECRET" --type merge -p "{\"stringData\":{\"$1\":\"$2\"}}" >/dev/null; }
gerar()  { head -c 192 /dev/urandom | base64 -w0 | tr -dc 'A-Za-z0-9' | cut -c1-"$1"; }
garantir_secret() {
  kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply --server-side -f - >/dev/null
  existe || kubectl -n "$NS" create secret generic "$SECRET" >/dev/null
}

garantir_secret
case "${1:-}" in
  --mostrar)
    echo "chirpstack_manager_role: $(ler POSTGRESQL_PASSWORD)"
    echo "postgres               : $(ler POSTGRES_SUPERUSER_PASSWORD)"
    exit 0 ;;
  --trocar)
    gravar POSTGRESQL_PASSWORD "$(gerar 32)"
    echo ">> POSTGRESQL_PASSWORD nova. Rode o deploy (Job 07 realinha o role; a API reinicia)."
    exit 0 ;;
  --trocar-superusuario)
    NOVA=$(gerar 32)
    POD=$(kubectl -n "$NS" get pod -l app=api-chirpstack-manager-postgresql-deployment \
          --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
    [ -n "$POD" ] || { echo "ERRO: PostgreSQL fora do ar -- a senha precisa ser aplicada no banco."; exit 1; }
    kubectl -n "$NS" exec "$POD" -- psql -U postgres -d postgres -qc "ALTER ROLE postgres PASSWORD '$NOVA'"
    gravar POSTGRES_SUPERUSER_PASSWORD "$NOVA"
    echo ">> Senha do postgres trocada no banco e no Secret."
    exit 0 ;;
esac

for CH in POSTGRESQL_PASSWORD POSTGRES_SUPERUSER_PASSWORD; do
  V=$(ler "$CH")
  if [ "${#V}" -lt 24 ]; then
    gravar "$CH" "$(gerar 32)"; echo ">> $CH ausente ou fraca: senha forte gerada."
  else
    [ "${GITHUB_ACTIONS:-}" = "true" ] && echo "::add-mask::$V"; echo ">> $CH presente (mantida)."
  fi
done
