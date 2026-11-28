package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Conta de e-mail e redefinição de senha. */
public final class EmailDtos {

    private EmailDtos() {
    }

    /**
     * Situação da conta de envio. {@code origem}: INSTALACAO (senha do ambiente), SISTEMA (cadastrada
     * na tela) ou SISTEMA_ILEGIVEL (cadastrada, mas a chave de cifragem mudou).
     */
    public record ContaEmailResponse(String servidor, int porta, String conta, String origem, Instant alteradoEm,
                                     String alteradoPor, boolean chaveDedicada) {
    }

    public record AlterarSenhaEmailRequest(
            @NotBlank(message = "Informe a nova senha de app.") @Size(max = 200) String senha,
            @NotBlank(message = "Confirme a nova senha de app.") @Size(max = 200) String confirmacao,
            Boolean testarAntes) {
    }

    public record TestarSenhaEmailRequest(@Size(max = 200) String senha) {
    }

    public record EsqueciSenhaRequest(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email) {
    }

    public record RedefinirSenhaRequest(
            @NotBlank(message = "Link inválido.") @Size(max = 200) String token,
            @NotBlank(message = "Informe a nova senha.")
            @Pattern(regexp = UsuarioDtos.SENHA_REGEX, message = UsuarioDtos.SENHA_MSG) String novaSenha) {
    }

    /** Confirmação do e-mail pelo link ({@code /auth/confirmar-email?token=...}). */
    public record ConfirmarEmailRequest(@NotBlank(message = "Link inválido.") @Size(max = 200) String token) {
    }

    /** "Reenviar confirmação" na tela de login: responde igual exista ou não o e-mail. */
    public record ReenviarConfirmacaoRequest(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email) {
    }
}
