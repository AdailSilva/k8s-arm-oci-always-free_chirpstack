package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uplink de um dispositivo (evento {@code up} do ChirpStack). Idempotente pelo
 * {@code deduplicationId}. {@code objeto} é o JSON decodificado pelo codec do perfil.
 */
@Entity
@Table(name = "tb_leitura")
public class Leitura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispositivo_id", nullable = false)
    private Long dispositivoId;

    @Column(name = "deduplication_id", nullable = false, length = 36, unique = true)
    private String deduplicationId;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Column(name = "f_cnt")
    private Long fCnt;

    @Column(name = "f_port")
    private Integer fPort;

    @Column(name = "dr")
    private Integer dr;

    @Column(name = "spreading_factor")
    private Integer spreadingFactor;

    @Column(name = "frequencia")
    private Long frequencia;

    @Column(name = "rssi")
    private Integer rssi;

    @Column(name = "snr", precision = 6, scale = 2)
    private BigDecimal snr;

    @Column(name = "gateway_id", length = 16)
    private String gatewayId;

    @Column(name = "qtd_gateways")
    private Integer qtdGateways;

    @Column(name = "confirmado")
    private Boolean confirmado;

    @Column(name = "dados_base64", length = 512)
    private String dadosBase64;

    @Column(name = "objeto", columnDefinition = "TEXT")
    private String objeto;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(Long dispositivoId) { this.dispositivoId = dispositivoId; }
    public String getDeduplicationId() { return deduplicationId; }
    public void setDeduplicationId(String deduplicationId) { this.deduplicationId = deduplicationId; }
    public Instant getRecebidoEm() { return recebidoEm; }
    public void setRecebidoEm(Instant recebidoEm) { this.recebidoEm = recebidoEm; }
    public Long getFCnt() { return fCnt; }
    public void setFCnt(Long fCnt) { this.fCnt = fCnt; }
    public Integer getFPort() { return fPort; }
    public void setFPort(Integer fPort) { this.fPort = fPort; }
    public Integer getDr() { return dr; }
    public void setDr(Integer dr) { this.dr = dr; }
    public Integer getSpreadingFactor() { return spreadingFactor; }
    public void setSpreadingFactor(Integer spreadingFactor) { this.spreadingFactor = spreadingFactor; }
    public Long getFrequencia() { return frequencia; }
    public void setFrequencia(Long frequencia) { this.frequencia = frequencia; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
    public BigDecimal getSnr() { return snr; }
    public void setSnr(BigDecimal snr) { this.snr = snr; }
    public String getGatewayId() { return gatewayId; }
    public void setGatewayId(String gatewayId) { this.gatewayId = gatewayId; }
    public Integer getQtdGateways() { return qtdGateways; }
    public void setQtdGateways(Integer qtdGateways) { this.qtdGateways = qtdGateways; }
    public Boolean getConfirmado() { return confirmado; }
    public void setConfirmado(Boolean confirmado) { this.confirmado = confirmado; }
    public String getDadosBase64() { return dadosBase64; }
    public void setDadosBase64(String dadosBase64) { this.dadosBase64 = dadosBase64; }
    public String getObjeto() { return objeto; }
    public void setObjeto(String objeto) { this.objeto = objeto; }
}
