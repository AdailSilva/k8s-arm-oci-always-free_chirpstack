package br.com.adailsilva.chirpstackmanager.api.atuacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AgendamentoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EquipamentoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ParametrosComando;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PerifericoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PoliticaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AcaoRele;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.AlvoAcao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Recorrencia;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.mqtt.EventoChirpStackService;
import br.com.adailsilva.chirpstackmanager.api.repository.AlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.ComandoTentativaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.LeituraRepository;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.AgendamentoService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.ComandoService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.EquipamentoService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.FilaComandosService;
import br.com.adailsilva.chirpstackmanager.api.service.chirpstack.ChirpStackClient;
import br.com.adailsilva.chirpstackmanager.api.service.chirpstack.Query;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Fila de comandos de ponta a ponta com o ChirpStack simulado: telemetria V1 decodificada pelo
 * servidor, comando enfileirado, transmitido (txack) e provado pela resposta com o mesmo seq; nova
 * tentativa sem resposta e falha com alerta; carga crítica; agendamento semanal.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fila;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.security.jwt-secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "app.mqtt.habilitado=false",
        "app.comandos.eventos-assincronos=false",
        "app.comandos.ciclo-ms=3600000"
})
class FilaComandosTests {

    private static final String DEV = "a0a1a2a3a4a5a6a7";
    private static final String APP = "7f3c1e2a-0000-4000-8000-000000000009";
    private static final String BASE = "application/" + APP + "/device/" + DEV + "/event/";
    private static final AtomicInteger SEQ_EVENTO = new AtomicInteger();

    @MockitoBean ChirpStackClient cs;
    @Autowired EventoChirpStackService eventos;
    @Autowired DispositivoRepository dispositivos;
    @Autowired LeituraRepository leituras;
    @Autowired EquipamentoService equipamentos;
    @Autowired ComandoService comandoService;
    @Autowired FilaComandosService fila;
    @Autowired ComandoRepository comandos;
    @Autowired ComandoTentativaRepository tentativas;
    @Autowired AlertaRepository alertas;
    @Autowired DispositivoService dispositivoService;
    @Autowired AgendamentoService agendamentos;

    private final AtomicInteger itens = new AtomicInteger();

    @BeforeEach
    void chirpstack() {
        when(cs.post(eq("/api/devices/{devEui}/queue"), any(), any())).thenAnswer(inv -> {
            ObjectNode r = JsonNodeFactory.instance.objectNode();
            r.put("id", "00000000-0000-4000-8000-" + String.format("%012d", itens.incrementAndGet()));
            return r;
        });
        when(cs.get(anyString(), any(Query.class), any())).thenAnswer(inv -> JsonNodeFactory.instance.objectNode().putArray("result").removeAll());
    }

    private void up(int porta, String base64) {
        eventos.processar(BASE + "up", """
                {"deduplicationId":"%s","time":"%s",
                 "deviceInfo":{"applicationName":"Cargas","deviceName":"Quadro 1","devEui":"%s"},
                 "fCnt":%d,"fPort":%d,"data":"%s","rxInfo":[{"gatewayId":"aa00000000000001","rssi":-70,"snr":8}]}
                """.formatted(java.util.UUID.randomUUID(), Instant.now(), DEV, SEQ_EVENTO.incrementAndGet(), porta, base64));
    }

    private void txack(String item) {
        eventos.processar(BASE + "txack", """
                {"time":"%s","deviceInfo":{"devEui":"%s"},"queueItemId":"%s","gatewayId":"aa00000000000001"}
                """.formatted(Instant.now(), DEV, item));
    }

    private Comando recarregar(Comando c) {
        return comandos.findById(c.getId()).orElseThrow();
    }

    @Test
    void cicloCompleto() {
        // Primeiro uplink cria o dispositivo; depois o equipamento V1 com 2 relés (o 1 crítico) e 1 PZEM
        up(20, "AQADAgABEAASCPwAAAH0AAAEsAAAMDkCWF8AMQABAQ==");
        Dispositivo d = dispositivos.findByDevEui(DEV).orElseThrow();
        equipamentos.salvar(d.getId(), new EquipamentoRequest(ModeloFirmware.ADAILSILVA_V1, true, new java.math.BigDecimal("0.85"),
                List.of(new PerifericoRequest(TipoPeriferico.RELE, 0, "Bomba", null, null, false),
                        new PerifericoRequest(TipoPeriferico.RELE, 1, "Forno", null, null, true),
                        new PerifericoRequest(TipoPeriferico.PZEM004T, 0, "Geral", null, null, false))), "teste");

        // Telemetria agora é decodificada pelo servidor: leitura com objeto, relés e medidor atualizados
        up(20, "AQADAgABEAASCPwAAAH0AAAEsAAAMDkCWF8AMQABAQ==");
        var eq = equipamentos.buscar(d.getId());
        var rele0 = eq.perifericos().stream().filter(p -> p.tipo() == TipoPeriferico.RELE && p.canal() == 0).findFirst().orElseThrow();
        assertThat(rele0.estadoReportado()).isTrue();
        var pzem = eq.perifericos().stream().filter(p -> p.tipo() == TipoPeriferico.PZEM004T).findFirst().orElseThrow();
        assertThat(pzem.leitura()).contains("\"potencia\":120.0").contains("custoTotal");
        assertThat(dispositivoService.series(d.getId(), null, null)).anyMatch(s -> s.campo().equals("pzem.0.potencia"));

        // Carga crítica exige confirmação
        assertThatThrownBy(() -> comandoService.criar(d.getId(), TipoComando.RELE_DEFINIR, ParametrosComando.rele(1, AcaoRele.LIGAR),
                null, OrigemComando.MANUAL, null, "op", false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Carga crítica");

        // Desligar o relé 0: enfileira, transmite e a resposta com o mesmo seq prova a execução
        Comando c = comandoService.criar(d.getId(), TipoComando.RELE_DEFINIR, ParametrosComando.rele(0, AcaoRele.DESLIGAR),
                null, OrigemComando.MANUAL, null, "op", false);
        assertThat(c.getStatus()).isEqualTo(StatusComando.PENDENTE);
        assertThat(c.getSeq()).isEqualTo(1);
        fila.processarCiclo(Instant.now());
        c = recarregar(c);
        assertThat(c.getStatus()).isEqualTo(StatusComando.ENFILEIRADO);
        txack(c.getChirpstackItemId());
        assertThat(recarregar(c).getStatus()).isEqualTo(StatusComando.TRANSMITIDO);
        up(11, java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 0x20, 0, 2, 0, 0 }));
        c = recarregar(c);
        assertThat(c.getStatus()).isEqualTo(StatusComando.EXECUTADO);
        assertThat(c.getResposta()).contains("\"statusNome\":\"OK\"");
        assertThat(equipamentos.buscar(d.getId()).perifericos().stream()
                .filter(p -> p.tipo() == TipoPeriferico.RELE && p.canal() == 0).findFirst().orElseThrow().estadoReportado()).isFalse();

        // Sem resposta: nova tentativa com o mesmo seq; esgotadas, FALHOU e abre o alerta
        Comando l = comandoService.criar(d.getId(), TipoComando.RELE_LER, null,
                new PoliticaRequest(2, 60, java.math.BigDecimal.ONE, 30, 600, null, null, null), OrigemComando.MANUAL, null, "op", false);
        Instant t0 = Instant.now();
        fila.processarCiclo(t0);
        txack(recarregar(l).getChirpstackItemId());
        fila.processarCiclo(Instant.now().plusSeconds(31));
        l = recarregar(l);
        assertThat(l.getStatus()).isEqualTo(StatusComando.PENDENTE);
        assertThat(l.getTentativas()).isEqualTo(1);
        Instant t1 = l.getProximaTentativaEm();
        fila.processarCiclo(t1);
        l = recarregar(l);
        assertThat(l.getStatus()).isEqualTo(StatusComando.ENFILEIRADO);
        assertThat(l.getTentativas()).isEqualTo(2);
        assertThat(l.getSeq()).isEqualTo(2); // mesmo seq na nova tentativa
        txack(l.getChirpstackItemId());
        fila.processarCiclo(Instant.now().plusSeconds(31));
        l = recarregar(l);
        assertThat(l.getStatus()).isEqualTo(StatusComando.FALHOU);
        assertThat(tentativas.findByComandoIdOrderByNumeroAsc(l.getId())).hasSize(2);
        assertThat(alertas.findAll()).anyMatch(a -> a.getDispositivoId().equals(d.getId()) && a.getStatus() == StatusAlerta.ABERTO
                && a.getMensagem().contains("Comando não executado"));

        // Um comando executado resolve o alerta
        Comando s = comandoService.criar(d.getId(), TipoComando.STATUS, null, null, OrigemComando.MANUAL, null, "op", false);
        fila.processarCiclo(Instant.now());
        txack(recarregar(s).getChirpstackItemId());
        byte[] st = new byte[3 + 19];
        st[0] = (byte) (int) s.getSeq();
        st[1] = 0x01;
        st[3] = 1;
        st[3 + 12] = 'A'; // classe
        st[3 + 14] = 2;   // quantidade de relés
        up(11, java.util.Base64.getEncoder().encodeToString(st));
        assertThat(recarregar(s).getStatus()).isEqualTo(StatusComando.EXECUTADO);
        assertThat(equipamentos.buscar(d.getId()).status()).contains("\"classe\":\"A\"");
        assertThat(alertas.findAll()).noneMatch(a -> a.getDispositivoId().equals(d.getId()) && a.getStatus() == StatusAlerta.ABERTO);

        // Agendamento semanal: próxima execução calculada e, vencida, vira comando na fila
        var ag = agendamentos.criar(new AgendamentoRequest("Bomba manhã", AlvoAcao.COMANDO, d.getId(), TipoComando.RELE_DEFINIR,
                ParametrosComando.rele(0, AcaoRele.LIGAR), null, Recorrencia.SEMANAL, null, LocalTime.of(6, 30), null, 30, 3,
                true, false), "op");
        assertThat(ag.proximaExecucaoEm()).isAfter(Instant.now());
        long antes = comandos.count();
        agendamentos.executarVencidos(ag.proximaExecucaoEm().plusSeconds(1));
        assertThat(comandos.count()).isEqualTo(antes + 1);
        assertThat(agendamentos.listar(d.getId()).get(0).proximaExecucaoEm()).isAfter(ag.proximaExecucaoEm());
    }
}
