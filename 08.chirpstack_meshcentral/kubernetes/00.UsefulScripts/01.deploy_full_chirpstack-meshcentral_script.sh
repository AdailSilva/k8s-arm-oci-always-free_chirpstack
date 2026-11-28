#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# 01 - Deploy completo do ChirpStack - MeshCentral (suporte remoto self-hosted).
#
# No dia a dia quem implanta e o pipeline (job "ChirpStack - MeshCentral") + ArgoCD
# (Application chirpstack-meshcentral). Este script faz o mesmo a mao, na mesma ordem:
#
#   01 Namespace -> diretorio no FSS (script 05) -> 02 StorageClass/PV/PVC
#   -> Secret (script 03) -> ConfigMap + Deployment (kustomization, = ArgoCD)
#   -> 05 Service -> 06 Ingress
#
# Pre-requisito: DNS A "mesh-chirpstack.adailsilva.com.br" apontando para o IP do NLB.
# Depois do primeiro deploy, crie o administrador com o script 04.
# ═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

K8S="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
U="$K8S/00.UsefulScripts"
NS="chirpstack-meshcentral"

echo ">> Deploy do ChirpStack - MeshCentral"

kubectl apply --server-side -f "$K8S/01.create__Namespace.yaml"
bash "$U/05.garantir_diretorio_fss_chirpstack-meshcentral_script.sh"
kubectl apply --server-side -f "$K8S/02.chirpstack-meshcentral__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml"
kubectl -n "$NS" wait --for=jsonpath='{.status.phase}'=Bound pvc/oci-fss-pvc-chirpstack-meshcentral --timeout=120s
bash "$U/03.gerar_segredos_chirpstack-meshcentral_script.sh"
kubectl apply --server-side -k "$K8S"
kubectl apply --server-side -f "$K8S/05.chirpstack-meshcentral__Service.yaml"
kubectl apply --server-side -f "$K8S/06.chirpstack-meshcentral__Ingress.yaml"

echo ">> Aguardando o MeshCentral ficar pronto..."
kubectl -n "$NS" rollout status deployment/chirpstack-meshcentral-deployment --timeout=600s

echo ""
kubectl -n "$NS" get pods,svc,ingress,certificate,pvc

cat <<'TXT'

>> Pronto: https://mesh-chirpstack.adailsilva.com.br
   O certificado TLS pode levar 1-2 min para ser emitido pelo cert-manager.
   Primeiro acesso: crie o administrador com
     bash 00.UsefulScripts/04.criar_administrador_chirpstack-meshcentral_script.sh
TXT
