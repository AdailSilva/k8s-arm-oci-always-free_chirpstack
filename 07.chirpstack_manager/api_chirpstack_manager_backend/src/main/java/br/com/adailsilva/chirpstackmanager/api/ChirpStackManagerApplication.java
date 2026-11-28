package br.com.adailsilva.chirpstackmanager.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ChirpStack Manager API.
 * <p>
 * Backend-for-frontend que expõe, com autenticação própria e controle de acesso por perfil,
 * todas as operações da REST API do ChirpStack v4. A API key do ChirpStack fica somente aqui.
 * <p>
 * Também recebe os eventos dos dispositivos LoRaWAN publicados pelo ChirpStack v4 no MQTT
 * ({@code application/+/device/+/event/+}), grava leituras e o estado de cada dispositivo e
 * avalia regras de alerta.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChirpStackManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChirpStackManagerApplication.class, args);
    }
}
