package br.com.adailsilva.chirpstackmanager.api.mqtt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.EventoDispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EventoDispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import br.com.adailsilva.chirpstackmanager.api.service.AlertaService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.FilaComandosService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Decodificacao;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Grava um evento do ChirpStack v4 (integração MQTT, JSON).
 * <p>
 * Tópico {@code application/<aplicacao>/device/<devEUI>/event/<tipo>}:
 * <ul>
 *   <li><b>up</b>: vira uma {@link Leitura} (idempotente pelo {@code deduplicationId}), atualiza o
 *       último estado do dispositivo e passa pelas regras de alerta;</li>
 *   <li><b>status</b>: bateria e alimentação externa;</li>
 *   <li><b>join, ack, txack, log, location, integration</b>: viram {@link EventoDispositivo}.</li>
 * </ul>
 * O dispositivo nasce na primeira mensagem, com nome, aplicação, perfil e tags do {@code deviceInfo}.
 */
@Service
public class EventoChirpStackService {

    private static final Logger log = LoggerFactory.getLogger(EventoChirpStackService.class);
    private static final Pattern TOPICO = Pattern.compile("^application/([^/]+)/device/([0-9a-fA-F]{16})/event/([a-z]+)$");
    private static final int MAX_PAYLOAD_EVENTO = 20_000;

    private final DispositivoRepository dispositivos;
    private final LeituraRepository leituras;
    private final EventoDispositivoRepository eventos;
    private final AlertaService alertas;
    private final ObjectMapper mapper;
    private final FilaComandosService fila;

    public EventoChirpStackService(DispositivoRepository dispositivos, LeituraRepository leituras,
                                   EventoDispositivoRepository eventos, AlertaService alertas, ObjectMapper mapper,
                                   FilaComandosService fila) {
        this.fila = fila;
        this.dispositivos = dispositivos;
        this.leituras = leituras;
        this.eventos = eventos;
        this.alertas = alertas;
        this.mapper = mapper;
    }

    @Transactional
    public void processar(String topico, String payload) {
        Matcher m = TOPICO.matcher(topico == null ? "" : topico);
        if (!m.matches()) {
            log.debug("Tópico fora do padrão ignorado: {}", topico);
            return;
        }
        String tipo = m.group(3);
        JsonNode json = mapper.readTree(payload);
        Instant quando = instante(json.path("time"));
        Dispositivo d = obterDispositivo(m.group(2).toLowerCase(), m.group(1), json.path("deviceInfo"), quando);

        switch (tipo) {
            case "up" -> uplink(d, json, quando);
            case "status" -> status(d, json, quando, payload);
            default -> evento(d, tipo, json, quando, payload);
        }
        d.setUltimoEvento(tipo);
        if (d.getUltimoContatoEm() == null || quando.isAfter(d.getUltimoContatoEm())) {
            d.setUltimoContatoEm(quando);
        }
        d.setAtualizadoEm(Instant.now());
        dispositivos.save(d);
    }

    // ── uplink ───────────────────────────────────────────────────────────────

    private void uplink(Dispositivo d, JsonNode j, Instant quando) {
        String dedup = texto(j, "deduplicationId");
        if (dedup == null) {
            dedup = d.getDevEui() + ":" + j.path("fCnt").asLong(-1) + ":" + quando.toEpochMilli();
        }
        if (leituras.existsByDeduplicationId(dedup)) {
            return; // repetida (outra réplica ou reentrega do broker)
        }
        Leitura l = new Leitura();
        l.setDispositivoId(d.getId());
        l.setDeduplicationId(dedup.length() > 36 ? dedup.substring(0, 36) : dedup);
        l.setRecebidoEm(quando);
        l.setFCnt(longo(j, "fCnt"));
        l.setFPort(inteiro(j, "fPort"));
        l.setDr(inteiro(j, "dr"));
        l.setConfirmado(j.has("confirmed") ? j.path("confirmed").asBoolean(false) : null);
        String dados = texto(j, "data");
        l.setDadosBase64(dados != null && dados.length() <= 512 ? dados : null);
        JsonNode tx = j.path("txInfo");
        l.setFrequencia(longo(tx, "frequency"));
        l.setSpreadingFactor(inteiro(tx.path("modulation").path("lora"), "spreadingFactor"));

        // Melhor gateway (maior RSSI) entre os que ouviram o uplink
        JsonNode rx = j.path("rxInfo");
        JsonNode melhor = null;
        if (rx.isArray()) {
            for (JsonNode g : rx) {
                if (melhor == null || g.path("rssi").asInt(-999) > melhor.path("rssi").asInt(-999)) {
                    melhor = g;
                }
            }
            l.setQtdGateways(rx.size());
        }
        if (melhor != null) {
            l.setGatewayId(texto(melhor, "gatewayId"));
            l.setRssi(inteiro(melhor, "rssi"));
            l.setSnr(decimal(melhor.path("snr")));
        }
        JsonNode obj = j.path("object");
        if (obj.isObject() && !obj.isEmpty()) {
            l.setObjeto(obj.toString());
        }
        // Equipamento com modelo de firmware cadastrado: o próprio servidor decodifica os bytes
        byte[] bytes = bytes(dados);
        Decodificacao dec = fila.decodificar(d.getId(), l.getFPort(), bytes);
        if (dec != null && l.getObjeto() == null && !dec.objeto().isEmpty()) {
            l.setObjeto(dec.objeto().toString());
            obj = dec.objeto();
        }
        try {
            leituras.saveAndFlush(l);
        } catch (DataIntegrityViolationException e) {
            return; // outra réplica gravou o mesmo uplink no mesmo instante
        }

        d.setTotalUplinks(d.getTotalUplinks() + 1);
        d.setUltimoFCnt(l.getFCnt());
        d.setUltimoRssi(l.getRssi());
        d.setUltimoSnr(l.getSnr());
        d.setUltimoGateway(l.getGatewayId());
        if (l.getObjeto() != null) {
            d.setUltimoObjeto(l.getObjeto());
        }
        alertas.avaliarLeitura(d, l, obj.isObject() ? obj : null);
        Long id = d.getId();
        Integer porta = l.getFPort();
        fila.depoisDoCommit(() -> fila.aoReceberUplink(id, porta, bytes, dec == null ? null : dec.efeitos()));
    }

    private static byte[] bytes(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── status ───────────────────────────────────────────────────────────────

    private void status(Dispositivo d, JsonNode j, Instant quando, String payload) {
        if (!j.path("batteryLevelUnavailable").asBoolean(false) && j.path("batteryLevel").isNumber()) {
            int b = (int) Math.round(j.path("batteryLevel").asDouble());
            d.setUltimaBateria(Math.max(0, Math.min(100, b)));
        }
        if (j.has("externalPowerSource")) {
            d.setAlimentacaoExterna(j.path("externalPowerSource").asBoolean(false));
        }
        String desc = d.getUltimaBateria() == null ? "Status recebido"
                : "Bateria " + d.getUltimaBateria() + "%" + (Boolean.TRUE.equals(d.getAlimentacaoExterna()) ? " (alimentação externa)" : "");
        gravarEvento(d, "status", quando, null, desc, payload);
        alertas.avaliarBateria(d);
    }

    // ── demais eventos ───────────────────────────────────────────────────────

    private void evento(Dispositivo d, String tipo, JsonNode j, Instant quando, String payload) {
        String nivel = null;
        String desc = switch (tipo) {
            case "join" -> "Join aceito" + (texto(j, "devAddr") != null ? " (DevAddr " + texto(j, "devAddr") + ")" : "");
            case "ack" -> j.path("acknowledged").asBoolean(false) ? "Downlink confirmado pelo dispositivo"
                    : "Downlink NÃO confirmado pelo dispositivo";
            case "txack" -> "Downlink transmitido pelo gateway"
                    + (texto(j, "gatewayId") != null ? " " + texto(j, "gatewayId") : "");
            case "log" -> {
                nivel = texto(j, "level");
                String code = texto(j, "code");
                String d2 = texto(j, "description");
                yield (code != null ? code + ": " : "") + (d2 != null ? d2 : "");
            }
            case "location" -> {
                JsonNode loc = j.path("location");
                yield "Localização " + loc.path("latitude").asDouble() + ", " + loc.path("longitude").asDouble();
            }
            case "integration" -> "Evento de integração" + (texto(j, "eventType") != null ? " " + texto(j, "eventType") : "");
            default -> "Evento " + tipo;
        };
        gravarEvento(d, tipo, quando, nivel, desc, payload);
        avisarFila(d, tipo, j, nivel);
    }

    /** Eventos que movem a fila de comandos (tratados depois do commit). */
    private void avisarFila(Dispositivo d, String tipo, JsonNode j, String nivel) {
        Long id = d.getId();
        String item = texto(j, "queueItemId");
        switch (tipo) {
            case "txack" -> fila.depoisDoCommit(() -> fila.aoTransmitir(item));
            case "ack" -> {
                boolean ok = j.path("acknowledged").asBoolean(false);
                fila.depoisDoCommit(() -> fila.aoConfirmar(item, ok));
            }
            case "join" -> fila.depoisDoCommit(() -> fila.aoEntrar(id));
            case "log" -> {
                String code = texto(j, "code");
                if ("ERROR".equalsIgnoreCase(nivel) && code != null && code.startsWith("DOWNLINK")) {
                    String descricao = texto(j, "description");
                    fila.depoisDoCommit(() -> fila.aoErroDownlink(id, code, descricao));
                }
            }
            default -> {
                // nada para a fila
            }
        }
    }

    private void gravarEvento(Dispositivo d, String tipo, Instant quando, String nivel, String desc, String payload) {
        EventoDispositivo e = new EventoDispositivo();
        e.setDispositivoId(d.getId());
        e.setTipo(tipo.length() > 20 ? tipo.substring(0, 20) : tipo);
        e.setRecebidoEm(quando);
        e.setNivel(nivel != null && nivel.length() > 10 ? nivel.substring(0, 10) : nivel);
        e.setDescricao(desc != null && desc.length() > 500 ? desc.substring(0, 500) : desc);
        e.setPayload(payload != null && payload.length() > MAX_PAYLOAD_EVENTO ? payload.substring(0, MAX_PAYLOAD_EVENTO) : payload);
        eventos.save(e);
    }

    // ── dispositivo ──────────────────────────────────────────────────────────

    private Dispositivo obterDispositivo(String devEui, String aplicacaoId, JsonNode info, Instant quando) {
        Dispositivo d = dispositivos.findByDevEui(devEui).orElse(null);
        if (d == null) {
            d = new Dispositivo();
            d.setDevEui(devEui);
            d.setNome(devEui);
            d.setCriadoEm(Instant.now());
            d.setAtualizadoEm(Instant.now());
            log.info("Novo dispositivo {} (aplicação {}).", devEui, aplicacaoId);
        }
        if (info.isObject()) {
            String nome = texto(info, "deviceName");
            if (nome != null && !nome.isBlank()) {
                d.setNome(cortar(nome, 120));
            }
            d.setAplicacaoNome(cortar(texto(info, "applicationName"), 120));
            d.setPerfilNome(cortar(texto(info, "deviceProfileName"), 120));
            d.setTenantNome(cortar(texto(info, "tenantName"), 120));
            JsonNode tags = info.path("tags");
            d.setTags(tags.isObject() && !tags.isEmpty() ? cortar(tags.toString(), 2000) : null);
        }
        d.setAplicacaoId(cortar(aplicacaoId, 36));
        if (d.getId() == null) {
            d = dispositivos.saveAndFlush(d);
        }
        return d;
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    private static Instant instante(JsonNode n) {
        if (n != null && n.isString()) {
            try {
                Instant t = Instant.parse(n.asString());
                // Relógio do gateway muito adiantado não pode "congelar" o último contato no futuro
                return t.isAfter(Instant.now().plusSeconds(300)) ? Instant.now() : t;
            } catch (DateTimeParseException ignorado) {
                // usa a hora de chegada
            }
        }
        return Instant.now();
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        return v == null || v.isNull() || v.isMissingNode() ? null : v.asString();
    }

    private static Integer inteiro(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        return v != null && v.isNumber() ? v.asInt() : null;
    }

    private static Long longo(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        return v != null && v.isNumber() ? v.asLong() : null;
    }

    private static BigDecimal decimal(JsonNode v) {
        return v != null && v.isNumber() ? v.decimalValue().setScale(2, RoundingMode.HALF_UP) : null;
    }

    private static String cortar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
