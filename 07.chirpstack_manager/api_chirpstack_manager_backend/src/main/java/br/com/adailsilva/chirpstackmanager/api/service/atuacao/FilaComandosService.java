package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PoliticaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.ComandoTentativa;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.EstrategiaConfirmacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ResultadoTentativa;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoTentativaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EquipamentoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.PerifericoRepository;
import br.com.adailsilva.chirpstackmanager.api.service.AlertaService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.ContextoEquipamento;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Decodificacao;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Desfecho;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriverModelo;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Efeitos;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Modbus;
import br.com.adailsilva.chirpstackmanager.api.service.chirpstack.ChirpStackClient;
import br.com.adailsilva.chirpstackmanager.api.service.chirpstack.Query;
import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Motor da fila de comandos.
 * <ul>
 *   <li>a cada ciclo: vence a validade, encerra tentativas sem resposta (ou que sumiram da fila do
 *       ChirpStack) e envia o próximo PENDENTE de cada dispositivo livre, dentro da janela de horário
 *       — um comando em andamento por dispositivo, na ordem em que foram pedidos;</li>
 *   <li>eventos do ChirpStack: {@code txack} marca TRANSMITIDO, {@code ack} confirma/recusa downlink
 *       confirmado, {@code log} de erro de downlink derruba a tentativa;</li>
 *   <li>uplinks: o driver do modelo diz se o uplink prova (ou recusa) o comando em andamento, e os
 *       efeitos (estado dos relés, medições, status) atualizam os periféricos; divergência entre o
 *       estado desejado e o reportado gera um comando de reconciliação.</li>
 * </ul>
 * Tentativa sem sucesso volta a PENDENTE para agora + intervalo × recuo^(n-1), até esgotar as
 * tentativas (FALHOU) ou a validade (EXPIRADO); os dois abrem o alerta "Comando não executado".
 * Uma trava serializa o ciclo e os eventos (uma réplica do backend).
 */
@Service
public class FilaComandosService {

    private static final Logger log = LoggerFactory.getLogger(FilaComandosService.class);
    private static final Set<StatusComando> ATIVOS = EnumSet.of(StatusComando.PENDENTE, StatusComando.ENFILEIRADO, StatusComando.TRANSMITIDO);
    private static final Set<StatusComando> EM_ANDAMENTO = EnumSet.of(StatusComando.ENFILEIRADO, StatusComando.TRANSMITIDO);
    private static final Duration ESPERA_MINIMA_FILA = Duration.ofMinutes(2);
    private static final Duration INTERVALO_CONSULTA_FILA = Duration.ofMinutes(5);
    private static final Duration INTERVALO_RECONCILIACAO = Duration.ofMinutes(10);

    private final ComandoRepository comandos;
    private final ComandoTentativaRepository tentativas;
    private final DispositivoRepository dispositivos;
    private final EquipamentoRepository equipamentos;
    private final PerifericoRepository perifericos;
    private final EquipamentoService equipamentoService;
    private final ComandoService comandoService;
    private final AlertaService alertas;
    private final ChirpStackClient cs;
    private final TransactionTemplate tx;
    private final ZoneId zona;
    private final boolean eventosAssincronos;
    private final ReentrantLock trava = new ReentrantLock();
    private final ExecutorService eventos = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("fila-comandos").factory());
    private final Map<Long, Instant> ultimaConsultaFila = new ConcurrentHashMap<>();

    public FilaComandosService(ComandoRepository comandos, ComandoTentativaRepository tentativas,
                               DispositivoRepository dispositivos, EquipamentoRepository equipamentos,
                               PerifericoRepository perifericos, EquipamentoService equipamentoService,
                               ComandoService comandoService, AlertaService alertas, ChirpStackClient cs,
                               PlatformTransactionManager tm,
                               @Value("${app.fuso-horario:America/Fortaleza}") String fuso,
                               @Value("${app.comandos.eventos-assincronos:true}") boolean eventosAssincronos) {
        this.comandos = comandos;
        this.tentativas = tentativas;
        this.dispositivos = dispositivos;
        this.equipamentos = equipamentos;
        this.perifericos = perifericos;
        this.equipamentoService = equipamentoService;
        this.comandoService = comandoService;
        this.alertas = alertas;
        this.cs = cs;
        this.tx = new TransactionTemplate(tm);
        // Transação própria: os eventos rodam no afterCommit de outra transação.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.zona = ZoneId.of(fuso);
        this.eventosAssincronos = eventosAssincronos;
    }

    @PreDestroy
    void encerrar() {
        eventos.shutdown();
    }

    // ── ciclo ────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${app.comandos.ciclo-ms:15000}", initialDelay = 30_000)
    public void ciclo() {
        comTrava(() -> processarCiclo(Instant.now()));
    }

    /** Um ciclo completo (exposto para testes). */
    public void processarCiclo(Instant agora) {
        List<Long> ids = tx.execute(s -> comandos.findByStatusInOrderByCriadoEmAscIdAsc(ATIVOS).stream().map(Comando::getId).toList());
        for (Long id : ids) {
            isolado(id, () -> vigiar(id, agora));
        }
        // Despacho: o mais antigo PENDENTE de cada dispositivo sem comando em andamento
        Map<Long, Comando> primeiro = new LinkedHashMap<>();
        Set<Long> ocupados = new HashSet<>();
        tx.executeWithoutResult(s -> {
            for (Comando c : comandos.findByStatusInOrderByCriadoEmAscIdAsc(ATIVOS)) {
                if (c.getStatus().emAndamento()) {
                    ocupados.add(c.getDispositivoId());
                } else {
                    primeiro.putIfAbsent(c.getDispositivoId(), c);
                }
            }
        });
        for (Comando c : primeiro.values()) {
            if (!ocupados.contains(c.getDispositivoId()) && !c.getProximaTentativaEm().isAfter(agora) && naJanela(c, agora)) {
                isolado(c.getId(), () -> enviar(c.getId(), agora));
            }
        }
    }

    private void vigiar(Long id, Instant agora) {
        Comando c = comandos.findById(id).orElse(null);
        if (c == null || !ATIVOS.contains(c.getStatus())) {
            return;
        }
        if (c.getValidadeEm().isBefore(agora)) {
            if (c.getStatus().emAndamento()) {
                removerDaFilaSePossivel(c);
            }
            fecharTentativa(c, ResultadoTentativa.EXPIRADO, "Validade vencida", agora);
            finalizar(c, StatusComando.EXPIRADO, "Validade vencida sem execução", agora);
        } else if (c.getStatus() == StatusComando.TRANSMITIDO
                && agora.isAfter(c.getTransmitidoEm().plusSeconds(c.getAguardarRespostaS()))) {
            falhaTentativa(c, ResultadoTentativa.SEM_RESPOSTA, "Sem prova de execução em " + c.getAguardarRespostaS() + " s após a transmissão", null, agora);
        } else if (c.getStatus() == StatusComando.ENFILEIRADO) {
            Duration espera = Duration.ofSeconds(c.getAguardarRespostaS());
            if (espera.compareTo(ESPERA_MINIMA_FILA) < 0) {
                espera = ESPERA_MINIMA_FILA;
            }
            Instant ultima = ultimaConsultaFila.get(c.getId());
            if (agora.isAfter(c.getEnfileiradoEm().plus(espera))
                    && (ultima == null || agora.isAfter(ultima.plus(INTERVALO_CONSULTA_FILA)))) {
                ultimaConsultaFila.put(c.getId(), agora);
                if (!naFilaChirpStack(c)) {
                    falhaTentativa(c, ResultadoTentativa.PERDIDO, "O item saiu da fila do ChirpStack sem transmissão registrada", null, agora);
                }
            }
        }
    }

    private void enviar(Long id, Instant agora) {
        Comando c = comandos.findById(id).orElse(null);
        if (c == null || c.getStatus() != StatusComando.PENDENTE) {
            return;
        }
        Dispositivo d = dispositivos.findById(c.getDispositivoId()).orElse(null);
        if (d == null) {
            return;
        }
        c.setTentativas(c.getTentativas() + 1);
        ComandoTentativa t = new ComandoTentativa();
        t.setComandoId(c.getId());
        t.setNumero(c.getTentativas());
        t.setEnfileiradoEm(agora);
        try {
            ObjectNode item = JsonNodeFactory.instance.objectNode();
            item.put("confirmed", c.isConfirmado());
            item.put("fPort", c.getFPort());
            item.put("data", c.getDadosBase64());
            ObjectNode corpo = JsonNodeFactory.instance.objectNode();
            corpo.set("queueItem", item);
            JsonNode r = cs.post("/api/devices/{devEui}/queue", corpo, d.getDevEui());
            String itemId = r == null ? null : r.path("id").asString(null);
            t.setChirpstackItemId(itemId);
            tentativas.save(t);
            c.setChirpstackItemId(itemId);
            c.setStatus(StatusComando.ENFILEIRADO);
            c.setEnfileiradoEm(agora);
            c.setTransmitidoEm(null);
            c.setDetalhe("Tentativa " + c.getTentativas() + " na fila do ChirpStack");
            salvar(c, agora);
            ultimaConsultaFila.remove(c.getId());
        } catch (RuntimeException e) {
            tentativas.save(t);
            log.warn("Comando {}: falha ao enfileirar no ChirpStack: {}", c.getId(), e.getMessage());
            falhaTentativa(c, ResultadoTentativa.ERRO, "ChirpStack recusou: " + cortar(e.getMessage(), 300), null, agora);
        }
    }

    // ── eventos do ChirpStack ────────────────────────────────────────────────

    /** Gateway transmitiu o item da fila (evento txack). */
    public void aoTransmitir(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        evento(() -> comandos.findFirstByChirpstackItemIdAndStatusIn(itemId, EnumSet.of(StatusComando.ENFILEIRADO)).ifPresent(c -> {
            Instant agora = Instant.now();
            c.setStatus(StatusComando.TRANSMITIDO);
            c.setTransmitidoEm(agora);
            c.setDetalhe("Transmitido; aguardando prova de execução");
            ultimaTentativa(c).ifPresent(t -> {
                t.setTransmitidoEm(agora);
                tentativas.save(t);
            });
            salvar(c, agora);
            if (c.getEstrategia() == EstrategiaConfirmacao.TRANSMISSAO) {
                concluir(c, Desfecho.executado("Transmitido pelo gateway (o firmware não envia resposta)", null, null), agora);
            }
        }));
    }

    /** Ack LoRaWAN de downlink confirmado (evento ack). */
    public void aoConfirmar(String itemId, boolean reconhecido) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        evento(() -> comandos.findFirstByChirpstackItemIdAndStatusIn(itemId, EM_ANDAMENTO).ifPresent(c -> {
            if (!c.isConfirmado()) {
                return;
            }
            Instant agora = Instant.now();
            if (!reconhecido) {
                falhaTentativa(c, ResultadoTentativa.NAO_CONFIRMADO, "Dispositivo não confirmou o downlink", null, agora);
            } else if (c.getEstrategia() == EstrategiaConfirmacao.CONFIRMACAO_LORAWAN) {
                concluir(c, Desfecho.executado("Downlink confirmado pelo dispositivo (ack)", null, null), agora);
            }
        }));
    }

    /** Erro de downlink registrado pelo ChirpStack (evento log, código DOWNLINK_*). */
    public void aoErroDownlink(Long dispositivoId, String codigo, String descricao) {
        evento(() -> comandos.findByDispositivoIdAndStatusInOrderByCriadoEmAscIdAsc(dispositivoId, EnumSet.of(StatusComando.ENFILEIRADO))
                .stream().findFirst().ifPresent(c -> falhaTentativa(c, ResultadoTentativa.ERRO,
                        "ChirpStack: " + codigo + (descricao == null ? "" : " — " + descricao), null, Instant.now())));
    }

    /**
     * Join aceito. No Radioenge original as saídas voltam a 0 quando o módulo reinicia: o estado
     * reportado dos relés passa a desligado e a reconciliação reaplica o desejado.
     */
    public void aoEntrar(Long dispositivoId) {
        evento(() -> equipamentoService.contexto(dispositivoId).ifPresent(ctx -> {
            if (ctx.equipamento().getModelo() != ModeloFirmware.RADIOENGE_ORIGINAL) {
                return;
            }
            Instant agora = Instant.now();
            for (Periferico r : ctx.reles()) {
                r.setEstadoReportado(false);
                r.setReportadoEm(agora);
                perifericos.save(r);
            }
            reconciliar(ctx, agora);
        }));
    }

    /** Decodificação feita pelo driver do modelo (dentro da transação do uplink); {@code null} se não houver. */
    public Decodificacao decodificar(Long dispositivoId, Integer porta, byte[] dados) {
        if (porta == null || dados == null || dados.length == 0) {
            return null;
        }
        return equipamentoService.contexto(dispositivoId)
                .map(ctx -> equipamentoService.driver(ctx.equipamento()).decodificar(porta, dados, ctx))
                .orElse(null);
    }

    /** Uplink gravado: efeitos nos periféricos, prova do comando em andamento e reconciliação. */
    public void aoReceberUplink(Long dispositivoId, Integer porta, byte[] dados, Efeitos efeitos) {
        evento(() -> equipamentoService.contexto(dispositivoId).ifPresent(ctx -> {
            Instant agora = Instant.now();
            if (efeitos != null) {
                aplicarEfeitos(ctx, efeitos, agora);
            }
            if (porta != null && dados != null) {
                DriverModelo driver = equipamentoService.driver(ctx.equipamento());
                for (Comando c : comandos.findByDispositivoIdAndStatusInOrderByCriadoEmAscIdAsc(dispositivoId, EM_ANDAMENTO)) {
                    Desfecho df = driver.avaliar(c, comandoService.parametros(c), ctx, porta, dados);
                    if (df == null) {
                        continue;
                    }
                    switch (df.tipo()) {
                        case EXECUTADO -> concluir(c, df, agora);
                        case REPETIR -> falhaTentativa(c, df.resultado(), df.detalhe(), df.resposta(), agora);
                        case FALHOU -> {
                            c.setResposta(df.resposta());
                            fecharTentativa(c, ResultadoTentativa.RECUSADO, df.detalhe(), agora);
                            finalizar(c, StatusComando.FALHOU, df.detalhe(), agora);
                        }
                    }
                }
            }
            reconciliar(equipamentoService.contexto(dispositivoId).orElse(ctx), agora);
        }));
    }

    // ── operações do usuário ─────────────────────────────────────────────────

    public Comando cancelar(Long id, String usuario) {
        return comTravaRetorno(() -> tx.execute(s -> {
            Comando c = comandoService.buscar(id);
            if (c.getStatus().finalizado()) {
                throw new BusinessException(HttpStatus.CONFLICT, "O comando já terminou (" + c.getStatus() + ").");
            }
            Instant agora = Instant.now();
            if (c.getStatus().emAndamento()) {
                removerDaFilaSePossivel(c);
            }
            fecharTentativa(c, ResultadoTentativa.CANCELADO, "Cancelado por " + usuario, agora);
            c.setStatus(StatusComando.CANCELADO);
            c.setConcluidoEm(agora);
            c.setDetalhe("Cancelado por " + usuario);
            return salvar(c, agora);
        }));
    }

    // ── transições ───────────────────────────────────────────────────────────

    private void falhaTentativa(Comando c, ResultadoTentativa resultado, String detalhe, String resposta, Instant agora) {
        fecharTentativa(c, resultado, detalhe, agora);
        if (resposta != null) {
            c.setResposta(resposta);
        }
        c.setChirpstackItemId(null);
        if (c.getTentativas() >= c.getMaxTentativas()) {
            finalizar(c, StatusComando.FALHOU, detalhe + " — " + c.getTentativas() + " tentativa(s) esgotada(s)", agora);
            return;
        }
        double fator = Math.pow(c.getFatorRecuo().doubleValue(), Math.max(0, c.getTentativas() - 1));
        long espera = Math.min(86_400L * 7, Math.round(c.getIntervaloTentativaS() * fator));
        Instant proxima = agora.plusSeconds(espera);
        if (proxima.isAfter(c.getValidadeEm())) {
            finalizar(c, StatusComando.EXPIRADO, detalhe + " — a próxima tentativa passaria da validade", agora);
            return;
        }
        c.setStatus(StatusComando.PENDENTE);
        c.setProximaTentativaEm(proxima);
        c.setDetalhe(detalhe + " — nova tentativa às " + DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(zona).format(proxima));
        salvar(c, agora);
    }

    private void finalizar(Comando c, StatusComando status, String detalhe, Instant agora) {
        c.setStatus(status);
        c.setConcluidoEm(agora);
        c.setDetalhe(cortar(detalhe, 500));
        c.setChirpstackItemId(null);
        salvar(c, agora);
        ultimaConsultaFila.remove(c.getId());
        if (status == StatusComando.FALHOU || status == StatusComando.EXPIRADO) {
            log.info("Comando {} ({}) {}: {}", c.getId(), c.getResumo(), status, detalhe);
            dispositivos.findById(c.getDispositivoId()).ifPresent(d -> alertas.comandoNaoExecutado(d, c.getTentativas(),
                    c.getResumo() + " — " + (status == StatusComando.EXPIRADO ? "expirou" : "falhou")));
        }
    }

    private void concluir(Comando c, Desfecho df, Instant agora) {
        fecharTentativa(c, ResultadoTentativa.EXECUTADO, df.detalhe(), agora);
        c.setStatus(StatusComando.EXECUTADO);
        c.setConcluidoEm(agora);
        c.setDetalhe(cortar(df.detalhe(), 500));
        if (df.resposta() != null) {
            c.setResposta(df.resposta());
        }
        c.setChirpstackItemId(null);
        salvar(c, agora);
        ultimaConsultaFila.remove(c.getId());
        Optional<ContextoEquipamento> ctx = equipamentoService.contexto(c.getDispositivoId());
        if (df.efeitos() != null && ctx.isPresent()) {
            aplicarEfeitos(ctx.get(), df.efeitos(), agora);
        }
        dispositivos.findById(c.getDispositivoId()).ifPresent(alertas::comandoExecutado);
        // Pulso feito pelo servidor: agenda a volta ao fim da duração
        if (c.getTipo() == TipoComando.RELE_PULSO && ctx.isPresent()
                && equipamentoService.driver(ctx.get().equipamento()).pulsoPeloServidor()) {
            ParametrosComando p = comandoService.parametros(c);
            boolean estado = p.estado() == null || p.estado();
            Comando volta = comandoService.criar(c.getDispositivoId(), TipoComando.RELE_DEFINIR,
                    ParametrosComando.rele(p.canal(), estado ? AcaoRele.DESLIGAR : AcaoRele.LIGAR),
                    new PoliticaRequest(c.getMaxTentativas(), c.getIntervaloTentativaS(), c.getFatorRecuo(),
                            c.getAguardarRespostaS(), null, null, null, null),
                    OrigemComando.PULSO, c.getId(), "sistema", true);
            volta.setProximaTentativaEm(agora.plusMillis(p.duracaoMs() == null ? 0 : p.duracaoMs()));
            volta.setValidadeEm(volta.getProximaTentativaEm().plus(Duration.ofHours(24)));
            comandos.save(volta);
        }
    }

    private void fecharTentativa(Comando c, ResultadoTentativa r, String detalhe, Instant agora) {
        ultimaTentativa(c).filter(t -> t.getConcluidaEm() == null).ifPresent(t -> {
            t.setConcluidaEm(agora);
            t.setResultado(r);
            t.setDetalhe(cortar(detalhe, 500));
            tentativas.save(t);
        });
    }

    private Optional<ComandoTentativa> ultimaTentativa(Comando c) {
        return tentativas.findFirstByComandoIdOrderByNumeroDesc(c.getId());
    }

    private Comando salvar(Comando c, Instant agora) {
        c.setAtualizadoEm(agora);
        return comandos.save(c);
    }

    // ── periféricos ──────────────────────────────────────────────────────────

    private void aplicarEfeitos(ContextoEquipamento ctx, Efeitos ef, Instant agora) {
        for (Map.Entry<Integer, Boolean> e : ef.reles.entrySet()) {
            ctx.periferico(TipoPeriferico.RELE, e.getKey()).ifPresent(r -> {
                r.setEstadoReportado(e.getValue());
                r.setReportadoEm(agora);
                if (r.getEstadoDesejado() == null) {
                    r.setEstadoDesejado(e.getValue());
                }
                r.setAtualizadoEm(agora);
                perifericos.save(r);
            });
        }
        for (Map.Entry<Integer, Modbus.Medicao> e : ef.medicoes.entrySet()) {
            ctx.periferico(TipoPeriferico.PZEM004T, e.getKey()).ifPresent(m -> {
                m.setLeituraJson(medicaoComCusto(e.getKey(), e.getValue(), ctx.equipamento()).toString());
                m.setLeituraEm(agora);
                m.setAtualizadoEm(agora);
                perifericos.save(m);
            });
        }
        if (ef.status != null) {
            Equipamento eq = equipamentos.findById(ctx.equipamento().getId()).orElse(ctx.equipamento());
            eq.setStatusJson(ef.status.toString());
            eq.setStatusEm(agora);
            equipamentos.save(eq);
        }
    }

    private static ObjectNode medicaoComCusto(int canal, Modbus.Medicao m, Equipamento e) {
        ObjectNode o = JsonNodeFactory.instance.objectNode();
        o.put("canal", canal);
        o.put("tensao", m.tensao());
        o.put("corrente", m.corrente());
        o.put("potencia", m.potencia());
        o.put("energia", m.energia());
        o.put("frequencia", m.frequencia());
        o.put("fp", m.fp());
        o.put("alarme", m.alarme());
        if (e.getTarifaKwh() != null) {
            o.put("custoTotal", BigDecimal.valueOf(m.energia()).movePointLeft(3).multiply(e.getTarifaKwh()).setScale(2, RoundingMode.HALF_UP));
        }
        return o;
    }

    /**
     * Estado desejado ≠ reportado (ex.: equipamento reiniciou, alguém mexeu no relé no local): manda
     * os relés de volta ao desejado, se o equipamento pedir reconciliação, não houver comando
     * pendente para ele e a última reconciliação tiver mais de 10 minutos.
     */
    private void reconciliar(ContextoEquipamento ctx, Instant agora) {
        Equipamento e = ctx.equipamento();
        if (!e.isReconciliar() || !equipamentoService.driver(e).comandos().contains(TipoComando.RELE_MASCARA)) {
            return;
        }
        int mascara = 0;
        int valores = 0;
        for (Periferico r : ctx.reles()) {
            if (r.getEstadoDesejado() != null && r.getEstadoReportado() != null
                    && !r.getEstadoDesejado().equals(r.getEstadoReportado()) && r.getCanal() < 16) {
                mascara |= 1 << r.getCanal();
                if (r.getEstadoDesejado()) {
                    valores |= 1 << r.getCanal();
                }
            }
        }
        if (mascara == 0 || comandos.existsByDispositivoIdAndStatusIn(e.getDispositivoId(), ATIVOS)) {
            return;
        }
        Optional<Comando> ultima = comandos.findFirstByDispositivoIdAndOrigemOrderByCriadoEmDesc(e.getDispositivoId(), OrigemComando.RECONCILIACAO);
        if (ultima.isPresent() && ultima.get().getCriadoEm().isAfter(agora.minus(INTERVALO_RECONCILIACAO))) {
            return;
        }
        try {
            comandoService.criar(e.getDispositivoId(), TipoComando.RELE_MASCARA, ParametrosComando.mascara(mascara, valores),
                    new PoliticaRequest(3, 120, BigDecimal.ONE, null, 30, null, null, null),
                    OrigemComando.RECONCILIACAO, null, "sistema", true);
            log.info("Dispositivo {}: reconciliando relés (máscara 0x{}).", e.getDispositivoId(), Integer.toHexString(mascara));
        } catch (BusinessException ex) {
            log.warn("Dispositivo {}: reconciliação não criada: {}", e.getDispositivoId(), ex.getMessage());
        }
    }

    // ── ChirpStack ───────────────────────────────────────────────────────────

    private boolean naFilaChirpStack(Comando c) {
        try {
            String devEui = dispositivos.findById(c.getDispositivoId()).map(Dispositivo::getDevEui).orElse(null);
            JsonNode r = cs.get("/api/devices/{devEui}/queue", Query.of(), devEui);
            for (JsonNode i : r.path("result")) {
                if (c.getChirpstackItemId() != null && c.getChirpstackItemId().equals(i.path("id").asString(null))) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException e) {
            return true; // sem como conferir agora: não derruba a tentativa
        }
    }

    /** Esvazia a fila do dispositivo no ChirpStack só se o único item for o deste comando. */
    private void removerDaFilaSePossivel(Comando c) {
        if (c.getChirpstackItemId() == null) {
            return;
        }
        try {
            String devEui = dispositivos.findById(c.getDispositivoId()).map(Dispositivo::getDevEui).orElse(null);
            JsonNode r = cs.get("/api/devices/{devEui}/queue", Query.of(), devEui);
            List<String> ids = new ArrayList<>();
            r.path("result").forEach(i -> ids.add(i.path("id").asString("")));
            if (ids.size() == 1 && ids.get(0).equals(c.getChirpstackItemId())) {
                cs.delete("/api/devices/{devEui}/queue", devEui);
            }
        } catch (RuntimeException e) {
            log.debug("Comando {}: não foi possível limpar a fila do ChirpStack: {}", c.getId(), e.getMessage());
        }
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    boolean naJanela(Comando c, Instant agora) {
        ZonedDateTime local = agora.atZone(zona);
        if (!DiasSemana.contem(c.getDiasSemana(), local.getDayOfWeek())) {
            return false;
        }
        if (c.getJanelaInicio() == null || c.getJanelaFim() == null) {
            return true;
        }
        LocalTime t = local.toLocalTime();
        LocalTime i = c.getJanelaInicio();
        LocalTime f = c.getJanelaFim();
        return i.isBefore(f) || i.equals(f) ? !t.isBefore(i) && !t.isAfter(f) : !t.isBefore(i) || !t.isAfter(f);
    }

    /** Evento do ChirpStack: fora da thread do MQTT, em ordem, com a trava e numa transação própria. */
    private void evento(Runnable r) {
        Runnable tarefa = () -> {
            try {
                comTrava(() -> tx.executeWithoutResult(s -> r.run()));
            } catch (RuntimeException e) {
                log.warn("Fila de comandos: falha ao tratar evento: {}", e.getMessage(), e);
            }
        };
        if (!eventosAssincronos) {
            tarefa.run();
        } else {
            eventos.submit(tarefa);
        }
    }

    /** Agenda o tratamento para depois do commit da transação atual (ou já, sem transação). */
    public void depoisDoCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private void isolado(Long id, Runnable r) {
        try {
            tx.executeWithoutResult(s -> r.run());
        } catch (RuntimeException e) {
            log.warn("Comando {}: {}", id, e.getMessage());
        }
    }

    private void comTrava(Runnable r) {
        trava.lock();
        try {
            r.run();
        } finally {
            trava.unlock();
        }
    }

    private <T> T comTravaRetorno(java.util.function.Supplier<T> s) {
        trava.lock();
        try {
            return s.get();
        } finally {
            trava.unlock();
        }
    }

    private static String cortar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
