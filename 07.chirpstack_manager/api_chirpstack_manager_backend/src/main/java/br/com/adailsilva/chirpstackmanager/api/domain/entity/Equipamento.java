package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.ModeloFirmware;

/** Como falar com o firmware de um dispositivo (modelo) e o contador de seq dos comandos V1. */
@Entity
@Table(name = "tb_equipamento")
public class Equipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "modelo", nullable = false, length = 30)
    private ModeloFirmware modelo;

    @Column(name = "ultimo_seq", nullable = false)
    private int ultimoSeq;

    @Column(name = "reconciliar", nullable = false)
    private boolean reconciliar;

    @Column(name = "tarifa_kwh", precision = 10, scale = 4)
    private BigDecimal tarifaKwh;

    @Column(name = "status_json", columnDefinition = "TEXT")
    private String statusJson;

    @Column(name = "status_em")
    private Instant statusEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    @Version
    @Column(name = "versao", nullable = false)
    private long versao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public ModeloFirmware getModelo() { return modelo; }
    public void setModelo(ModeloFirmware modelo) { this.modelo = modelo; }
    public int getUltimoSeq() { return ultimoSeq; }
    public void setUltimoSeq(int ultimoSeq) { this.ultimoSeq = ultimoSeq; }
    public boolean isReconciliar() { return reconciliar; }
    public void setReconciliar(boolean reconciliar) { this.reconciliar = reconciliar; }
    public BigDecimal getTarifaKwh() { return tarifaKwh; }
    public void setTarifaKwh(BigDecimal tarifaKwh) { this.tarifaKwh = tarifaKwh; }
    public String getStatusJson() { return statusJson; }
    public void setStatusJson(String statusJson) { this.statusJson = statusJson; }
    public Instant getStatusEm() { return statusEm; }
    public void setStatusEm(Instant statusEm) { this.statusEm = statusEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
    public long getVersao() { return versao; }
    public void setVersao(long versao) { this.versao = versao; }
}
