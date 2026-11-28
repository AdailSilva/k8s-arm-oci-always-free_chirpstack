package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.config.RetencaoProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EventoDispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;

/**
 * Apaga leituras, eventos e comandos já encerrados mais antigos que {@code app.retencao.leituras-dias},
 * toda madrugada.
 */
@Service
public class RetencaoService {

    private static final Logger log = LoggerFactory.getLogger(RetencaoService.class);

    private final LeituraRepository leituras;
    private final EventoDispositivoRepository eventos;
    private final RetencaoProperties props;
    private final ComandoRepository comandos;

    public RetencaoService(LeituraRepository leituras, EventoDispositivoRepository eventos, RetencaoProperties props,
                           ComandoRepository comandos) {
        this.comandos = comandos;
        this.leituras = leituras;
        this.eventos = eventos;
        this.props = props;
    }

    @Scheduled(cron = "0 50 3 * * *", zone = "${app.fuso-horario:America/Fortaleza}")
    @Transactional
    public void apagarAntigas() {
        Instant limite = Instant.now().minus(Duration.ofDays(props.leiturasDias()));
        int l = leituras.apagarAntesDe(limite);
        int e = eventos.apagarAntesDe(limite);
        int c = comandos.apagarAntesDe(limite, EnumSet.of(StatusComando.EXECUTADO, StatusComando.FALHOU,
                StatusComando.EXPIRADO, StatusComando.CANCELADO));
        if (l + e + c > 0) {
            log.info("Retenção: {} leitura(s), {} evento(s) e {} comando(s) com mais de {} dias apagados.", l, e, c,
                    props.leiturasDias());
        }
    }
}
