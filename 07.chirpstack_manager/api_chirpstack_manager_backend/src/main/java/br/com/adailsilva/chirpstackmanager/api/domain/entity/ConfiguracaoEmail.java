package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Conta de e-mail (linha única, id = 1): senha de app do SMTP cifrada. A carga geral grava a semente
 * {@code v0:}, trocada pela cifra {@code v1:} na primeira subida.
 */
@Entity
@Table(name = "tb_configuracao_email")
public class ConfiguracaoEmail {

    @Id
    private Short id;

    @Column(name = "senha_app_cifrada", nullable = false, length = 512)
    private String senhaAppCifrada;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    public static final short ID_UNICO = 1;

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public String getSenhaAppCifrada() { return senhaAppCifrada; }
    public void setSenhaAppCifrada(String senhaAppCifrada) { this.senhaAppCifrada = senhaAppCifrada; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
    public void setAtualizadoPor(String atualizadoPor) { this.atualizadoPor = atualizadoPor; }
}
