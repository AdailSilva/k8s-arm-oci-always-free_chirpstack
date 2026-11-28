package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EnergiaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Periferico;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.modelo.ContextoEquipamento;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumo de energia de um medidor no período: diferença da energia acumulada entre a primeira e
 * a última leitura (se o medidor foi zerado no meio, soma os trechos), custo pela tarifa do
 * equipamento e potência média/máxima.
 */
@Service
public class EnergiaService {

    private static final int MAX_LEITURAS = 20_000;

    private final LeituraRepository leituras;
    private final EquipamentoService equipamentos;
    private final ObjectMapper mapper;

    public EnergiaService(LeituraRepository leituras, EquipamentoService equipamentos, ObjectMapper mapper) {
        this.leituras = leituras;
        this.equipamentos = equipamentos;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public EnergiaResponse consumo(Long dispositivoId, int canal, Instant de, Instant ate) {
        Instant fim = ate == null ? Instant.now() : ate;
        Instant inicio = de == null ? fim.minus(Duration.ofDays(30)) : de;
        if (!inicio.isBefore(fim) || Duration.between(inicio, fim).toDays() > 400) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Período inválido (até 400 dias).");
        }
        ContextoEquipamento ctx = equipamentos.contexto(dispositivoId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Equipamento sem configuração."));
        Periferico m = ctx.periferico(TipoPeriferico.PZEM004T, canal).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND, "Medidor " + canal + " não cadastrado."));

        List<Leitura> ls = leituras.findByDispositivoIdAndRecebidoEmBetweenOrderByRecebidoEmAsc(dispositivoId, inicio, fim,
                PageRequest.of(0, MAX_LEITURAS));
        Long primeira = null;
        Long anterior = null;
        long consumoWh = 0;
        BigDecimal somaPot = BigDecimal.ZERO;
        BigDecimal maxPot = null;
        int n = 0;
        for (Leitura l : ls) {
            JsonNode med = medicao(l.getObjeto(), canal);
            if (med == null) {
                continue;
            }
            long e = med.path("energia").asLong();
            if (primeira == null) {
                primeira = e;
            } else if (e >= anterior) {
                consumoWh += e - anterior;
            } else {
                consumoWh += e; // medidor zerado: conta a partir do zero
            }
            anterior = e;
            BigDecimal p = med.path("potencia").decimalValue();
            somaPot = somaPot.add(p);
            maxPot = maxPot == null || p.compareTo(maxPot) > 0 ? p : maxPot;
            n++;
        }
        BigDecimal kwh = BigDecimal.valueOf(consumoWh).movePointLeft(3);
        BigDecimal tarifa = ctx.equipamento().getTarifaKwh();
        return new EnergiaResponse(canal, m.getNome(), inicio, fim, primeira, anterior, kwh, tarifa,
                tarifa == null ? null : kwh.multiply(tarifa).setScale(2, RoundingMode.HALF_UP),
                n == 0 ? null : somaPot.divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP), maxPot, n);
    }

    private JsonNode medicao(String objeto, int canal) {
        if (objeto == null || !objeto.contains("pzem")) {
            return null;
        }
        JsonNode lista = mapper.readTree(objeto).path("pzem");
        for (JsonNode i : lista) {
            if (i.path("canal").asInt(-1) == canal && i.path("energia").isNumber()) {
                return i;
            }
        }
        return null;
    }
}
