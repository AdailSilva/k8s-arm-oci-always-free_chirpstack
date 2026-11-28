#!/usr/bin/env bash
# =============================================================================
# 05 - Diretorio do ChirpStack - MeshCentral no OCI FSS
# =============================================================================
# O PV (02.*) monta /FileSystem-K8S/chirpstack-meshcentral-data, que precisa existir
# no FSS antes do primeiro pod. Este script o cria com um pod temporario que
# monta a raiz do FSS -- sem precisar de SSH num no. Idempotente.
#
# Uso: bash 05.garantir_diretorio_fss_chirpstack-meshcentral_script.sh
# =============================================================================
set -euo pipefail

NS="chirpstack-meshcentral"
FSS_SERVIDOR="10.0.0.143"
FSS_RAIZ="/FileSystem-K8S"
DIRETORIO="chirpstack-meshcentral-data"
POD="chirpstack-meshcentral-preparar-fss"

command -v kubectl >/dev/null || { echo "ERRO: kubectl nao encontrado."; exit 1; }
kubectl get namespace "$NS" >/dev/null 2>&1 || { echo "ERRO: namespace $NS nao existe (aplique 01.create__Namespace.yaml)."; exit 1; }

kubectl -n "$NS" delete pod "$POD" --ignore-not-found --wait=true >/dev/null

SOBREPOR=$(cat <<JSON
{"spec":{"restartPolicy":"Never",
  "containers":[{"name":"preparar","image":"busybox:1.36",
    "command":["sh","-c","mkdir -p /fss/$DIRETORIO && ls -ld /fss/$DIRETORIO"],
    "volumeMounts":[{"name":"fss","mountPath":"/fss"}]}],
  "volumes":[{"name":"fss","nfs":{"server":"$FSS_SERVIDOR","path":"$FSS_RAIZ"}}]}}
JSON
)

echo ">> Garantindo $FSS_RAIZ/$DIRETORIO no FSS..."
if ! kubectl -n "$NS" run "$POD" --image=busybox:1.36 --restart=Never --rm -i --quiet \
       --pod-running-timeout=3m --overrides="$SOBREPOR" 2>&1; then
  echo "ERRO: nao foi possivel preparar o diretorio no FSS."
  kubectl -n "$NS" get events --sort-by=.lastTimestamp 2>/dev/null | tail -5 || true
  kubectl -n "$NS" delete pod "$POD" --ignore-not-found >/dev/null 2>&1 || true
  exit 1
fi
echo ">> Diretorio $FSS_RAIZ/$DIRETORIO pronto."
