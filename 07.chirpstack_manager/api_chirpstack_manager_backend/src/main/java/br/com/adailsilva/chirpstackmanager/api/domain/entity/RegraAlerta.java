package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.Operador;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Severidade;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;
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
 * Regra de alerta. {@code VALOR}: compara {@code campo} da leitura com {@code limite} pelo
 * {@code operador}. {@code SEM_COMUNICACAO}: dispara após {@code limite} minutos sem mensagem.
 * {@code dispositivoId} nulo = vale para todos os dispositivos.
 */
@Entity
@Table(name = "tb_regra_alerta")
public class RegraAlerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoRegra tipo;

    @Column(name = "dispositivo_id")
    private Long dispositivoId;

    @Column(name = "campo", length = 120)
    private String campo;

    @Enumerated(EnumType.STRING)
    @Column(name = "operador", length = 3)
    private Operador operador;

    @Column(name = "limite", nullable = false, precision = 14, scale = 4)
    private BigDecimal limite;

    @Enumerated(EnumType.STRING)
    @Column(name = "severidade", nullable = false, length = 10)
    private Severidade severidade = Severidade.AVISO;

    @Column(name = "notificar_email", nullable = false)
    private boolean notificarEmail;

    @Column(name = "destinatarios", length = 500)
    private String destinatarios;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoRegra getTipo() { return tipo; }
    public void setTipo(TipoRegra tipo) { this.tipo = tipo; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public String getCampo() { return campo; }
    public void setCampo(String campo) { this.campo = campo; }
    public Operador getOperador() { return operador; }
    public void setOperador(Operador operador) { this.operador = operador; }
    public BigDecimal getLimite() { return limite; }
    public void setLimite(BigDecimal limite) { this.limite = limite; }
    public Severidade getSeveridade() { return severidade; }
    public void setSeveridade(Severidade severidade) { this.severidade = severidade; }
    public boolean isNotificarEmail() { return notificarEmail; }
    public void setNotificarEmail(boolean notificarEmail) { this.notificarEmail = notificarEmail; }
    public String getDestinatarios() { return destinatarios; }
    public void setDestinatarios(String destinatarios) { this.destinatarios = destinatarios; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
}
