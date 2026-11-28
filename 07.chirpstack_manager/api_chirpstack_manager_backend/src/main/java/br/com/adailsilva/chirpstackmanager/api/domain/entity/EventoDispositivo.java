package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Evento de um dispositivo que não é uplink: join, status, ack, txack, log, location, integration.
 */
@Entity
@Table(name = "tb_evento_dispositivo")
public class EventoDispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Column(name = "nivel", length = 10)
    private String nivel;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Instant getRecebidoEm() { return recebidoEm; }
    public void setRecebidoEm(Instant recebidoEm) { this.recebidoEm = recebidoEm; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
