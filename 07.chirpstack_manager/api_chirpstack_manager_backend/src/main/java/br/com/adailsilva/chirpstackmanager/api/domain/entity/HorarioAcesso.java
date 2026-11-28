package br.com.adailsilva.chirpstackmanager.api.domain.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Horário de acesso de um dia da semana ({@code tb_horario_acesso}). ISO: 1 = segunda ... 7 = domingo.
 * <p>
 * Só os dias com restrição têm linha: com horário ({@code permitido} e início/fim) ou sem acesso
 * ({@code permitido = false}). Dia sem linha é livre; tabela vazia = nenhuma restrição.
 */
@Entity
@Table(name = "tb_horario_acesso")
public class HorarioAcesso {

    @Id
    @Column(name = "dia_semana")
    private Short diaSemana;

    @Column(name = "permitido", nullable = false)
    private boolean permitido = true;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", length = 160)
    private String atualizadoPor;

    protected HorarioAcesso() {
    }

    public HorarioAcesso(int diaSemana, boolean permitido, LocalTime horaInicio, LocalTime horaFim, String atualizadoPor) {
        this.diaSemana = (short) diaSemana;
        this.permitido = permitido;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.atualizadoPor = atualizadoPor;
    }

    @PrePersist
    @PreUpdate
    void carimbar() {
        atualizadoEm = Instant.now();
    }

    public int getDiaSemana() { return diaSemana; }
    public boolean isPermitido() { return permitido; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
}
