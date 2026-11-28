package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Disparo de uma regra para um dispositivo. Fica ABERTO até ser reconhecido ou até o valor voltar ao
 * normal (RESOLVIDO). Enquanto houver um ABERTO/RECONHECIDO para a mesma regra e dispositivo, não
 * nasce outro.
 */
@Entity
@Table(name = "tb_alerta")
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "regra_id", nullable = false)
    private Long regraId;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private StatusAlerta status = StatusAlerta.ABERTO;

    @Column(name = "valor", precision = 14, scale = 4)
    private BigDecimal valor;

    @Column(name = "mensagem", nullable = false, length = 300)
    private String mensagem;

    @Column(name = "disparado_em", nullable = false)
    private Instant disparadoEm;

    @Column(name = "reconhecido_em")
    private Instant reconhecidoEm;

    @Column(name = "reconhecido_por", length = 160)
    private String reconhecidoPor;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    @Column(name = "email_enviado", nullable = false)
    private boolean emailEnviado;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRegraId() { return regraId; }
    public void setRegraId(Long regraId) { this.regraId = regraId; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public StatusAlerta getStatus() { return status; }
    public void setStatus(StatusAlerta status) { this.status = status; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public Instant getDisparadoEm() { return disparadoEm; }
    public void setDisparadoEm(Instant disparadoEm) { this.disparadoEm = disparadoEm; }
    public Instant getReconhecidoEm() { return reconhecidoEm; }
    public void setReconhecidoEm(Instant reconhecidoEm) { this.reconhecidoEm = reconhecidoEm; }
    public String getReconhecidoPor() { return reconhecidoPor; }
    public void setReconhecidoPor(String reconhecidoPor) { this.reconhecidoPor = reconhecidoPor; }
    public Instant getResolvidoEm() { return resolvidoEm; }
    public void setResolvidoEm(Instant resolvidoEm) { this.resolvidoEm = resolvidoEm; }
    public boolean isEmailEnviado() { return emailEnviado; }
    public void setEmailEnviado(boolean emailEnviado) { this.emailEnviado = emailEnviado; }
}
