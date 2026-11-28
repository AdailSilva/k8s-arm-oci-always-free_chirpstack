package br.com.adailsilva.chirpstackmanager.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexão com o broker MQTT do ChirpStack v4 (prefixo {@code app.mqtt}).
 *
 * @param habilitado liga a assinatura (desligada em development/staging sem broker)
 * @param url        {@code ssl://host:8883} ou {@code tcp://host:1883}
 * @param usuario    usuário do broker
 * @param senha      senha do broker
 * @param caArquivo  CA do broker em PEM; vazio = CAs da JVM
 * @param clientId   identificador do cliente (recebe um sufixo por réplica)
 * @param topico     filtro assinado, padrão {@code application/+/device/+/event/+}
 * @param qos        0, 1 ou 2
 */
@ConfigurationProperties(prefix = "app.mqtt")
public record MqttProperties(
        boolean habilitado,
        String url,
        String usuario,
        String senha,
        String caArquivo,
        String clientId,
        String topico,
        Integer qos) {

    public MqttProperties {
        if (clientId == null || clientId.isBlank()) {
            clientId = "chirpstack-manager";
        }
        if (topico == null || topico.isBlank()) {
            topico = "application/+/device/+/event/+";
        }
        if (qos == null || qos < 0 || qos > 2) {
            qos = 1;
        }
    }
}
