#!/bin/bash

# ChirpStack-Manager destruction on K8S (ordem inversa do deploy).
#
# OS DADOS NAO SAO APAGADOS: os PVs usam Retain; banco e backups continuam no FSS
# (/FileSystem-K8S/chirpstack-manager-postgresql-data e /FileSystem-K8S/chirpstack-manager-data).
#
# ATENCAO: apagar o Namespace apaga junto o Secret api-chirpstack-manager-secret.
# Ele e salvo antes (abaixo) e o 00.create_secret o restaura no proximo deploy.
# O namespace e COMPARTILHADO com o frontend: o 01.deploy do backend reaplica o
# Service e o Ingress do frontend; o Deployment do frontend volta pelo ArgoCD.

set -e

K8S_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/07.chirpstack_manager/api_chirpstack_manager_backend/kubernetes
SECRET_BACKUP_DIR=~/Apps/OracleCloud/00.Useful/chirpstack_manager_secrets_back_up

echo "Start destroying ChirpStack-Manager pod/service."

kubectl delete -f $K8S_DIR/11.api-chirpstack-manager-backend__Service.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/10.api-chirpstack-manager-backend__Deployment.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/08.api-chirpstack-manager-backend__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/07.api-chirpstack-manager-migration__Job.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/06.api-chirpstack-manager-postgresql__Service.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/05.api-chirpstack-manager-postgresql__Deployment.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/04.api-chirpstack-manager-postgresql__ConfigMap.yaml --ignore-not-found
kubectl delete -f $K8S_DIR/03.api-chirpstack-manager-postgresql__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml --ignore-not-found

if kubectl -n chirpstack-manager get secret api-chirpstack-manager-secret >/dev/null 2>&1; then
  mkdir -p "$SECRET_BACKUP_DIR" && chmod 700 "$SECRET_BACKUP_DIR"
  ( umask 077; kubectl -n chirpstack-manager get secret api-chirpstack-manager-secret -o yaml \
      | grep -v -E '^\s+(resourceVersion|uid|creationTimestamp):' > "$SECRET_BACKUP_DIR/api-chirpstack-manager-secret.yaml" )
  echo ">> Backup do Secret em $SECRET_BACKUP_DIR/api-chirpstack-manager-secret.yaml"
fi

kubectl delete -f $K8S_DIR/01.create__Namespace.yaml --ignore-not-found

echo "Successfully completed destruction of ChirpStack-Manager pod/service."
