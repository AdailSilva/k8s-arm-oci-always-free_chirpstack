package br.com.adailsilva.chirpstackmanager.api.mqtt;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.adailsilva.chirpstackmanager.api.config.MqttProperties;
import jakarta.annotation.PreDestroy;

/**
 * Assina os eventos do ChirpStack v4 no MQTT e entrega cada mensagem ao {@link EventoChirpStackService}.
 * <p>
 * Sessão limpa e {@code clientId} único por réplica: cada réplica recebe as mensagens, e a gravação é
 * idempotente (o uplink pelo {@code deduplicationId}). Com reconexão automática do Paho; se a
 * primeira conexão falhar (broker ainda subindo), uma tarefa tenta de novo a cada 30 s.
 * Uma mensagem com erro é registrada no log e descartada: nunca derruba a conexão.
 */
@Component
public class MqttConsumidor implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttConsumidor.class);

    private final MqttProperties props;
    private final EventoChirpStackService eventos;
    private final MqttEstado estado;
    private volatile MqttClient cliente;

    public MqttConsumidor(MqttProperties props, EventoChirpStackService eventos, MqttEstado estado) {
        this.props = props;
        this.eventos = eventos;
        this.estado = estado;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!props.habilitado()) {
            log.info("MQTT desligado (app.mqtt.habilitado=false): nenhum evento do ChirpStack será recebido.");
            return;
        }
        conectar();
    }

    /** Nova tentativa quando a primeira conexão falhou (depois dela, o Paho reconecta sozinho). */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void vigiar() {
        if (props.habilitado() && (cliente == null || !cliente.isConnected()) && !estado.isConectado()) {
            conectar();
        }
    }

    private synchronized void conectar() {
        if (cliente != null && cliente.isConnected()) {
            return;
        }
        try {
            if (cliente == null) {
                String id = props.clientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
                cliente = new MqttClient(props.url(), id, new MemoryPersistence());
                cliente.setCallback(this);
            }
            MqttConnectOptions o = new MqttConnectOptions();
            o.setCleanSession(true);
            o.setAutomaticReconnect(true);
            o.setKeepAliveInterval(30);
            o.setConnectionTimeout(15);
            if (props.usuario() != null && !props.usuario().isBlank()) {
                o.setUserName(props.usuario());
                o.setPassword(props.senha() == null ? new char[0] : props.senha().toCharArray());
            }
            if (props.url().startsWith("ssl://") && props.caArquivo() != null && !props.caArquivo().isBlank()) {
                if (Files.isReadable(Path.of(props.caArquivo()))) {
                    o.setSocketFactory(MqttTls.confiandoEm(props.caArquivo()));
                } else {
                    log.warn("CA do MQTT não encontrada em {}: usando as CAs da JVM.", props.caArquivo());
                }
            }
            cliente.connect(o);
        } catch (Exception e) {
            estado.desconectou(e.getMessage());
            log.warn("Não foi possível conectar ao MQTT {}: {}", props.url(), e.getMessage());
        }
    }

    @Override
    public void connectComplete(boolean reconexao, String servidor) {
        try {
            cliente.subscribe(props.topico(), props.qos());
            estado.conectou();
            log.info("MQTT {} em {}; assinando {}", reconexao ? "reconectado" : "conectado", servidor, props.topico());
        } catch (MqttException e) {
            estado.desconectou(e.getMessage());
            log.warn("Falha ao assinar {}: {}", props.topico(), e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable causa) {
        estado.desconectou(causa == null ? "conexão perdida" : causa.getMessage());
        log.warn("Conexão MQTT perdida: {}", causa == null ? "?" : causa.getMessage());
    }

    @Override
    public void messageArrived(String topico, MqttMessage mensagem) {
        estado.recebeu();
        try {
            eventos.processar(topico, new String(mensagem.getPayload(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Mensagem MQTT descartada ({}): {}", topico, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // só assina
    }

    @PreDestroy
    public void encerrar() {
        MqttClient c = cliente;
        if (c != null) {
            try {
                if (c.isConnected()) {
                    c.disconnect(2000);
                }
                c.close();
            } catch (MqttException ignorado) {
                // encerrando
            }
        }
    }
}
