package br.com.adailsilva.chirpstackmanager.api.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.config.RetencaoProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.DispositivoUpdateRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.Ponto;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.SerieResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.EventoDispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EventoDispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Consulta e edição dos dispositivos e de suas leituras e eventos. */
@Service
public class DispositivoService {

    private static final int MAX_PONTOS = 2000;

    private final DispositivoRepository dispositivos;
    private final LeituraRepository leituras;
    private final EventoDispositivoRepository eventos;
    private final RetencaoProperties retencao;
    private final ObjectMapper mapper;

    public DispositivoService(DispositivoRepository dispositivos, LeituraRepository leituras,
                              EventoDispositivoRepository eventos, RetencaoProperties retencao, ObjectMapper mapper) {
        this.dispositivos = dispositivos;
        this.leituras = leituras;
        this.eventos = eventos;
        this.retencao = retencao;
        this.mapper = mapper;
    }

    /** Antes disso o dispositivo é "sem comunicação". */
    public Instant limiteOnline() {
        return Instant.now().minus(Duration.ofMinutes(retencao.semComunicacaoMinutos()));
    }

    @Transactional(readOnly = true)
    public Page<Dispositivo> listar(String busca, boolean inativos, int pagina, int tamanho) {
        String b = busca == null || busca.isBlank() ? null : busca.trim();
        return dispositivos.buscar(b, inativos, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 500),
                Sort.by("nome")));
    }

    @Transactional(readOnly = true)
    public Dispositivo buscar(Long id) {
        return dispositivos.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado."));
    }

    @Transactional(readOnly = true)
    public Map<Long, Dispositivo> porIds(Set<Long> ids) {
        return dispositivos.findAllById(ids).stream().collect(Collectors.toMap(Dispositivo::getId, Function.identity()));
    }

    @Transactional
    public Dispositivo atualizar(Long id, DispositivoUpdateRequest req) {
        Dispositivo d = buscar(id);
        d.setDescricao(req.descricao() == null || req.descricao().isBlank() ? null : req.descricao().trim());
        if (req.ativo() != null) {
            d.setAtivo(req.ativo());
        }
        d.setAtualizadoEm(Instant.now());
        return dispositivos.save(d);
    }

    /** Excluir apaga o dispositivo e TODAS as leituras, eventos e alertas dele (FK em cascata). */
    @Transactional
    public void excluir(Long id) {
        dispositivos.delete(buscar(id));
    }

    @Transactional(readOnly = true)
    public Page<Leitura> leituras(Long id, Instant de, Instant ate, int pagina, int tamanho) {
        buscar(id);
        Instant[] p = periodo(de, ate);
        return leituras.findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmDesc(id, p[0], p[1],
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 500)));
    }

    @Transactional(readOnly = true)
    public Page<EventoDispositivo> eventos(Long id, Instant de, Instant ate, int pagina, int tamanho) {
        buscar(id);
        Instant[] p = periodo(de, ate);
        return eventos.findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmDesc(id, p[0], p[1],
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanho, 1), 500)));
    }

    /**
     * Séries numéricas do período para o gráfico: rssi, snr e cada campo numérico do objeto
     * decodificado (caminhos com pontos). Até {@value #MAX_PONTOS} leituras.
     */
    @Transactional(readOnly = true)
    public List<SerieResponse> series(Long id, Instant de, Instant ate) {
        buscar(id);
        Instant[] p = periodo(de, ate);
        List<Leitura> lista = leituras.findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmAsc(id, p[0], p[1],
                PageRequest.of(0, MAX_PONTOS));
        Map<String, List<Ponto>> series = new java.util.LinkedHashMap<>();
        Set<String> ordem = new LinkedHashSet<>();
        for (Leitura l : lista) {
            if (l.getObjeto() != null) {
                try {
                    achatar("", mapper.readTree(l.getObjeto()), l.getRecebidoEm(), series, ordem);
                } catch (RuntimeException ignorado) {
                    // objeto ilegível: só as séries de rádio
                }
            }
            if (l.getRssi() != null) {
                series.computeIfAbsent("rssi", k -> new ArrayList<>()).add(new Ponto(l.getRecebidoEm(), BigDecimal.valueOf(l.getRssi())));
            }
            if (l.getSnr() != null) {
                series.computeIfAbsent("snr", k -> new ArrayList<>()).add(new Ponto(l.getRecebidoEm(), l.getSnr()));
            }
        }
        List<SerieResponse> out = new ArrayList<>();
        for (String c : ordem) {
            out.add(new SerieResponse(c, series.get(c)));
        }
        if (series.containsKey("rssi")) {
            out.add(new SerieResponse("rssi", series.get("rssi")));
        }
        if (series.containsKey("snr")) {
            out.add(new SerieResponse("snr", series.get("snr")));
        }
        return out;
    }

    private static void achatar(String prefixo, JsonNode n, Instant t, Map<String, List<Ponto>> series, Set<String> ordem) {
        if (n.isObject()) {
            for (var e : n.properties()) {
                achatar(prefixo.isEmpty() ? e.getKey() : prefixo + "." + e.getKey(), e.getValue(), t, series, ordem);
            }
        } else if (n.isArray()) {
            // Lista de medidores/canais: a chave é o "canal" do item (ou a posição), ex.: pzem.0.potencia
            for (int i = 0; i < n.size(); i++) {
                JsonNode item = n.get(i);
                String chave = item.isObject() && item.path("canal").isNumber() ? item.path("canal").asString() : String.valueOf(i);
                String p = prefixo.isEmpty() ? chave : prefixo + "." + chave;
                if (item.isObject()) {
                    for (var e : item.properties()) {
                        if (!e.getKey().equals("canal")) {
                            achatar(p + "." + e.getKey(), e.getValue(), t, series, ordem);
                        }
                    }
                } else {
                    achatar(p, item, t, series, ordem);
                }
            }
        } else if (n.isNumber() && !prefixo.isEmpty()) {
            ordem.add(prefixo);
            series.computeIfAbsent(prefixo, k -> new ArrayList<>()).add(new Ponto(t, n.decimalValue()));
        } else if (n.isBoolean() && !prefixo.isEmpty()) {
            ordem.add(prefixo);
            series.computeIfAbsent(prefixo, k -> new ArrayList<>()).add(new Ponto(t, n.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO));
        }
    }

    /** Sem período = últimas 24 h. Período máximo de 400 dias. */
    private static Instant[] periodo(Instant de, Instant ate) {
        Instant fim = ate != null ? ate : Instant.now();
        Instant inicio = de != null ? de : fim.minus(Duration.ofHours(24));
        if (inicio.isAfter(fim)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "O início do período é depois do fim.");
        }
        if (Duration.between(inicio, fim).toDays() > 400) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Período máximo: 400 dias.");
        }
        return new Instant[] {inicio, fim};
    }
}
