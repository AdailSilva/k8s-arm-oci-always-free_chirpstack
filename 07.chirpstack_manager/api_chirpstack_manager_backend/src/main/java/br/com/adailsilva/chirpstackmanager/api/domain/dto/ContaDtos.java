package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Conta do usuário logado: preferências de aparência, tela bloqueada e suporte.
 */
public final class ContaDtos {

    private ContaDtos() {
    }

    /** Aparência (tema Avalon): o mesmo formato do LayoutConfig do frontend. */
    public record Preferencias(
            @NotBlank @Pattern(regexp = "Aura|Lara|Nora", message = "Tema inválido.") String preset,
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[a-z][a-z0-9-]{0,29}$", message = "Cor inválida.") String primary,
            @Size(max = 30) @Pattern(regexp = "^$|^[a-z][a-z0-9-]{0,29}$", message = "Superfície inválida.") String surface,
            @NotNull Boolean darkTheme,
            @NotBlank @Pattern(regexp = "static|overlay|slim|slim-plus|compact|reveal|drawer|horizontal",
                    message = "Modo de menu inválido.") String menuMode,
            Instant atualizadoEm) {
    }

    public record DesbloquearRequest(@NotBlank(message = "Informe a senha.") @Size(max = 72) String senha) {
    }

    public record SuporteRequest(
            @NotBlank(message = "Informe o assunto.") @Size(max = 150, message = "Assunto: até 150 caracteres.") String assunto,
            @NotBlank(message = "Escreva a mensagem.") @Size(max = 5000, message = "Mensagem: até 5000 caracteres.") String mensagem,
            @Pattern(regexp = "^$|^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$", message = "Telefone inválido. Ex.: (88) 3333-4444") String telefone,
            @Pattern(regexp = "^$|^\\(?\\d{2}\\)?\\s?9\\d{4}-?\\d{4}$", message = "WhatsApp inválido. Ex.: (88) 99999-8888") String whatsapp) {
    }

    public record SuporteResponse(String destinatario, Instant enviadoEm) {
    }
}
