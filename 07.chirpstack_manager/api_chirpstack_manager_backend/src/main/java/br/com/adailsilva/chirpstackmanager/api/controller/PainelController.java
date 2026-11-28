package br.com.adailsilva.chirpstackmanager.api.controller;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.config.MqttProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.MqttStatusResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.PainelResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.UltimaLeitura;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Leitura;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import br.com.adailsilva.chirpstackmanager.api.mqtt.MqttEstado;
import br.com.adailsilva.chirpstackmanager.api.repository.AlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;

/** Indicadores do painel e situação da conexão MQTT. */
@RestController
@RequestMapping("/api/v1/painel")
public class PainelController {

    private final DispositivoRepository dispositivos;
    private final LeituraRepository leituras;
    private final AlertaRepository alertas;
    private final DispositivoService dispositivoService;
    private final MqttEstado mqtt;
    private final MqttProperties mqttProps;

    public PainelController(DispositivoRepository dispositivos, LeituraRepository leituras, AlertaRepository alertas,
                            DispositivoService dispositivoService, MqttEstado mqtt, MqttProperties mqttProps) {
        this.dispositivos = dispositivos;
        this.leituras = leituras;
        this.alertas = alertas;
        this.dispositivoService = dispositivoService;
        this.mqtt = mqtt;
        this.mqttProps = mqttProps;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    @Transactional(readOnly = true)
    public PainelResponse resumo() {
        long total = dispositivos.countByAtivoTrue();
        long online = dispositivos.countByAtivoTrueAndUltimoContatoEmAfter(dispositivoService.limiteOnline());
        List<Leitura> ultimas = leituras.findTop20ByOrderByRecebidoEmDesc();
        Map<Long, String> nomes = dispositivos.findAllById(ultimas.stream().map(Leitura::getDispositivoId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Dispositivo::getId, Dispositivo::getNome));
        return new PainelResponse(total, online, total - online,
                leituras.countByRecebidoEmAfter(Instant.now().minus(Duration.ofHours(24))),
                alertas.countByStatus(StatusAlerta.ABERTO), alertas.countByStatus(StatusAlerta.RECONHECIDO), status(),
                ultimas.stream().map(l -> new UltimaLeitura(l.getDispositivoId(), nomes.get(l.getDispositivoId()),
                        l.getRecebidoEm(), l.getRssi(), l.getSnr(), l.getObjeto())).toList());
    }

    @GetMapping("/mqtt")
    @PreAuthorize(Perfis.LEITURA)
    public MqttStatusResponse status() {
        return new MqttStatusResponse(mqttProps.habilitado(), mqtt.isConectado(), mqttProps.url(), mqttProps.topico(),
                mqtt.getConectadoEm(), mqtt.getUltimaMensagemEm(), mqtt.getMensagens(), mqtt.getUltimoErro(),
                mqtt.getUltimoErroEm());
    }
}
