#!/usr/bin/env bash
# =============================================================================
# 07 - Segredos da aplicacao do ChirpStack Manager
# =============================================================================
#   APP_JWT_SECRET        gerado se ausente ou < 32 caracteres (trocar so derruba sessoes)
#   APP_ADMIN_PASSWORD    gravada SO se ausente, com a senha INICIAL fixa (admin@chirpstack.com.br /
#                         chirpstack, ver README). Vale so no 1o boot com banco vazio; no primeiro
#                         acesso a API obriga a troca. Valor ja presente e mantido.
#   APP_CHAVE_CIFRAGEM    gerada SO se ausente (trocar invalida os segredos do Drive)
#   CHIRPSTACK_API_TOKEN  API key ADMIN do ChirpStack v4: informe CHIRPSTACK_API_TOKEN=...
#                         (o pipeline usa o GitHub Secret CHIRPSTACK_MANAGER_API_TOKEN)
#   MQTT_USERNAME/        copiados do Secret chirpstack-v4-mqtt-credentials (namespace chirpstack-v4):
#   MQTT_PASSWORD         o mesmo usuario do broker usado pelo ChirpStack (monitoramento). Rode de
#                         novo depois de trocar a senha do MQTT (07.criar_secrets_chirpstack-v4 --trocar-mqtt).
#   api-chirpstack-manager-mqtt-ca   copia da CA do broker (Secret chirpstack-v4-mosquitto-ca, chave ca.crt)
#   GOOGLE_DRIVE_*        opcionais: GOOGLE_DRIVE_CLIENT_ID=... GOOGLE_DRIVE_CLIENT_SECRET=...
#
# Uso:
#   bash 07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh
#   CHIRPSTACK_API_TOKEN='...' bash 07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh
#   bash 07.gerar_segredos_aplicacao_api-chirpstack-manager_script.sh --conferir
# =============================================================================
set -euo pipefail
NS=chirpstack-manager
SECRET=api-chirpstack-manager-secret
SECRET_CA=api-chirpstack-manager-mqtt-ca
NS_CHIRPSTACK=chirpstack-v4
existe() { kubectl -n "$NS" get secret "$SECRET" >/dev/null 2>&1; }
ler()    { kubectl -n "$NS" get secret "$SECRET" -o jsonpath="{.data.$1}" 2>/dev/null | base64 -d 2>/dev/null || true; }
gravar() { [ "${GITHUB_ACTIONS:-}" = "true" ] && [ -n "$2" ] && echo "::add-mask::$2"
           kubectl -n "$NS" patch secret "$SECRET" --type merge -p "{\"stringData\":{\"$1\":\"$2\"}}" >/dev/null; }
gerar()  { head -c 192 /dev/urandom | base64 -w0 | tr -dc 'A-Za-z0-9' | cut -c1-"$1"; }
garantir_secret() {
  kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply --server-side -f - >/dev/null
  existe || kubectl -n "$NS" create secret generic "$SECRET" >/dev/null
}

garantir_secret
if [ "${1:-}" = "--conferir" ]; then
  for CH in APP_JWT_SECRET APP_ADMIN_PASSWORD APP_CHAVE_CIFRAGEM CHIRPSTACK_API_TOKEN MQTT_USERNAME MQTT_PASSWORD GOOGLE_DRIVE_CLIENT_ID GOOGLE_DRIVE_CLIENT_SECRET; do
    V=$(ler "$CH"); printf '  %-28s %s\n' "$CH" "$([ -n "$V" ] && echo "presente (${#V} caracteres)" || echo AUSENTE)"
  done
  printf '  %-28s %s\n' "CA do MQTT" "$(kubectl -n "$NS" get secret "$SECRET_CA" >/dev/null 2>&1 && echo presente || echo AUSENTE)"
  exit 0
fi

# Senha inicial do admin: fixa e documentada; a troca e obrigatoria no primeiro acesso.
if [ -z "$(ler APP_ADMIN_PASSWORD)" ]; then
  gravar APP_ADMIN_PASSWORD "chirpstack"; echo ">> APP_ADMIN_PASSWORD ausente: senha inicial padrao gravada."
else
  echo ">> APP_ADMIN_PASSWORD presente (mantida)."
fi

for ITEM in APP_CHAVE_CIFRAGEM:0:32 APP_JWT_SECRET:32:64; do
  CH=${ITEM%%:*}; RESTO=${ITEM#*:}; MIN=${RESTO%%:*}; TAM=${RESTO##*:}
  V=$(ler "$CH")
  if [ -z "$V" ] || { [ "$MIN" -gt 0 ] && [ "${#V}" -lt "$MIN" ]; }; then
    gravar "$CH" "$(gerar "$TAM")"; echo ">> $CH ausente ou fraca: valor forte gerado."
  else
    [ "${GITHUB_ACTIONS:-}" = "true" ] && echo "::add-mask::$V"; echo ">> $CH presente (mantida)."
  fi
done

if [ -n "${CHIRPSTACK_API_TOKEN:-}" ]; then
  [ "$(ler CHIRPSTACK_API_TOKEN)" != "$CHIRPSTACK_API_TOKEN" ] && gravar CHIRPSTACK_API_TOKEN "$CHIRPSTACK_API_TOKEN" \
    && echo ">> CHIRPSTACK_API_TOKEN gravado." || echo ">> CHIRPSTACK_API_TOKEN ja sincronizado."
elif [ -z "$(ler CHIRPSTACK_API_TOKEN)" ]; then
  gravar CHIRPSTACK_API_TOKEN ""
  echo ">> AVISO: CHIRPSTACK_API_TOKEN vazio -- a API sobe, mas recusa as chamadas ao ChirpStack."
fi
# Credenciais e CA do broker MQTT do ChirpStack v4 (o Manager assina os eventos dos dispositivos).
lerCs() { kubectl -n "$NS_CHIRPSTACK" get secret "$1" -o jsonpath="{.data.${2//./\\.}}" 2>/dev/null | base64 -d 2>/dev/null || true; }
U=$(lerCs chirpstack-v4-mqtt-credentials MQTT_USERNAME); P=$(lerCs chirpstack-v4-mqtt-credentials MQTT_PASSWORD)
if [ -n "$U" ] && [ -n "$P" ]; then
  [ "${GITHUB_ACTIONS:-}" = "true" ] && echo "::add-mask::$P"
  if [ "$(ler MQTT_USERNAME)" != "$U" ] || [ "$(ler MQTT_PASSWORD)" != "$P" ]; then
    gravar MQTT_USERNAME "$U"; gravar MQTT_PASSWORD "$P"; echo ">> MQTT_USERNAME/MQTT_PASSWORD copiados do ChirpStack v4."
  else
    echo ">> Credenciais MQTT ja sincronizadas com o ChirpStack v4."
  fi
else
  echo ">> AVISO: chirpstack-v4-mqtt-credentials nao encontrado em $NS_CHIRPSTACK -- o monitoramento do Manager sobe sem receber eventos."
fi
CA=$(lerCs chirpstack-v4-mosquitto-ca ca.crt)
if [ -n "$CA" ]; then
  printf '%s\n' "$CA" > /tmp/chirpstack-manager-mqtt-ca.crt
  kubectl -n "$NS" create secret generic "$SECRET_CA" --from-file=ca.crt=/tmp/chirpstack-manager-mqtt-ca.crt \
    --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f - >/dev/null
  rm -f /tmp/chirpstack-manager-mqtt-ca.crt
  echo ">> CA do broker copiada para o Secret $SECRET_CA."
else
  echo ">> AVISO: chirpstack-v4-mosquitto-ca nao encontrado em $NS_CHIRPSTACK -- sem a CA a conexao TLS ao broker falha."
  kubectl -n "$NS" get secret "$SECRET_CA" >/dev/null 2>&1 || kubectl -n "$NS" create secret generic "$SECRET_CA" >/dev/null
fi
for CH in GOOGLE_DRIVE_CLIENT_ID GOOGLE_DRIVE_CLIENT_SECRET; do
  if [ -n "${!CH:-}" ]; then gravar "$CH" "${!CH}"; echo ">> $CH gravado."; fi
done
echo ">> Admin inicial (so com banco vazio): admin@chirpstack.com.br com a senha inicial do README; troca obrigatoria no primeiro acesso."
