package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoDetalheResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PoliticaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ResumoComandosResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.TentativaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Equipamento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoTentativaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EquipamentoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.PerifericoRepository;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.ContextoEquipamento;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.DriverModelo;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.Downlink;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.protocolo.Bytes;
import tools.jackson.databind.ObjectMapper;

/**
 * Cria comandos (codificados pelo driver do modelo de firmware), com a política de novas
 * tentativas, e responde às consultas da fila. As mudanças de estado ficam na
 * {@link FilaComandosService}.
 */
@Service
public class ComandoService {

    private final ComandoRepository comandos;
    private final ComandoTentativaRepository tentativas;
    private final EquipamentoRepository equipamentos;
    private final PerifericoRepository perifericos;
    private final DispositivoRepository dispositivos;
    private final EquipamentoService equipamentoService;
    private final ObjectMapper mapper;
    private final int padraoTentativas;
    private final int padraoIntervaloS;
    private final BigDecimal padraoRecuo;
    private final int padraoAguardarS;
    private final int padraoValidadeMin;

    public ComandoService(ComandoRepository comandos, ComandoTentativaRepository tentativas,
                          EquipamentoRepository equipamentos, PerifericoRepository perifericos,
                          DispositivoRepository dispositivos, EquipamentoService equipamentoService, ObjectMapper mapper,
                          @Value("${app.comandos.max-tentativas:5}") int padraoTentativas,
                          @Value("${app.comandos.intervalo-tentativa-s:300}") int padraoIntervaloS,
                          @Value("${app.comandos.fator-recuo:1.5}") BigDecimal padraoRecuo,
                          @Value("${app.comandos.aguardar-resposta-s:300}") int padraoAguardarS,
                          @Value("${app.comandos.validade-min:1440}") int padraoValidadeMin) {
        this.comandos = comandos;
        this.tentativas = tentativas;
        this.equipamentos = equipamentos;
        this.perifericos = perifericos;
        this.dispositivos = dispositivos;
        this.equipamentoService = equipamentoService;
        this.mapper = mapper;
        this.padraoTentativas = padraoTentativas;
        this.padraoIntervaloS = padraoIntervaloS;
        this.padraoRecuo = padraoRecuo;
        this.padraoAguardarS = padraoAguardarS;
        this.padraoValidadeMin = padraoValidadeMin;
    }

    // ── criação ──────────────────────────────────────────────────────────────

    /**
     * Valida, codifica e põe o comando na fila (PENDENTE). Relé marcado como crítico exige
     * {@code confirmarCritico} em comandos pedidos por pessoas (MANUAL e CENA).
     */
    @Transactional
    public Comando criar(Long dispositivoId, TipoComando tipo, ParametrosComando parametros, PoliticaRequest politica,
                         OrigemComando origem, Long origemId, String usuario, boolean confirmarCritico) {
        if (!dispositivos.existsById(dispositivoId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado.");
        }
        ContextoEquipamento ctx = equipamentoService.contexto(dispositivoId).orElseThrow(() -> new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY, "Configure o modelo do firmware deste dispositivo antes de enviar comandos."));
        Equipamento e = ctx.equipamento();
        DriverModelo driver = equipamentoService.driver(e);
        if (!driver.comandos().contains(tipo)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "\"" + tipo.rotulo() + "\" não é suportado pelo firmware " + e.getModelo().rotulo() + ".");
        }
        ParametrosComando p = parametros == null ? ParametrosComando.vazio() : parametros;
        List<Periferico> afetados = relesAfetados(tipo, p, ctx);
        if ((origem == OrigemComando.MANUAL || origem == OrigemComando.CENA) && !confirmarCritico) {
            List<String> criticos = afetados.stream().filter(Periferico::isCritico).map(r -> r.getCanal() + " (" + r.getNome() + ")").toList();
            if (!criticos.isEmpty()) {
                throw new BusinessException(HttpStatus.CONFLICT,
                        "Carga crítica no relé " + String.join(", ", criticos) + ": confirme o acionamento.");
            }
        }
        int seq = 0;
        if (driver.usaSeq(tipo)) {
            seq = e.getUltimoSeq() % 255 + 1;
            e.setUltimoSeq(seq);
            equipamentos.save(e);
        }
        Downlink dl = driver.codificar(tipo, p, ctx, seq);
        atualizarDesejado(tipo, p, ctx);

        Instant agora = Instant.now();
        PoliticaRequest pol = politica == null ? new PoliticaRequest(null, null, null, null, null, null, null, null) : politica;
        Comando c = new Comando();
        c.setDispositivoId(dispositivoId);
        c.setTipo(tipo);
        c.setParametros(mapper.writeValueAsString(p));
        c.setResumo(cortar(dl.resumo(), 200));
        c.setFPort(dl.porta());
        c.setDadosBase64(Base64.getEncoder().encodeToString(dl.dados()));
        c.setConfirmado(dl.confirmado());
        c.setSeq(driver.usaSeq(tipo) ? seq : null);
        c.setEstrategia(dl.estrategia());
        c.setStatus(StatusComando.PENDENTE);
        c.setOrigem(origem);
        c.setOrigemId(origemId);
        c.setMaxTentativas(pol.maxTentativas() != null ? pol.maxTentativas() : padraoTentativas);
        c.setIntervaloTentativaS(pol.intervaloTentativaS() != null ? pol.intervaloTentativaS() : padraoIntervaloS);
        c.setFatorRecuo(pol.fatorRecuo() != null ? pol.fatorRecuo() : padraoRecuo);
        c.setAguardarRespostaS(pol.aguardarRespostaS() != null ? pol.aguardarRespostaS() : padraoAguardarS);
        c.setValidadeEm(agora.plus(Duration.ofMinutes(pol.validadeMin() != null ? pol.validadeMin() : padraoValidadeMin)));
        if ((pol.janelaInicio() == null) != (pol.janelaFim() == null)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o início e o fim da janela de horário (ou nenhum).");
        }
        c.setJanelaInicio(pol.janelaInicio());
        c.setJanelaFim(pol.janelaFim());
        c.setDiasSemana(DiasSemana.mascara(pol.diasSemana()));
        c.setProximaTentativaEm(agora);
        c.setCriadoPor(usuario);
        c.setCriadoEm(agora);
        c.setAtualizadoEm(agora);
        return comandos.save(c);
    }

    /** Relés que o comando mexe (para a confirmação de carga crítica e o estado desejado). */
    private static List<Periferico> relesAfetados(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx) {
        List<Periferico> l = new ArrayList<>();
        switch (tipo) {
            case RELE_DEFINIR -> {
                if (p.canal() == null) {
                    l.addAll(ctx.reles());
                } else {
                    ctx.periferico(TipoPeriferico.RELE, p.canal()).ifPresent(l::add);
                }
            }
            case RELE_PULSO -> {
                if (p.canal() != null) {
                    ctx.periferico(TipoPeriferico.RELE, p.canal()).ifPresent(l::add);
                }
            }
            case RELE_MASCARA -> {
                int m = p.mascara() == null ? 0 : p.mascara();
                for (Periferico r : ctx.reles()) {
                    if ((m & (1 << r.getCanal())) != 0) {
                        l.add(r);
                    }
                }
            }
            default -> {
                // não mexe em relé
            }
        }
        return l;
    }

    /** O estado desejado é o que foi pedido por último (pulso não muda o desejado). */
    private void atualizarDesejado(TipoComando tipo, ParametrosComando p, ContextoEquipamento ctx) {
        List<Periferico> mudados = new ArrayList<>();
        if (tipo == TipoComando.RELE_DEFINIR && p.acao() != null) {
            for (Periferico r : relesAfetados(tipo, p, ctx)) {
                boolean atual = r.getEstadoReportado() != null ? r.getEstadoReportado()
                        : r.getEstadoDesejado() != null && r.getEstadoDesejado();
                r.setEstadoDesejado(p.acao() == AcaoRele.ALTERNAR ? !atual : p.acao() == AcaoRele.LIGAR);
                mudados.add(r);
            }
        } else if (tipo == TipoComando.RELE_MASCARA) {
            int v = p.valores() == null ? 0 : p.valores();
            for (Periferico r : relesAfetados(tipo, p, ctx)) {
                r.setEstadoDesejado((v & (1 << r.getCanal())) != 0);
                mudados.add(r);
            }
        }
        mudados.forEach(r -> r.setAtualizadoEm(Instant.now()));
        perifericos.saveAll(mudados);
    }

    /** Mesmo comando de novo (novo seq, nova política com os mesmos valores). */
    @Transactional
    public Comando reenviar(Long id, String usuario, boolean confirmarCritico) {
        Comando o = buscar(id);
        ParametrosComando p = parametros(o);
        long validadeMin = Math.max(1, Duration.between(o.getCriadoEm(), o.getValidadeEm()).toMinutes());
        PoliticaRequest pol = new PoliticaRequest(o.getMaxTentativas(), o.getIntervaloTentativaS(), o.getFatorRecuo(),
                o.getAguardarRespostaS(), (int) Math.min(validadeMin, 43200), o.getJanelaInicio(), o.getJanelaFim(),
                DiasSemana.lista(o.getDiasSemana()));
        return criar(o.getDispositivoId(), o.getTipo(), p, pol, OrigemComando.MANUAL, null, usuario, confirmarCritico);
    }

    // ── consultas ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Comando buscar(Long id) {
        return comandos.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Comando não encontrado."));
    }

    @Transactional(readOnly = true)
    public Page<Comando> listar(Long dispositivoId, List<StatusComando> status, int pagina, int tamanho) {
        boolean filtrar = status != null && !status.isEmpty();
        return comandos.buscar(dispositivoId, filtrar, filtrar ? status : EnumSet.allOf(StatusComando.class),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 200)));
    }

    @Transactional(readOnly = true)
    public ResumoComandosResponse resumo() {
        Instant dia = Instant.now().minus(Duration.ofHours(24));
        return new ResumoComandosResponse(comandos.countByStatus(StatusComando.PENDENTE),
                comandos.countByStatus(StatusComando.ENFILEIRADO) + comandos.countByStatus(StatusComando.TRANSMITIDO),
                comandos.countByStatusAndConcluidoEmAfter(StatusComando.EXECUTADO, dia),
                comandos.countByStatusAndConcluidoEmAfter(StatusComando.FALHOU, dia)
                        + comandos.countByStatusAndConcluidoEmAfter(StatusComando.EXPIRADO, dia));
    }

    @Transactional(readOnly = true)
    public ComandoDetalheResponse detalhe(Long id) {
        Comando c = buscar(id);
        List<TentativaResponse> ts = tentativas.findByComandoIdOrderByNumeroAsc(id).stream()
                .map(t -> new TentativaResponse(t.getNumero(), t.getChirpstackItemId(), t.getEnfileiradoEm(),
                        t.getTransmitidoEm(), t.getConcluidaEm(), t.getResultado(), t.getDetalhe()))
                .toList();
        return new ComandoDetalheResponse(resposta(c, nomes(List.of(c))), ts);
    }

    public Map<Long, String> nomes(List<Comando> lista) {
        return dispositivos.findAllById(lista.stream().map(Comando::getDispositivoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Dispositivo::getId, Dispositivo::getNome));
    }

    public ComandoResponse resposta(Comando c, Map<Long, String> nomes) {
        return new ComandoResponse(c.getId(), c.getDispositivoId(), nomes.get(c.getDispositivoId()), c.getTipo(),
                c.getTipo().rotulo(), c.getResumo(), parametros(c), c.getFPort(),
                Bytes.hex(Base64.getDecoder().decode(c.getDadosBase64())), c.isConfirmado(), c.getSeq(), c.getEstrategia(),
                c.getStatus(), c.getOrigem(), c.getOrigemId(), c.getTentativas(), c.getMaxTentativas(),
                c.getIntervaloTentativaS(), c.getFatorRecuo(), c.getAguardarRespostaS(), c.getValidadeEm(),
                c.getJanelaInicio(), c.getJanelaFim(), DiasSemana.lista(c.getDiasSemana()), c.getProximaTentativaEm(),
                c.getEnfileiradoEm(), c.getTransmitidoEm(), c.getConcluidoEm(), c.getResposta(), c.getDetalhe(),
                c.getCriadoPor(), c.getCriadoEm());
    }

    public <T> List<T> respostas(List<Comando> lista, Function<ComandoResponse, T> f) {
        Map<Long, String> n = nomes(lista);
        return lista.stream().map(c -> f.apply(resposta(c, n))).toList();
    }

    public ParametrosComando parametros(Comando c) {
        return lerParametros(c.getParametros());
    }

    public ParametrosComando lerParametros(String json) {
        return json == null || json.isBlank() ? ParametrosComando.vazio() : mapper.readValue(json, ParametrosComando.class);
    }

    public String escreverParametros(ParametrosComando p) {
        return mapper.writeValueAsString(p == null ? ParametrosComando.vazio() : p);
    }

    /** Resumo legível de um comando sem criá-lo (cenas, agendamentos e automações). */
    @Transactional(readOnly = true)
    public String descrever(Long dispositivoId, TipoComando tipo, ParametrosComando p) {
        return equipamentoService.contexto(dispositivoId).map(ctx -> {
            try {
                return equipamentoService.driver(ctx.equipamento()).codificar(tipo, p == null ? ParametrosComando.vazio() : p, ctx, 1).resumo();
            } catch (BusinessException e) {
                return tipo.rotulo() + " (" + e.getMessage() + ")";
            }
        }).orElse(tipo.rotulo());
    }

    /** Valida um comando sem criá-lo (lança 422 se o driver recusar). */
    @Transactional(readOnly = true)
    public void validar(Long dispositivoId, TipoComando tipo, ParametrosComando p) {
        ContextoEquipamento ctx = equipamentoService.contexto(dispositivoId).orElseThrow(() -> new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY, "Configure o modelo do firmware do dispositivo antes de usar comandos nele."));
        DriverModelo driver = equipamentoService.driver(ctx.equipamento());
        if (!driver.comandos().contains(tipo)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "\"" + tipo.rotulo() + "\" não é suportado pelo firmware " + ctx.equipamento().getModelo().rotulo() + ".");
        }
        driver.codificar(tipo, p == null ? ParametrosComando.vazio() : p, ctx, 1);
    }

    /** Se o comando mexe em algum relé crítico. */
    @Transactional(readOnly = true)
    public boolean critico(Long dispositivoId, TipoComando tipo, ParametrosComando p) {
        return equipamentoService.contexto(dispositivoId)
                .map(ctx -> relesAfetados(tipo, p == null ? ParametrosComando.vazio() : p, ctx).stream().anyMatch(Periferico::isCritico))
                .orElse(false);
    }

    private static String cortar(String s, int max) {
        return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
    }
}
