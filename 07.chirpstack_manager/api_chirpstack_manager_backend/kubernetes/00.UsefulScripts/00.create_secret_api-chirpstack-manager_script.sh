#!/bin/bash

# ==============================================================================
#  Cria o Secret api-chirpstack-manager-secret.
#  Os valores NUNCA passam pelo Git. Em ordem de prioridade:
#
#    1) Secret ja existe: nada a fazer (as chaves que faltarem vem do 06 e do 07).
#    2) BACKUP feito pelo 02.destroy (~/Apps/OracleCloud/00.Useful/chirpstack_manager_secrets_back_up/api-chirpstack-manager-secret.yaml).
#    3) Nada disso: os scripts 06 e 07 geram os valores (primeira instalacao).
#
#  Idempotente. O 01.deploy_full chama este script antes do 06 e do 07.
# ==============================================================================

set -euo pipefail
NS=chirpstack-manager
SECRET=api-chirpstack-manager-secret
existe() { kubectl -n "$NS" get secret "$SECRET" >/dev/null 2>&1; }
BACKUP_FILE=~/Apps/OracleCloud/00.Useful/chirpstack_manager_secrets_back_up/$SECRET.yaml

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply --server-side -f - >/dev/null

if existe; then
  echo ">> O Secret $SECRET ja existe em $NS. Nada a fazer (chaves: scripts 06 e 07)."
  exit 0
fi

if [ -f "$BACKUP_FILE" ]; then
  echo ">> Backup encontrado: $BACKUP_FILE"
  if [ -t 0 ]; then read -r -p ">> Restaurar o Secret a partir dele? [S/n] " R; else R=S; fi
  if [ "${R:-S}" != "n" ] && [ "${R:-S}" != "N" ]; then
    kubectl apply -f "$BACKUP_FILE"; echo ">> Secret restaurado do backup."; exit 0
  fi
fi
echo ">> Nenhum Secret nem backup: os scripts 06 e 07 vao gerar os valores."
