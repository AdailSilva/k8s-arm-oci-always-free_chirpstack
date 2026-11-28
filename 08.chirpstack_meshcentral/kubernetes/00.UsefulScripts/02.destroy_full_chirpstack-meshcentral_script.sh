#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# 02 - Remove o ChirpStack - MeshCentral do cluster, MANTENDO os dados.
#
# Remove Ingress, Service, Deployment e ConfigMap. O PVC, o PV (Retain), o Secret
# e o namespace ficam -- um novo deploy volta com os mesmos dados e a mesma chave.
#
# Se a Application chirpstack-meshcentral estiver ativa no ArgoCD (auto-sync), ela
# recria o Deployment e o ConfigMap: pause o namespace
# (bash ~/Apps/OracleCloud/00.Useful/05_ns_toggle.sh chirpstack-meshcentral pause) ou
# retire a Application do app-of-apps antes.
# ═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

K8S="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NS="chirpstack-meshcentral"

kubectl delete -f "$K8S/06.chirpstack-meshcentral__Ingress.yaml" --ignore-not-found
kubectl delete -f "$K8S/05.chirpstack-meshcentral__Service.yaml" --ignore-not-found
kubectl delete -k "$K8S" --ignore-not-found

# Remocao TOTAL (apaga os dados do namespace; o diretorio no FSS continua, PV Retain):
# kubectl -n "$NS" delete secret chirpstack-meshcentral-secret --ignore-not-found
# kubectl delete -f "$K8S/02.chirpstack-meshcentral__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml" --ignore-not-found
# kubectl delete -f "$K8S/01.create__Namespace.yaml" --ignore-not-found

echo ">> ChirpStack - MeshCentral removido (dados preservados no FSS e no PVC)."
