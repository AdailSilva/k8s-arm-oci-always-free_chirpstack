#!/usr/bin/env bash
# =============================================================================
# 07 - Segredos do ChirpStack v4 (fora do Git)
# =============================================================================
# O repositorio k8s-arm-oci-always-free_chirpstack e PUBLICO: nenhum segredo fica nos
# manifestos. Este script cria/troca os segredos do namespace chirpstack-v4, no padrao
# chirpstack-<versao>-<componente>-<conteudo> (o mesmo do ChirpStack v3):
#
#   chirpstack-v4-api-secret        API_SECRET
#       Assina os tokens de login e de API ([api].secret do chirpstack.toml, via env
#       CHIRPSTACK_API_SECRET no 17.chirpstack-v4__Deployment.yaml). Trocar a chave
#       invalida TODOS os logins e chaves de API existentes.
#       ATENCAO: o ChirpStack Manager (CHIRPSTACK_API_TOKEN) acessa o ChirpStack com
#       chaves de API assinadas por este segredo. Depois de trocar, gere chaves novas na
#       UI do ChirpStack (API keys) e atualize o Secret do Manager.
#
#   chirpstack-v4-mqtt-credentials  MQTT_USERNAME / MQTT_PASSWORD
#   chirpstack-v4-mosquitto-auth    passwd
#       O broker (porta 8883, TLS) tem UM usuario (padrao: adailsilva), usado pelo
#       ChirpStack e pelo Gateway Bridge (e copiado para o ChirpStack Manager pelo script
#       07 dele). --trocar-mqtt gera uma senha nova, regrava o passwd (com o
#       mosquitto_passwd do proprio pod do broker) e o Secret de credenciais, e reinicia
#       Mosquitto, ChirpStack e Gateway Bridge. Clientes MQTT de fora do cluster que usam
#       esse usuario precisam receber a senha nova.
#
#   chirpstack-v4-mosquitto-tls     ca.crt / tls.crt / tls.key   (certificado do broker)
#   chirpstack-v4-mosquitto-ca      ca.crt                       (CA para os clientes)
#       Emitidos pela CA propria (chirpstack-internal-mqtt-ca). Instalacao inicial do
#       broker (README, secao do MQTT com TLS); este script so confere (e, se faltar
#       o chirpstack-v4-mosquitto-ca, copia o ca.crt do chirpstack-v4-mosquitto-tls).
#
# Sem argumentos (o pipeline roda assim antes de todo Sync -- main.yml, jobs chirpstack_stack_*):
#   cria o que falta SEM mudar nada no ar.
#
# Uso:
#   bash 07.criar_secrets_chirpstack-v4_script.sh                  # cria o que falta
#   bash 07.criar_secrets_chirpstack-v4_script.sh --trocar-api     # chave NOVA + restart do ChirpStack
#   bash 07.criar_secrets_chirpstack-v4_script.sh --trocar-mqtt    # senha MQTT NOVA + restarts
#
# Valores proprios em vez de gerados: API_SECRET='...' e/ou MQTT_PASSWORD='...' antes
# do comando; usuario MQTT: MQTT_USERNAME (padrao: o do Secret atual, ou adailsilva).
# Os valores nunca sao impressos.
# =============================================================================
set -euo pipefail

NS="chirpstack-v4"
SECRET_API="chirpstack-v4-api-secret"
SECRET_MQTT="chirpstack-v4-mqtt-credentials"
SECRET_MOSQUITTO="chirpstack-v4-mosquitto-auth"
SECRET_TLS="chirpstack-v4-mosquitto-tls"
SECRET_CA="chirpstack-v4-mosquitto-ca"
DEPLOY_CHIRPSTACK="chirpstack-v4-deployment"
DEPLOY_BRIDGE="chirpstack-v4-gateway-bridge-deployment"
DEPLOY_MOSQUITTO="chirpstack-v4-mosquitto-deployment"

command -v kubectl >/dev/null || { echo "ERRO: kubectl nao encontrado."; exit 1; }
command -v openssl >/dev/null || { echo "ERRO: openssl nao encontrado."; exit 1; }
command -v jq      >/dev/null || { echo "ERRO: jq nao encontrado."; exit 1; }

existe() { kubectl -n "$NS" get secret "$1" >/dev/null 2>&1; }
ler()    { kubectl -n "$NS" get secret "$1" -o jsonpath="{.data.${2//./\\.}}" 2>/dev/null | base64 -d; }

aplicar_secret() {  # aplicar_secret <nome> <chave=valor>...  (cria ou substitui, sem expor valores)
  local NOME="$1"; shift
  local ARGS=()
  for KV in "$@"; do ARGS+=("--from-literal=$KV"); done
  kubectl -n "$NS" create secret generic "$NOME" "${ARGS[@]}" --dry-run=client -o yaml \
    | kubectl apply -f - >/dev/null
}

# Copia chaves de um Secret para outro, com os mesmos bytes (direto do base64: nao
# perde a quebra de linha final do ca.crt).
copiar() {  # copiar <destino> <origem> <chave>...
  local NOVO="$1" ORIGEM="$2"; shift 2
  existe "$ORIGEM" || return 1
  local JSON C FILTRO='{}'
  JSON=$(kubectl -n "$NS" get secret "$ORIGEM" -o json)
  for C in "$@"; do
    jq -e --arg c "$C" '.data[$c] // empty' >/dev/null <<<"$JSON" || return 1
    FILTRO="$FILTRO + {\"$C\": .data[\"$C\"]}"
  done
  jq --arg ns "$NS" --arg nome "$NOVO" \
    "{apiVersion: \"v1\", kind: \"Secret\", type: \"Opaque\", metadata: {name: \$nome, namespace: \$ns}, data: ($FILTRO)}" \
    <<<"$JSON" | kubectl apply -f - >/dev/null
  echo ">> $NOVO criado com os valores de $ORIGEM (mesmos valores)."
}

chave_api() {
  aplicar_secret "$SECRET_API" "API_SECRET=${API_SECRET:-$(openssl rand -base64 32)}"
  echo ">> $SECRET_API gravado."
}

criar_o_que_falta() {
  if existe "$SECRET_API"; then echo ">> $SECRET_API ja existe."
  else chave_api; echo "   (valor novo gerado)"; fi

  if existe "$SECRET_MQTT"; then echo ">> $SECRET_MQTT ja existe."
  else
    echo "ERRO: $SECRET_MQTT nao existe."
    echo "      Instalacao nova: crie o usuario no Mosquitto e o Secret (README, secao do MQTT)."
    exit 1
  fi

  if existe "$SECRET_MOSQUITTO"; then echo ">> $SECRET_MOSQUITTO ja existe."
  else echo "ERRO: $SECRET_MOSQUITTO nao existe (instalacao inicial do broker -- README)."; exit 1; fi

  if existe "$SECRET_CA"; then echo ">> $SECRET_CA ja existe."
  elif copiar "$SECRET_CA" "$SECRET_TLS" ca.crt; then :
  else echo "ERRO: $SECRET_CA nao existe (instalacao inicial do broker -- README)."; exit 1; fi

  existe "$SECRET_TLS" || { echo "ERRO: $SECRET_TLS nao existe (instalacao inicial do broker -- README)."; exit 1; }
}

trocar_mqtt() {
  local USUARIO="${MQTT_USERNAME:-$(ler "$SECRET_MQTT" MQTT_USERNAME || true)}"
  USUARIO="${USUARIO:-adailsilva}"
  local SENHA="${MQTT_PASSWORD:-$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)}"
  local POD TMP NOVO
  existe "$SECRET_MOSQUITTO" || { echo "ERRO: Secret $SECRET_MOSQUITTO nao existe em $NS."; exit 1; }
  POD=$(kubectl -n "$NS" get pod -l app="$DEPLOY_MOSQUITTO" -o jsonpath='{.items[0].metadata.name}')
  [ -n "$POD" ] || { echo "ERRO: pod do Mosquitto nao encontrado em $NS."; exit 1; }

  TMP=$(mktemp -d); trap 'rm -rf "$TMP"' RETURN
  ler "$SECRET_MOSQUITTO" passwd > "$TMP/passwd"
  # Atualiza so a linha do usuario; os outros usuarios do passwd continuam iguais.
  # Usuario e senha vao pelo stdin do exec (nao aparecem na linha de comando).
  NOVO=$( { printf '%s\n%s\n' "$USUARIO" "$SENHA"; cat "$TMP/passwd"; } \
    | kubectl -n "$NS" exec -i "$POD" -c chirpstack-v4-mosquitto -- sh -c '
        read -r U; read -r P; cat > /tmp/passwd.novo
        mosquitto_passwd -b /tmp/passwd.novo "$U" "$P" >/dev/null && cat /tmp/passwd.novo; rm -f /tmp/passwd.novo' )
  [ -n "$NOVO" ] || { echo "ERRO: mosquitto_passwd nao gerou o arquivo."; exit 1; }
  printf '%s\n' "$NOVO" > "$TMP/passwd.novo"

  kubectl -n "$NS" create secret generic "$SECRET_MOSQUITTO" --from-file=passwd="$TMP/passwd.novo" \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  aplicar_secret "$SECRET_MQTT" "MQTT_USERNAME=$USUARIO" "MQTT_PASSWORD=$SENHA"
  echo ">> $SECRET_MOSQUITTO e $SECRET_MQTT gravados (usuario $USUARIO)."

  for D in "$DEPLOY_MOSQUITTO" "$DEPLOY_CHIRPSTACK" "$DEPLOY_BRIDGE"; do
    kubectl -n "$NS" rollout restart deployment "$D" >/dev/null 2>&1 || true
  done
  echo ">> Mosquitto, ChirpStack e Gateway Bridge reiniciados."
  echo ">> ChirpStack Manager: rode o 07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh para copiar a senha nova."
}

confirmar() {
  read -r -p ">> $1 [s/N] " R
  [ "${R:-N}" = "s" ] || [ "${R:-N}" = "S" ] || exit 1
}

case "${1:-}" in
  --trocar-api)
    confirmar "Gerar NOVA chave da API (invalida todos os logins e chaves de API) e reiniciar o ChirpStack?"
    chave_api
    kubectl -n "$NS" rollout restart deployment "$DEPLOY_CHIRPSTACK" >/dev/null
    echo ">> ChirpStack reiniciado." ;;

  --trocar-mqtt)
    confirmar "Gerar NOVA senha MQTT e reiniciar Mosquitto, ChirpStack e Gateway Bridge?"
    trocar_mqtt ;;

  "")
    criar_o_que_falta
    echo ">> Segredos do ChirpStack v4 prontos para o Sync." ;;

  *)
    echo "Uso: $0 [--trocar-api | --trocar-mqtt]"; exit 1 ;;
esac
