#!/usr/bin/env bash
# =============================================================================
# 03 - Segredos do ChirpStack - MeshCentral
# =============================================================================
# Mantem FORA do Git a chave de sessao do MeshCentral:
#
#   SESSION_KEY -> Secret "chirpstack-meshcentral-secret" (namespace chirpstack-meshcentral)
#                  . o initContainer do 04.chirpstack-meshcentral__Deployment.yaml a grava
#                    no config.json na PRIMEIRA subida (troca "__SESSION_KEY__")
#                  . depois disso o config.json do volume e a fonte: trocar o Secret
#                    nao altera um MeshCentral ja inicializado
#
# Uso:
#   bash 03.gerar_segredos_chirpstack-meshcentral_script.sh            # cria se faltar (ou se for fraco)
#   bash 03.gerar_segredos_chirpstack-meshcentral_script.sh --conferir # so confere, nao altera
#
# "Fraco" = ausente ou com menos de 32 caracteres. Nenhum segredo e impresso.
# Na pipeline (GITHUB_ACTIONS=true) o valor e mascarado.
# =============================================================================
set -euo pipefail

NS="chirpstack-meshcentral"
SECRET="chirpstack-meshcentral-secret"
MINIMO=32

command -v kubectl >/dev/null || { echo "ERRO: kubectl nao encontrado."; exit 1; }

mascarar() { [ "${GITHUB_ACTIONS:-}" = "true" ] && [ -n "$1" ] && echo "::add-mask::$1" || true; }

# (sem `head` no fim do pipe: com pipefail, o SIGPIPE do tr derrubaria o script)
gerar() { local S; S=$(head -c 192 /dev/urandom | base64 -w0 | tr -dc 'A-Za-z0-9'); printf '%s' "${S:0:64}"; }

ler() {
  local V
  V=$(kubectl -n "$NS" get secret "$SECRET" -o go-template='{{ index .data "SESSION_KEY" }}' 2>/dev/null) || V=""
  { [ -z "$V" ] || [ "$V" = "<no value>" ]; } && { printf ''; return; }
  printf '%s' "$V" | base64 -d
}

kubectl get namespace "$NS" >/dev/null 2>&1 || { echo "ERRO: namespace $NS nao existe (aplique 01.create__Namespace.yaml)."; exit 1; }

ATUAL=$(ler)
mascarar "$ATUAL"

if [ "${1:-}" = "--conferir" ]; then
  if [ "${#ATUAL}" -ge "$MINIMO" ]; then echo ">> $SECRET: SESSION_KEY ok (${#ATUAL} caracteres)."; exit 0; fi
  echo ">> $SECRET: SESSION_KEY ausente ou fraco."; exit 1
fi

if [ "${#ATUAL}" -ge "$MINIMO" ]; then
  echo ">> $SECRET: SESSION_KEY ja existe -- mantido."
  exit 0
fi

NOVO=$(gerar)
mascarar "$NOVO"
kubectl -n "$NS" create secret generic "$SECRET" --from-literal=SESSION_KEY="$NOVO" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null
unset NOVO
echo ">> $SECRET: SESSION_KEY gerado (64 caracteres)."
