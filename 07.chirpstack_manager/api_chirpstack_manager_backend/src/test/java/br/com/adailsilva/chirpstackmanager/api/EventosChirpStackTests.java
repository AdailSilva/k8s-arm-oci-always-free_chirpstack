package br.com.adailsilva.chirpstackmanager.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.RegraAlertaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.SerieResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Alerta;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Operador;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Severidade;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;
import br.com.adailsilva.chirpstackmanager.api.mqtt.EventoChirpStackService;
import br.com.adailsilva.chirpstackmanager.api.repository.AlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.EventoDispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;
import br.com.adailsilva.chirpstackmanager.api.service.RegraAlertaService;

/**
 * Eventos do ChirpStack v4 no formato JSON da integração MQTT: dispositivo nasce na primeira
 * mensagem, uplink repetido não duplica, status grava a bateria e as regras abrem e resolvem alertas.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventos;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.security.jwt-secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "app.mqtt.habilitado=false"
})
class EventosChirpStackTests {

    private static final String DEV = "0102030405060708";
    private static final String TOPICO_UP = "application/7f3c1e2a-0000-4000-8000-000000000001/device/" + DEV + "/event/up";

    @Autowired EventoChirpStackService eventos;
    @Autowired DispositivoRepository dispositivos;
    @Autowired LeituraRepository leituras;
    @Autowired EventoDispositivoRepository eventosRepo;
    @Autowired AlertaRepository alertas;
    @Autowired RegraAlertaService regras;
    @Autowired DispositivoService dispositivoService;

    private static String up(String dedup, int fCnt, double temperatura, int rssi) {
        return """
                {"deduplicationId":"%s","time":"%s",
                 "deviceInfo":{"tenantName":"ChirpStack","applicationId":"7f3c1e2a-0000-4000-8000-000000000001",
                   "applicationName":"Sensores","deviceProfileName":"Perfil TH","deviceName":"Sensor Sala",
                   "devEui":"%s","tags":{"local":"sala"}},
                 "devAddr":"0011aabb","dr":5,"fCnt":%d,"fPort":10,"confirmed":false,"data":"AQI=",
                 "object":{"temperatura":%s,"sensor":{"umidade":55}},
                 "rxInfo":[{"gatewayId":"aa00000000000001","rssi":-110,"snr":-3.5},
                           {"gatewayId":"aa00000000000002","rssi":%d,"snr":7.25}],
                 "txInfo":{"frequency":916800000,"modulation":{"lora":{"bandwidth":125000,"spreadingFactor":7}}}}
                """.formatted(dedup, Instant.now().toString(), DEV, fCnt, temperatura, rssi);
    }

    @Test
    void fluxoCompleto() {
        regras.criar(new RegraAlertaRequest("Temperatura alta", TipoRegra.VALOR, null, "temperatura", Operador.GT,
                new BigDecimal("30"), Severidade.CRITICO, false, null, true), "teste@x.com");

        eventos.processar(TOPICO_UP, up("a1b2c3d4-0000-4000-8000-000000000001", 1, 25.5, -80));
        Dispositivo d = dispositivos.findByDevEui(DEV).orElseThrow();
        assertThat(d.getNome()).isEqualTo("Sensor Sala");
        assertThat(d.getAplicacaoNome()).isEqualTo("Sensores");
        assertThat(d.getUltimoRssi()).isEqualTo(-80); // melhor gateway
        assertThat(d.getUltimoGateway()).isEqualTo("aa00000000000002");
        assertThat(d.getTotalUplinks()).isEqualTo(1);

        // Repetido (mesmo deduplicationId): não duplica
        eventos.processar(TOPICO_UP, up("a1b2c3d4-0000-4000-8000-000000000001", 1, 25.5, -80));
        assertThat(leituras.count()).isEqualTo(1);
        assertThat(alertas.count()).isZero();

        // Acima do limite: abre alerta; repetir não abre outro
        eventos.processar(TOPICO_UP, up("a1b2c3d4-0000-4000-8000-000000000002", 2, 31.2, -81));
        eventos.processar(TOPICO_UP, up("a1b2c3d4-0000-4000-8000-000000000003", 3, 32.0, -82));
        List<Alerta> abertos = alertas.findAll();
        assertThat(abertos).hasSize(1);
        assertThat(abertos.get(0).getStatus()).isEqualTo(StatusAlerta.ABERTO);
        assertThat(abertos.get(0).getMensagem()).contains("Sensor Sala").contains("31.2");

        // Voltou ao normal: resolve
        eventos.processar(TOPICO_UP, up("a1b2c3d4-0000-4000-8000-000000000004", 4, 22.0, -79));
        assertThat(alertas.findFirstByRegraIdAndDispositivoIdAndStatusIn(abertos.get(0).getRegraId(), d.getId(),
                EnumSet.of(StatusAlerta.ABERTO, StatusAlerta.RECONHECIDO))).isEmpty();
        assertThat(alertas.findById(abertos.get(0).getId()).orElseThrow().getStatus()).isEqualTo(StatusAlerta.RESOLVIDO);

        // Status: bateria
        eventos.processar("application/x/device/" + DEV + "/event/status",
                "{\"deviceInfo\":{\"deviceName\":\"Sensor Sala\"},\"margin\":10,\"externalPowerSource\":false,"
                        + "\"batteryLevelUnavailable\":false,\"batteryLevel\":87.4}");
        assertThat(dispositivos.findByDevEui(DEV).orElseThrow().getUltimaBateria()).isEqualTo(87);

        // Log: vira evento
        eventos.processar("application/x/device/" + DEV + "/event/log",
                "{\"deviceInfo\":{\"deviceName\":\"Sensor Sala\"},\"level\":\"ERROR\",\"code\":\"UPLINK_CODEC\","
                        + "\"description\":\"falha no codec\"}");
        assertThat(eventosRepo.findAll()).anyMatch(e -> "log".equals(e.getTipo()) && "ERROR".equals(e.getNivel()));

        // Tópico fora do padrão é ignorado
        eventos.processar("gateway/aa00000000000001/event/up", "{}");

        // Séries do gráfico: campos numéricos do objeto + rádio
        List<SerieResponse> series = dispositivoService.series(d.getId(), null, null);
        assertThat(series).extracting(SerieResponse::campo).contains("temperatura", "sensor.umidade", "rssi", "snr");
        assertThat(series.stream().filter(s -> s.campo().equals("temperatura")).findFirst().orElseThrow().pontos()).hasSize(4);
    }
}
