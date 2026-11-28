#!/bin/bash

# Build Project
# docker buildx build --platform 'linux/arm64' --tag gru.ocir.io/griszz3l82u1/api-chirpstack-manager-backend_platform_linux-arm64:latest --no-cache --push .


# ChirpStack-Manager deployment on K8S.
#
# Ordem de aplicacao:
# Namespace -> Secret -> PostgreSQL (volume, ConfigMap, Deployment, Service) ->
# espera o banco -> Job de migracao -> volume de backups -> backend -> Service.
#
# Numeros AUSENTES neste sistema (componente nao existe aqui):
#   09  RBAC do backup -- o Backup & Restore da API roda pg_dump/psql pela rede,
#       com o proprio role (postgresql17-client na imagem), sem `kubectl exec`.
#   12-14 cert-manager/Issuer/Ingress da API -- a API e publicada pelo NGINX do
#       frontend (/api -> api-chirpstack-manager-backend-service:8083).

set -e

K8S_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/07.chirpstack_manager/api_chirpstack_manager_backend/kubernetes
NS=chirpstack-manager

# ─── Vinculo PV <-> PVC (mesma logica do pipeline) ─────────────────────
# PV e recurso de CLUSTER e usa persistentVolumeReclaimPolicy: Retain.
# Quando o PVC e apagado (namespace removido/recriado, limpeza manual)
# o PV vai para Released e continua com spec.claimRef apontando para
# o PVC ANTIGO (mesmo nome, outro UID). O PVC novo fica Pending e o pod
# do Postgres fica Pending com "unbound immediate PersistentVolumeClaims".
#   - claimRef aponta para um PVC que AINDA EXISTE com aquele UID
#     (instancia viva em outro lugar) -> ERRO, nao mexe em nada;
#   - claimRef obsoleto (dono nao existe mais) -> remove o claimRef.
#     Os dados no FSS NAO sao tocados (Retain + NFS);
#   - depois espera o PVC ficar Bound (ate 120s).
garantir_vinculo_pv() {
  local PV="$1" PVC="$2"
  local REF_NS REF_NOME REF_UID UID_PVC UID_DONO FASE
  REF_NS=$(kubectl get pv "$PV" -o jsonpath='{.spec.claimRef.namespace}')
  REF_NOME=$(kubectl get pv "$PV" -o jsonpath='{.spec.claimRef.name}')
  REF_UID=$(kubectl get pv "$PV" -o jsonpath='{.spec.claimRef.uid}')
  UID_PVC=$(kubectl get pvc "$PVC" -n "$NS" -o jsonpath='{.metadata.uid}')

  if [ -n "$REF_NOME" ]; then
    if [ "$REF_NS" = "$NS" ] && [ "$REF_NOME" = "$PVC" ] \
       && { [ -z "$REF_UID" ] || [ "$REF_UID" = "$UID_PVC" ]; }; then
      : # ja aponta para o PVC atual
    else
      UID_DONO=$(kubectl get pvc "$REF_NOME" -n "$REF_NS" \
        -o jsonpath='{.metadata.uid}' 2>/dev/null || true)
      if [ -n "$UID_DONO" ] && { [ -z "$REF_UID" ] || [ "$UID_DONO" = "$REF_UID" ]; }; then
        echo "===================================================================="
        echo ">> ERRO: PV $PV esta em uso pelo PVC $REF_NS/$REF_NOME (ainda existe)."
        echo ">> Dois PVCs no mesmo diretorio do FSS = risco de corromper o banco."
        echo ">> Confirme que nada usa o antigo e remova-o:"
        echo ">>   kubectl get pods -n $REF_NS"
        echo ">>   kubectl delete pvc $REF_NOME -n $REF_NS"
        echo "===================================================================="
        return 1
      fi
      echo ">> PV $PV preso ao claim obsoleto $REF_NS/$REF_NOME (uid ${REF_UID:-?})."
      echo ">> Removendo claimRef para o PVC $NS/$PVC vincular (dados preservados)."
      kubectl patch pv "$PV" --type=json -p '[{"op":"remove","path":"/spec/claimRef"}]'
    fi
  fi

  for _ in $(seq 1 24); do
    FASE=$(kubectl get pvc "$PVC" -n "$NS" -o jsonpath='{.status.phase}')
    if [ "$FASE" = "Bound" ]; then
      echo ">> PVC $NS/$PVC Bound em $PV."
      return 0
    fi
    sleep 5
  done
  echo ">> ERRO: PVC $NS/$PVC nao ficou Bound em 120s."
  kubectl describe pv "$PV" || true
  kubectl describe pvc "$PVC" -n "$NS" || true
  return 1
}

echo "Start deploying the ChirpStack-Manager pod/service."

# 01 -- Namespace.
kubectl apply -f $K8S_DIR/01.create__Namespace.yaml

# 02 -- Secret unico (modelo comentado; valores pelos scripts 00, 06 e 07).
bash $K8S_DIR/00.UsefulScripts/00.create_secret_api-chirpstack-manager_script.sh
bash $K8S_DIR/00.UsefulScripts/06.criar_secret_senha_banco_api-chirpstack-manager_script.sh
bash $K8S_DIR/00.UsefulScripts/07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh

for chave in POSTGRES_SUPERUSER_PASSWORD POSTGRESQL_PASSWORD APP_JWT_SECRET APP_ADMIN_PASSWORD APP_CHAVE_CIFRAGEM; do
  if [ -z "$(kubectl -n $NS get secret api-chirpstack-manager-secret -o jsonpath="{.data.$chave}" 2>/dev/null)" ]; then
    echo ">> ERRO: o Secret api-chirpstack-manager-secret nao tem a chave $chave (scripts 06/07)."
    exit 1
  fi
done

if ! kubectl -n $NS get secret oci-registry-secret >/dev/null 2>&1; then
  echo ">> AVISO: oci-registry-secret nao existe em $NS -- o pull da imagem do backend vai falhar (ImagePullBackOff)."
fi

# 03 a 06 -- PostgreSQL. Pre-requisito do 03: /FileSystem-K8S/chirpstack-manager-postgresql-data no FSS.
kubectl apply -f $K8S_DIR/03.api-chirpstack-manager-postgresql__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml
garantir_vinculo_pv oci-fss-pv-postgresql-data-chirpstack-manager oci-fss-pvc-postgresql-data-chirpstack-manager

kubectl apply -f $K8S_DIR/04.api-chirpstack-manager-postgresql__ConfigMap.yaml
kubectl apply -f $K8S_DIR/05.api-chirpstack-manager-postgresql__Deployment.yaml
kubectl apply -f $K8S_DIR/06.api-chirpstack-manager-postgresql__Service.yaml

echo "Waiting for Postgres Service to become available to start ChirpStack-Manager Service..."
kubectl -n $NS rollout status deployment/api-chirpstack-manager-postgresql-deployment --timeout=600s
kubectl wait --namespace $NS --for=condition=ready pod --selector=app=api-chirpstack-manager-postgresql-deployment --timeout=300s

# 07 -- Job de preparacao do banco (role, database, dono do schema; REBUILD opcional).
#       As tabelas vem do Flyway da API no boot (ver o cabecalho do 07).
kubectl -n $NS delete job api-chirpstack-manager-db-migration --ignore-not-found --wait=true
kubectl apply -f $K8S_DIR/07.api-chirpstack-manager-migration__Job.yaml

echo "Waiting for the migration Job to complete..."
if ! kubectl -n $NS wait --for=condition=complete job/api-chirpstack-manager-db-migration --timeout=600s; then
  echo ">> ERRO: o Job de migracao nao concluiu. Logs:"
  for POD in $(kubectl -n $NS get pods --selector=job-name=api-chirpstack-manager-db-migration -o name </dev/null 2>/dev/null); do
    echo ">> ===== $POD ====="; kubectl -n $NS logs "$POD" --all-containers=true --tail=200 </dev/null || true
  done
  exit 1
fi

# 08 -- Volume de backups. Pre-requisito: /FileSystem-K8S/chirpstack-manager-data no FSS.
kubectl apply -f $K8S_DIR/08.api-chirpstack-manager-backend__StorageClass__PersistentVolume__PersistentVolumeClaim.yaml
garantir_vinculo_pv oci-fss-pv-chirpstack-manager oci-fss-pvc-chirpstack-manager

# 10 e 11 -- Backend (pela kustomization, como o ArgoCD) e Service.
kubectl apply -k $K8S_DIR
kubectl -n $NS rollout restart deployment/api-chirpstack-manager-backend-deployment
kubectl apply -f $K8S_DIR/11.api-chirpstack-manager-backend__Service.yaml

# Frontend no MESMO namespace: Service e Ingress (o ArgoCD so recria o Deployment).
K8S_FRONT_DIR=~/Apps/OracleCloud/02.k8s-on-arm-oci-always-free_chirpstack/07.chirpstack_manager/app_chirpstack_manager_frontend/kubernetes
kubectl apply -f $K8S_FRONT_DIR/03.app-chirpstack-manager-frontend__Service.yaml
kubectl apply -f $K8S_FRONT_DIR/06.app-chirpstack-manager__Ingress.yaml
kubectl apply -f $K8S_FRONT_DIR/07.app-chirpstack-manager-restauracao__Ingress.yaml

echo "Waiting for the ChirpStack-Manager backend..."
kubectl -n $NS rollout status deployment/api-chirpstack-manager-backend-deployment --timeout=600s
echo "Successfully completed deployment of ChirpStack-Manager pod/service."
echo ">> Admin inicial (so com banco vazio): admin@chirpstack.com.br com a senha inicial do README; troca obrigatoria no primeiro acesso."
