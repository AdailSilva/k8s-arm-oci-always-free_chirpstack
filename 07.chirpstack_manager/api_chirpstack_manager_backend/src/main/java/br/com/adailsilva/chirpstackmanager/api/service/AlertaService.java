package br.com.adailsilva.chirpstackmanager.api.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.Alerta;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Operador;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Severidade;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.AlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.RegraAlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.AlertaMudouEvento;
import br.com.adailsilva.chirpstackmanager.api.service.email.EmailService;
import tools.jackson.databind.JsonNode;

/**
 * Motor de alertas.
 * <ul>
 *   <li><b>VALOR</b>: avaliado a cada uplink (campos do objeto decodificado, {@code rssi}, {@code snr})
 *       e a cada status ({@code bateria});</li>
 *   <li><b>SEM_COMUNICACAO</b>: avaliado a cada minuto pelo último contato do dispositivo.</li>
 * </ul>
 * Condição atendida abre um alerta (se já não houver um ABERTO/RECONHECIDO da mesma regra e
 * dispositivo); condição normal de novo resolve o que estiver aberto. A notificação por e-mail sai
 * depois do commit, fora da transação da mensagem.
 */
@Service
public class AlertaService {

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final EnumSet<StatusAlerta> EM_ABERTO = EnumSet.of(StatusAlerta.ABERTO, StatusAlerta.RECONHECIDO);
    public static final String CAMPO_RSSI = "rssi";
    public static final String CAMPO_SNR = "snr";
    public static final String CAMPO_BATERIA = "bateria";

    private final RegraAlertaRepository regras;
    private final AlertaRepository alertas;
    private final DispositivoRepository dispositivos;
    private final EmailService email;
    private final TransactionTemplate tx;
    private final ZoneId fuso;
    private final ApplicationEventPublisher eventos;

    /** Campo da regra criada pelo sistema para comandos que não foram executados (fila de comandos). */
    public static final String CAMPO_COMANDO_NAO_EXECUTADO = "comando_nao_executado";

    public AlertaService(RegraAlertaRepository regras, AlertaRepository alertas, DispositivoRepository dispositivos,
                         EmailService email, PlatformTransactionManager tm, ApplicationEventPublisher eventos,
                         @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.eventos = eventos;
        this.regras = regras;
        this.alertas = alertas;
        this.dispositivos = dispositivos;
        this.email = email;
        this.tx = new TransactionTemplate(tm);
        this.fuso = ZoneId.of(fuso);
    }

    // ── avaliação ────────────────────────────────────────────────────────────

    /** Regras VALOR sobre um uplink (chamado dentro da transação da mensagem). */
    public void avaliarLeitura(Dispositivo d, Leitura l, JsonNode objeto) {
        for (RegraAlerta r : regras.ativasPara(TipoRegra.VALOR, d.getId())) {
            String campo = r.getCampo() == null ? "" : r.getCampo().trim();
            if (CAMPO_BATERIA.equalsIgnoreCase(campo)) {
                continue;
            }
            BigDecimal v = switch (campo.toLowerCase()) {
                case CAMPO_RSSI -> l.getRssi() == null ? null : BigDecimal.valueOf(l.getRssi());
                case CAMPO_SNR -> l.getSnr();
                default -> valorDoObjeto(objeto, campo);
            };
            if (v != null) {
                aplicar(r, d, v);
            }
        }
    }

    /** Regras VALOR com campo "bateria" (chamado ao receber um status). */
    public void avaliarBateria(Dispositivo d) {
        if (d.getUltimaBateria() == null) {
            return;
        }
        for (RegraAlerta r : regras.ativasPara(TipoRegra.VALOR, d.getId())) {
            if (CAMPO_BATERIA.equalsIgnoreCase(r.getCampo() == null ? "" : r.getCampo().trim())) {
                aplicar(r, d, BigDecimal.valueOf(d.getUltimaBateria()));
            }
        }
    }

    /** SEM_COMUNICACAO: a cada minuto. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    @Transactional
    public void avaliarSemComunicacao() {
        List<RegraAlerta> lista = regras.findByAtivoTrueAndTipo(TipoRegra.SEM_COMUNICACAO);
        if (lista.isEmpty()) {
            return;
        }
        Instant agora = Instant.now();
        List<Dispositivo> ativos = dispositivos.findByAtivoTrue();
        for (RegraAlerta r : lista) {
            Duration limite = Duration.ofMinutes(Math.max(1, r.getLimite().longValue()));
            for (Dispositivo d : ativos) {
                if (r.getDispositivoId() != null && !r.getDispositivoId().equals(d.getId())) {
                    continue;
                }
                Instant ultimo = d.getUltimoContatoEm() != null ? d.getUltimoContatoEm() : d.getCriadoEm();
                long minutos = Duration.between(ultimo, agora).toMinutes();
                boolean dispara = Duration.between(ultimo, agora).compareTo(limite) > 0;
                processar(r, d, dispara, BigDecimal.valueOf(minutos),
                        r.getNome() + ": " + d.getNome() + " sem comunicação há " + minutos + " min (limite "
                                + limite.toMinutes() + " min)");
            }
        }
    }

    private void aplicar(RegraAlerta r, Dispositivo d, BigDecimal v) {
        if (r.getOperador() == null) {
            return;
        }
        boolean dispara = r.getOperador().avaliar(v, r.getLimite());
        processar(r, d, dispara, v, r.getNome() + ": " + d.getNome() + " " + r.getCampo() + " = "
                + v.stripTrailingZeros().toPlainString() + " (" + r.getOperador().simbolo() + " "
                + r.getLimite().stripTrailingZeros().toPlainString() + ")");
    }

    private void processar(RegraAlerta r, Dispositivo d, boolean dispara, BigDecimal valor, String mensagem) {
        Optional<Alerta> aberto = alertas.findFirstByRegraIdAndDispositivoIdAndStatusIn(r.getId(), d.getId(), EM_ABERTO);
        if (dispara && aberto.isEmpty()) {
            Alerta a = new Alerta();
            a.setRegraId(r.getId());
            a.setDispositivoId(d.getId());
            a.setStatus(StatusAlerta.ABERTO);
            a.setValor(valor);
            a.setMensagem(mensagem.length() > 300 ? mensagem.substring(0, 300) : mensagem);
            a.setDisparadoEm(Instant.now());
            Alerta salvo = alertas.save(a);
            log.info("Alerta aberto: {}", salvo.getMensagem());
            eventos.publishEvent(new AlertaMudouEvento(r.getId(), d.getId(), salvo.getId(), true));
            if (r.isNotificarEmail() && r.getDestinatarios() != null && !r.getDestinatarios().isBlank()) {
                notificarDepoisDoCommit(salvo.getId(), r, d);
            }
        } else if (!dispara && aberto.isPresent()) {
            Alerta a = aberto.get();
            a.setStatus(StatusAlerta.RESOLVIDO);
            a.setResolvidoEm(Instant.now());
            alertas.save(a);
            eventos.publishEvent(new AlertaMudouEvento(r.getId(), d.getId(), a.getId(), false));
        }
    }

    // ── comandos não executados ──────────────────────────────────────────────

    /**
     * Abre (se ainda não houver) o alerta "Comando não executado" do dispositivo. A regra nasce na
     * primeira falha, valendo para todos os dispositivos; nela o usuário liga o e-mail, muda a
     * severidade ou cria automações.
     */
    public void comandoNaoExecutado(Dispositivo d, int tentativas, String mensagem) {
        RegraAlerta r = regraComandos();
        if (r.isAtivo()) {
            processar(r, d, true, BigDecimal.valueOf(tentativas), r.getNome() + ": " + d.getNome() + " — " + mensagem);
        }
    }

    /** Um comando do dispositivo foi executado: resolve o alerta de comando não executado, se houver. */
    public void comandoExecutado(Dispositivo d) {
        regras.findFirstByCampoOrderByIdAsc(CAMPO_COMANDO_NAO_EXECUTADO)
                .ifPresent(r -> processar(r, d, false, null, ""));
    }

    private RegraAlerta regraComandos() {
        return regras.findFirstByCampoOrderByIdAsc(CAMPO_COMANDO_NAO_EXECUTADO).orElseGet(() -> {
            RegraAlerta r = new RegraAlerta();
            r.setNome("Comando não executado");
            r.setTipo(TipoRegra.VALOR);
            r.setCampo(CAMPO_COMANDO_NAO_EXECUTADO);
            r.setOperador(Operador.GT);
            r.setLimite(BigDecimal.ZERO);
            r.setSeveridade(Severidade.AVISO);
            r.setAtivo(true);
            r.setCriadoEm(Instant.now());
            r.setAtualizadoEm(Instant.now());
            r.setAtualizadoPor("sistema");
            return regras.save(r);
        });
    }

    // ── tela ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Alerta> listar(StatusAlerta status, Long dispositivoId, int pagina, int tamanho) {
        return alertas.buscar(status, dispositivoId, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 200)));
    }

    @Transactional
    public Alerta reconhecer(Long id, String usuario) {
        Alerta a = alertas.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Alerta não encontrado."));
        if (a.getStatus() != StatusAlerta.ABERTO) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Só um alerta ABERTO pode ser reconhecido.");
        }
        a.setStatus(StatusAlerta.RECONHECIDO);
        a.setReconhecidoEm(Instant.now());
        a.setReconhecidoPor(usuario);
        return alertas.save(a);
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    /** Caminho com pontos no objeto decodificado ("sensor.temperatura"). Booleano vira 1/0. */
    static BigDecimal valorDoObjeto(JsonNode objeto, String caminho) {
        if (objeto == null || caminho == null || caminho.isBlank()) {
            return null;
        }
        JsonNode n = objeto;
        for (String parte : caminho.split("\\.")) {
            // Índice numérico em lista: "pzem.0.potencia"
            n = n.isArray() && parte.matches("\\d+") ? itemDaLista(n, Integer.parseInt(parte)) : n.path(parte);
        }
        if (n.isNumber()) {
            return n.decimalValue();
        }
        if (n.isBoolean()) {
            return n.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (n.isString()) {
            try {
                return new BigDecimal(n.asString().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Item da lista pelo "canal" (medidores, relés) ou, sem canal, pela posição. */
    private static JsonNode itemDaLista(JsonNode lista, int chave) {
        for (JsonNode item : lista) {
            if (item.isObject() && item.path("canal").isNumber() && item.path("canal").asInt() == chave) {
                return item;
            }
        }
        return lista.path(chave);
    }

    private void notificarDepoisDoCommit(Long alertaId, RegraAlerta r, Dispositivo d) {
        String[] para = Arrays.stream(r.getDestinatarios().split("[,;\\s]+")).filter(s -> s.contains("@")).toArray(String[]::new);
        String severidade = r.getSeveridade().name();
        String nomeDisp = d.getNome();
        String devEui = d.getDevEui();
        Runnable envio = () -> Thread.ofVirtual().start(() -> {
            Alerta a = alertas.findById(alertaId).orElse(null);
            if (a == null || para.length == 0) {
                return;
            }
            String quando = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(fuso).format(a.getDisparadoEm());
            boolean ok = email.enviar(para, "[" + severidade + "] " + a.getMensagem(),
                    email.corpoAlerta(severidade, a.getMensagem(), nomeDisp, devEui, quando));
            if (ok) {
                tx.executeWithoutResult(s -> alertas.findById(alertaId).ifPresent(x -> {
                    x.setEmailEnviado(true);
                    alertas.save(x);
                }));
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    envio.run();
                }
            });
        } else {
            envio.run();
        }
    }
}
