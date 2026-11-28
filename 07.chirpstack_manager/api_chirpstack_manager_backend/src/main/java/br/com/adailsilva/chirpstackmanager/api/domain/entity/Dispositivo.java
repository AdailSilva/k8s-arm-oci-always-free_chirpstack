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
 * Dispositivo LoRaWAN, um por DevEUI. Nasce na primeira mensagem MQTT; nome, aplicação, perfil e
 * tags vêm do {@code deviceInfo} do evento. {@code descricao} e {@code ativo} são editados no painel
 * (desativado some do painel e a coleta continua). Guarda o último estado conhecido.
 */
@Entity
@Table(name = "tb_dispositivo")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_eui", nullable = false, length = 16, unique = true)
    private String devEui;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 300)
    private String descricao;

    @Column(name = "aplicacao_id", length = 36)
    private String aplicacaoId;

    @Column(name = "aplicacao_nome", length = 120)
    private String aplicacaoNome;

    @Column(name = "perfil_nome", length = 120)
    private String perfilNome;

    @Column(name = "tenant_nome", length = 120)
    private String tenantNome;

    @Column(name = "tags", length = 2000)
    private String tags;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_contato_em")
    private Instant ultimoContatoEm;

    @Column(name = "ultimo_evento", length = 20)
    private String ultimoEvento;

    @Column(name = "ultimo_rssi")
    private Integer ultimoRssi;

    @Column(name = "ultimo_snr", precision = 6, scale = 2)
    private BigDecimal ultimoSnr;

    @Column(name = "ultimo_f_cnt")
    private Long ultimoFCnt;

    @Column(name = "ultimo_gateway", length = 16)
    private String ultimoGateway;

    @Column(name = "ultima_bateria")
    private Integer ultimaBateria;

    @Column(name = "alimentacao_externa")
    private Boolean alimentacaoExterna;

    @Column(name = "ultimo_objeto", columnDefinition = "TEXT")
    private String ultimoObjeto;

    @Column(name = "total_uplinks", nullable = false)
    private long totalUplinks;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDevEui() { return devEui; }
    public void setDevEui(String devEui) { this.devEui = devEui; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public String getAplicacaoId() { return aplicacaoId; }
    public void setAplicacaoId(String aplicacaoId) { this.aplicacaoId = aplicacaoId; }
    public String getAplicacaoNome() { return aplicacaoNome; }
    public void setAplicacaoNome(String aplicacaoNome) { this.aplicacaoNome = aplicacaoNome; }
    public String getPerfilNome() { return perfilNome; }
    public void setPerfilNome(String perfilNome) { this.perfilNome = perfilNome; }
    public String getTenantNome() { return tenantNome; }
    public void setTenantNome(String tenantNome) { this.tenantNome = tenantNome; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getUltimoContatoEm() { return ultimoContatoEm; }
    public void setUltimoContatoEm(Instant ultimoContatoEm) { this.ultimoContatoEm = ultimoContatoEm; }
    public String getUltimoEvento() { return ultimoEvento; }
    public void setUltimoEvento(String ultimoEvento) { this.ultimoEvento = ultimoEvento; }
    public Integer getUltimoRssi() { return ultimoRssi; }
    public void setUltimoRssi(Integer ultimoRssi) { this.ultimoRssi = ultimoRssi; }
    public BigDecimal getUltimoSnr() { return ultimoSnr; }
    public void setUltimoSnr(BigDecimal ultimoSnr) { this.ultimoSnr = ultimoSnr; }
    public Long getUltimoFCnt() { return ultimoFCnt; }
    public void setUltimoFCnt(Long ultimoFCnt) { this.ultimoFCnt = ultimoFCnt; }
    public String getUltimoGateway() { return ultimoGateway; }
    public void setUltimoGateway(String ultimoGateway) { this.ultimoGateway = ultimoGateway; }
    public Integer getUltimaBateria() { return ultimaBateria; }
    public void setUltimaBateria(Integer ultimaBateria) { this.ultimaBateria = ultimaBateria; }
    public Boolean getAlimentacaoExterna() { return alimentacaoExterna; }
    public void setAlimentacaoExterna(Boolean alimentacaoExterna) { this.alimentacaoExterna = alimentacaoExterna; }
    public String getUltimoObjeto() { return ultimoObjeto; }
    public void setUltimoObjeto(String ultimoObjeto) { this.ultimoObjeto = ultimoObjeto; }
    public long getTotalUplinks() { return totalUplinks; }
    public void setTotalUplinks(long totalUplinks) { this.totalUplinks = totalUplinks; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
