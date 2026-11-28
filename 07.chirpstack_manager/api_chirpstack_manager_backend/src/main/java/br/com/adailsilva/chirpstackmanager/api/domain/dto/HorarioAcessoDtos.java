package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Horário de acesso (Sistema → Horário de acesso) e a situação do usuário logado.
 */
public final class HorarioAcessoDtos {

    /** "HH:mm" de 00:00 a 23:59 (vazio = sem hora). */
    private static final String HORA = "^$|^([01]\\d|2[0-3]):[0-5]\\d$";

    private HorarioAcessoDtos() {
    }

    /**
     * Um dia da grade. {@code permitido=false} = sem acesso no dia; com início e fim = janela do dia;
     * sem horas = dia livre.
     */
    public record Dia(
            @NotNull(message = "Informe o dia da semana.") @Min(value = 1, message = "Dia da semana inválido.")
            @Max(value = 7, message = "Dia da semana inválido.") Integer diaSemana,
            String diaNome,
            Boolean permitido,
            @Pattern(regexp = HORA, message = "Hora de início inválida (use HH:mm).") String horaInicio,
            @Pattern(regexp = HORA, message = "Hora de fim inválida (use HH:mm).") String horaFim) {
    }

    public record SalvarRequest(
            @NotNull(message = "Informe os dias.") @Size(max = 7, message = "No máximo sete dias.") List<@Valid Dia> dias,
            /** Encerrar já as sessões de quem ficou fora da nova grade (exceto ADMIN). */
            Boolean aplicarAgora) {
    }

    public record SalvarResponse(List<Dia> dias, int sessoesEncerradas) {
    }

    /**
     * Situação do usuário logado: alimenta o aviso dos 5 minutos e a desconexão no fim do horário.
     *
     * @param restrito       sujeito ao horário agora (não é ADMIN e o dia tem janela)
     * @param permitidoAgora dentro do horário
     * @param fimDoHorario   quando o acesso de hoje termina (nulo se não há fim hoje)
     * @param horaFim        o mesmo fim em "HH:mm", hora local do servidor
     * @param proximoInicio  quando o acesso volta (fora do horário)
     * @param mensagem       texto pronto para o usuário
     * @param agora          hora do servidor, para o navegador corrigir a diferença de relógio
     */
    public record Situacao(boolean restrito, boolean permitidoAgora, Instant fimDoHorario, String horaFim,
                           Instant proximoInicio, String mensagem, Instant agora) {
    }
}
