package br.com.adailsilva.chirpstackmanager.api.mqtt;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/** Situação da conexão MQTT desta réplica, mostrada no painel. */
@Component
public class MqttEstado {

    private volatile boolean conectado;
    private volatile Instant conectadoEm;
    private volatile Instant ultimaMensagemEm;
    private volatile String ultimoErro;
    private volatile Instant ultimoErroEm;
    private final AtomicLong mensagens = new AtomicLong();

    void conectou() {
        conectado = true;
        conectadoEm = Instant.now();
    }

    void desconectou(String erro) {
        conectado = false;
        if (erro != null) {
            ultimoErro = erro;
            ultimoErroEm = Instant.now();
        }
    }

    void recebeu() {
        ultimaMensagemEm = Instant.now();
        mensagens.incrementAndGet();
    }

    public boolean isConectado() { return conectado; }
    public Instant getConectadoEm() { return conectadoEm; }
    public Instant getUltimaMensagemEm() { return ultimaMensagemEm; }
    public String getUltimoErro() { return ultimoErro; }
    public Instant getUltimoErroEm() { return ultimoErroEm; }
    public long getMensagens() { return mensagens.get(); }
}
