#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════════
# 04 - Cria (ou promove) o administrador do ChirpStack - MeshCentral.
#
# O config.json tem "newAccounts": false (sem auto-cadastro): o primeiro usuario
# e criado por aqui, direto no pod. Rode uma vez, depois do primeiro deploy.
#
# Uso:
#   bash 04.criar_administrador_chirpstack-meshcentral_script.sh                 # usuario "admin"
#   MESH_ADMIN_USUARIO=suporte bash 04.criar_administrador_chirpstack-meshcentral_script.sh
#
# A senha e pedida no terminal (nao aparece na tela nem no historico). Para
# automatizar, passe-a em MESH_ADMIN_SENHA. Minimo: 8 caracteres.
# ═══════════════════════════════════════════════════════════════════════════════
set -euo pipefail

NS="chirpstack-meshcentral"
USUARIO="${MESH_ADMIN_USUARIO:-admin}"
SENHA="${MESH_ADMIN_SENHA:-}"
MESH="node /opt/meshcentral/meshcentral/meshcentral --configfile /opt/meshcentral/meshcentral-data/config.json"

POD=$(kubectl -n "$NS" get pod -l app=chirpstack-meshcentral-deployment \
        --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
[ -n "$POD" ] || { echo "ERRO: nenhum pod do MeshCentral rodando em $NS (rode o deploy antes)."; exit 1; }

if [ -z "$SENHA" ]; then
  read -r -s -p "Senha para o usuario '$USUARIO': " SENHA; echo
  read -r -s -p "Repita a senha: " CONF; echo
  [ "$SENHA" = "$CONF" ] || { echo "ERRO: as senhas nao conferem."; exit 1; }
fi
[ "${#SENHA}" -ge 8 ] || { echo "ERRO: a senha precisa de ao menos 8 caracteres."; exit 1; }

echo ">> Criando a conta '$USUARIO' no pod $POD..."
kubectl -n "$NS" exec "$POD" -- sh -c "$MESH --createaccount \"\$1\" --pass \"\$2\"" _ "$USUARIO" "$SENHA" \
  || echo "   (se a conta ja existia, segue para a promocao a administrador)"
echo ">> Promovendo '$USUARIO' a administrador do servidor..."
kubectl -n "$NS" exec "$POD" -- sh -c "$MESH --adminaccount \"\$1\"" _ "$USUARIO"

# O MeshCentral le as contas na inicializacao: reinicia para valer.
kubectl -n "$NS" rollout restart deployment/chirpstack-meshcentral-deployment >/dev/null
kubectl -n "$NS" rollout status deployment/chirpstack-meshcentral-deployment --timeout=600s
unset SENHA CONF
echo ">> Pronto: entre em https://mesh-chirpstack.adailsilva.com.br com o usuario '$USUARIO'."
