package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoPeriferico;

/** Atuador ou sensor ligado a um equipamento (relé, PZEM-004T...). */
@Entity
@Table(name = "tb_periferico")
public class Periferico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipamento_id", nullable = false)
    private Long equipamentoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoPeriferico tipo;

    @Column(name = "canal", nullable = false)
    private int canal;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "gpio")
    private Integer gpio;

    @Column(name = "endereco_modbus")
    private Integer enderecoModbus;

    @Column(name = "critico", nullable = false)
    private boolean critico;

    @Column(name = "estado_desejado")
    private Boolean estadoDesejado;

    @Column(name = "estado_reportado")
    private Boolean estadoReportado;

    @Column(name = "reportado_em")
    private Instant reportadoEm;

    @Column(name = "leitura_json", columnDefinition = "TEXT")
    private String leituraJson;

    @Column(name = "leitura_em")
    private Instant leituraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEquipamentoId() { return equipamentoId; }
    public void setEquipamentoId(Long equipamentoId) { this.equipamentoId = equipamentoId; }
    public TipoPeriferico getTipo() { return tipo; }
    public void setTipo(TipoPeriferico tipo) { this.tipo = tipo; }
    public int getCanal() { return canal; }
    public void setCanal(int canal) { this.canal = canal; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getGpio() { return gpio; }
    public void setGpio(Integer gpio) { this.gpio = gpio; }
    public Integer getEnderecoModbus() { return enderecoModbus; }
    public void setEnderecoModbus(Integer enderecoModbus) { this.enderecoModbus = enderecoModbus; }
    public boolean isCritico() { return critico; }
    public void setCritico(boolean critico) { this.critico = critico; }
    public Boolean getEstadoDesejado() { return estadoDesejado; }
    public void setEstadoDesejado(Boolean estadoDesejado) { this.estadoDesejado = estadoDesejado; }
    public Boolean getEstadoReportado() { return estadoReportado; }
    public void setEstadoReportado(Boolean estadoReportado) { this.estadoReportado = estadoReportado; }
    public Instant getReportadoEm() { return reportadoEm; }
    public void setReportadoEm(Instant reportadoEm) { this.reportadoEm = reportadoEm; }
    public String getLeituraJson() { return leituraJson; }
    public void setLeituraJson(String leituraJson) { this.leituraJson = leituraJson; }
    public Instant getLeituraEm() { return leituraEm; }
    public void setLeituraEm(Instant leituraEm) { this.leituraEm = leituraEm; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
