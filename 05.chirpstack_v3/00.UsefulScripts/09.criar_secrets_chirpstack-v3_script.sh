#!/usr/bin/env bash
# =============================================================================
# 09 - Segredos do ChirpStack v3 (fora do Git)
# =============================================================================
# O repositorio k8s-arm-oci-always-free_chirpstack e PUBLICO: nenhum segredo fica nos
# manifestos. Este script cria/troca os segredos do namespace chirpstack-v3, no padrao
# chirpstack-<versao>-<componente>-<conteudo> (o mesmo do ChirpStack v4):
#
#   chirpstack-v3-api-secret        API_SECRET
#       Assina os tokens de login e de API do Application Server
#       (APPLICATION_SERVER__EXTERNAL_API__JWT_SECRET no 27.*Deployment.yaml). Trocar
#       invalida todos os logins e chaves de API.
#
#   chirpstack-v3-mqtt-credentials  MQTT_USERNAME / MQTT_PASSWORD
#   chirpstack-v3-mosquitto-auth    passwd
#       O broker (porta 1888, publica no NLB) exige usuario e senha. UM usuario (padrao:
#       adailsilva, o mesmo do v4), usado pelo Gateway Bridge, Network Server e Application Server.
#       --trocar-mqtt gera outra senha, regrava os dois Secrets e reinicia Mosquitto,
#       Gateway Bridge, NS e AS. Clientes MQTT de fora do cluster precisam da senha nova.
#
# Sem argumentos (o pipeline roda assim antes de todo Sync -- main.yml, jobs chirpstack_stack_*):
#   cria o que falta SEM mudar nada no ar. Se so o passwd faltar, ele e regerado com o
#   usuario e a senha do Secret de credenciais. So quando nao ha valor nenhum e que um
#   novo e gerado.
#
# Uso:
#   bash 09.criar_secrets_chirpstack-v3_script.sh                # cria o que falta
#   bash 09.criar_secrets_chirpstack-v3_script.sh --trocar-api   # chave NOVA + restart do AS
#   bash 09.criar_secrets_chirpstack-v3_script.sh --trocar-mqtt  # senha MQTT NOVA + restarts
#
# Valores proprios em vez de gerados: API_SECRET='...' e/ou MQTT_PASSWORD='...' antes do
# comando; usuario MQTT: MQTT_USERNAME (padrao: o do Secret atual, ou adailsilva).
# Para ler a senha atual:
#   kubectl -n chirpstack-v3 get secret chirpstack-v3-mqtt-credentials -o jsonpath='{.data.MQTT_PASSWORD}' | base64 -d
# Os valores nunca sao impressos por este script.
# =============================================================================
set -euo pipefail

NS="chirpstack-v3"
SECRET_API="chirpstack-v3-api-secret"
SECRET_MQTT="chirpstack-v3-mqtt-credentials"
SECRET_MOSQUITTO="chirpstack-v3-mosquitto-auth"
DEPLOY_AS="chirpstack-v3-application-server-deployment"
DEPLOYS_MQTT=(chirpstack-v3-mosquitto-deployment chirpstack-v3-gateway-bridge-deployment
              chirpstack-v3-network-server-deployment "$DEPLOY_AS")

command -v kubectl >/dev/null || { echo "ERRO: kubectl nao encontrado."; exit 1; }
command -v openssl >/dev/null || { echo "ERRO: openssl nao encontrado."; exit 1; }

existe() { kubectl -n "$NS" get secret "$1" >/dev/null 2>&1; }
ler()    { kubectl -n "$NS" get secret "$1" -o jsonpath="{.data.$2}" 2>/dev/null | base64 -d; }

aplicar_secret() {  # aplicar_secret <nome> <chave=valor>...  (cria ou substitui, sem expor valores)
  local NOME="$1"; shift
  local ARGS=()
  for KV in "$@"; do ARGS+=("--from-literal=$KV"); done
  kubectl -n "$NS" create secret generic "$NOME" "${ARGS[@]}" --dry-run=client -o yaml \
    | kubectl apply -f - >/dev/null
}

gravar_api() {
  aplicar_secret "$SECRET_API" "API_SECRET=${API_SECRET:-$(openssl rand -base64 32)}"
}

# Linha do passwd do Mosquitto no formato $6$ (SHA-512 com salt), aceito pelo Mosquitto 2.x:
#   usuario:$6$<salt base64>$<base64(sha512(senha + salt))>
# Gerada aqui com openssl, sem depender do mosquitto_passwd nem do broker no ar.
linha_passwd() {  # <usuario> <senha>
  local TMP SALT HASH
  TMP=$(mktemp -d)
  openssl rand 12 > "$TMP/salt"
  SALT=$(base64 -w0 < "$TMP/salt")
  HASH=$( { printf '%s' "$2"; cat "$TMP/salt"; } | openssl dgst -sha512 -binary | base64 -w0)
  rm -rf "$TMP"
  printf '%s:$6$%s$%s\n' "$1" "$SALT" "$HASH"
}

gravar_mqtt() {
  local USUARIO="${MQTT_USERNAME:-$(ler "$SECRET_MQTT" MQTT_USERNAME || true)}"
  USUARIO="${USUARIO:-adailsilva}"
  local SENHA="${MQTT_PASSWORD:-$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)}"
  aplicar_secret "$SECRET_MOSQUITTO" "passwd=$(linha_passwd "$USUARIO" "$SENHA")"
  aplicar_secret "$SECRET_MQTT" "MQTT_USERNAME=$USUARIO" "MQTT_PASSWORD=$SENHA"
  echo ">> $SECRET_MOSQUITTO e $SECRET_MQTT gravados (usuario $USUARIO)."
}

criar_o_que_falta() {
  kubectl get namespace "$NS" >/dev/null 2>&1 || kubectl create namespace "$NS" >/dev/null

  if existe "$SECRET_API"; then echo ">> $SECRET_API ja existe."
  else gravar_api; echo ">> $SECRET_API criado com valor NOVO."; fi

  if existe "$SECRET_MQTT" && existe "$SECRET_MOSQUITTO"; then
    echo ">> $SECRET_MQTT e $SECRET_MOSQUITTO ja existem."
  elif existe "$SECRET_MQTT"; then
    # So o passwd falta: regera com o usuario e a senha atuais (nada muda para os clientes).
    MQTT_PASSWORD="${MQTT_PASSWORD:-$(ler "$SECRET_MQTT" MQTT_PASSWORD)}" gravar_mqtt
  else gravar_mqtt; fi
}

confirmar() {
  read -r -p ">> $1 [s/N] " R
  [ "${R:-N}" = "s" ] || [ "${R:-N}" = "S" ] || exit 1
}

case "${1:-}" in
  "")
    criar_o_que_falta
    echo ">> Segredos do ChirpStack v3 prontos para o Sync." ;;

  --trocar-api)
    confirmar "Gerar NOVA chave da API do v3 (invalida logins e chaves de API) e reiniciar o Application Server?"
    gravar_api
    kubectl -n "$NS" rollout restart deployment "$DEPLOY_AS" >/dev/null 2>&1 || true
    echo ">> $SECRET_API trocado." ;;

  --trocar-mqtt)
    confirmar "Gerar NOVA senha MQTT do v3 e reiniciar Mosquitto, Gateway Bridge, NS e AS?"
    gravar_mqtt
    for D in "${DEPLOYS_MQTT[@]}"; do
      kubectl -n "$NS" rollout restart deployment "$D" >/dev/null 2>&1 || true
    done
    echo ">> Mosquitto, Gateway Bridge, Network Server e Application Server reiniciados." ;;

  *)
    echo "Uso: $0 [--trocar-api | --trocar-mqtt]"; exit 1 ;;
esac
