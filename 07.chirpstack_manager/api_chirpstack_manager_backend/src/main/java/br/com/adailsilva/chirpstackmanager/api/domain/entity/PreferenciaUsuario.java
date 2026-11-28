package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Preferências de aparência de um usuário (tema Avalon), guardadas no servidor para valerem em
 * qualquer navegador ou dispositivo ({@code tb_preferencia_usuario}, 1:1 com o usuário).
 */
@Entity
@Table(name = "tb_preferencia_usuario")
public class PreferenciaUsuario {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String preset = "Aura";

    @Column(name = "cor_primaria", nullable = false, length = 30)
    private String corPrimaria = "blue";

    @Column(length = 30)
    private String superficie;

    @Column(name = "tema_escuro", nullable = false)
    private boolean temaEscuro;

    @Column(name = "modo_menu", nullable = false, length = 20)
    private String modoMenu = "static";

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected PreferenciaUsuario() {
    }

    public PreferenciaUsuario(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    @PrePersist
    @PreUpdate
    void carimbar() {
        atualizadoEm = Instant.now();
    }

    public Long getUsuarioId() { return usuarioId; }
    public String getPreset() { return preset; }
    public void setPreset(String v) { this.preset = v; }
    public String getCorPrimaria() { return corPrimaria; }
    public void setCorPrimaria(String v) { this.corPrimaria = v; }
    public String getSuperficie() { return superficie; }
    public void setSuperficie(String v) { this.superficie = v; }
    public boolean isTemaEscuro() { return temaEscuro; }
    public void setTemaEscuro(boolean v) { this.temaEscuro = v; }
    public String getModoMenu() { return modoMenu; }
    public void setModoMenu(String v) { this.modoMenu = v; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
