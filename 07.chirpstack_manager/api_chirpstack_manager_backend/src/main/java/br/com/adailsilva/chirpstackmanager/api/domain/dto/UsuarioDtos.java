package br.com.adailsilva.chirpstackmanager.api.domain.dto;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;

/**
 * DTOs do módulo de usuários do painel.
 */
public final class UsuarioDtos {

    /** Senha: 8 a 72 caracteres, com pelo menos uma letra e um número. */
    public static final String SENHA_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String SENHA_MSG = "A senha deve ter de 8 a 72 caracteres, com letras e números.";

    /** Foto de perfil: miniatura JPEG/PNG em data URI (o navegador reduz a 512 px). ~300 KB no máximo. */
    public static final String FOTO_REGEX = "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$";
    public static final int FOTO_MAX = 400_000;

    private UsuarioDtos() {
    }

    /**
     * Dados de cadastro além de nome, e-mail e perfil (mesmo conjunto dos demais sistemas). Todos
     * opcionais; telefone e CEP chegam só com dígitos ou formatados e são gravados só com dígitos.
     */
    public record DadosCadastro(
            @Size(max = 20, message = "Telefone: até 20 caracteres.") String telefone,
            @Size(max = 80, message = "Cargo: até 80 caracteres.") String cargo,
            @Size(max = FOTO_MAX, message = "Foto muito grande. Escolha outra imagem.")
            @Pattern(regexp = FOTO_REGEX, message = "Foto inválida. Use uma imagem JPG, PNG ou WEBP.") String fotoPerfil,
            @Size(max = 9, message = "CEP inválido.") String cep,
            @Size(max = 20) String enderecoTipo,
            @Size(max = 255, message = "Logradouro: até 255 caracteres.") String endereco,
            @Size(max = 20, message = "Número: até 20 caracteres.") String numero,
            @Size(max = 255, message = "Complemento: até 255 caracteres.") String complemento,
            @Size(max = 150, message = "Bairro: até 150 caracteres.") String bairro,
            @Size(max = 100, message = "Cidade: até 100 caracteres.") String cidade,
            @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "UF inválida.") String uf,
            @Size(max = 4000, message = "Observações: até 4000 caracteres.") String observacoes) {

        public static DadosCadastro de(Usuario u) {
            return new DadosCadastro(u.getTelefone(), u.getCargo(), u.getFotoPerfil(), u.getCep(), u.getEnderecoTipo(),
                    u.getEndereco(), u.getNumero(), u.getComplemento(), u.getBairro(), u.getCidade(), u.getUf(),
                    u.getObservacoes());
        }
    }

    /**
     * {@code emailVerificado}: FALSE até o usuário confirmar o e-mail pelo link (o login é recusado).
     * {@code convitePendente}: criado por convite e ainda sem senha definida.
     */
    public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo,
                                  boolean bloqueado, Instant bloqueadoAte, String motivoBloqueio,
                                  Instant ultimoLogin, Instant criadoEm, Instant atualizadoEm,
                                  boolean emailVerificado, boolean convitePendente, DadosCadastro cadastro) {
        public static UsuarioResponse of(Usuario u) {
            boolean bloqueado = u.isBloqueado();
            return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(), u.isAtivo(),
                    bloqueado, bloqueado ? u.getBloqueadoAte() : null, bloqueado ? u.getMotivoBloqueio() : null,
                    u.getUltimoLogin(), u.getCriadoEm(), u.getAtualizadoEm(),
                    u.isEmailVerificado(), u.isConvitePendente(), DadosCadastro.de(u));
        }
    }

    /**
     * Novo usuário. Com {@code enviarConvite} (padrão quando a senha vem vazia), o usuário recebe um
     * link para definir a própria senha — que também confirma o e-mail. Com senha informada pelo
     * administrador, recebe um link de confirmação do e-mail e só entra depois de confirmar.
     */
    public record UsuarioCreateRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @Pattern(regexp = "^$|" + SENHA_REGEX, message = SENHA_MSG) String senha,
            @NotNull(message = "Informe o perfil.") Perfil perfil,
            Boolean ativo,
            Boolean enviarConvite,
            @Valid DadosCadastro cadastro) {

        /** Compatibilidade: criação só com os dados de acesso (senha informada). */
        public UsuarioCreateRequest(String nome, String email, String senha, Perfil perfil, Boolean ativo) {
            this(nome, email, senha, perfil, ativo, Boolean.FALSE, null);
        }
    }

    public record UsuarioUpdateRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @NotNull(message = "Informe o perfil.") Perfil perfil,
            @NotNull Boolean ativo,
            /** Opcional: quando informada, redefine a senha. */
            @Pattern(regexp = "^$|" + SENHA_REGEX, message = SENHA_MSG) String novaSenha,
            @Valid DadosCadastro cadastro) {

        /** Compatibilidade: edição só dos dados de acesso. */
        public UsuarioUpdateRequest(String nome, String email, Perfil perfil, Boolean ativo, String novaSenha) {
            this(nome, email, perfil, ativo, novaSenha, null);
        }
    }

    /** Minha Conta: o próprio usuário altera nome e cadastro (e-mail e perfil só o administrador). */
    public record PerfilUpdateRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String nome,
            @Valid DadosCadastro cadastro) {

        public PerfilUpdateRequest(String nome) {
            this(nome, null);
        }
    }

    public record TrocaSenhaRequest(
            @NotBlank(message = "Informe a senha atual.") String senhaAtual,
            @NotBlank(message = "Informe a nova senha.") @Pattern(regexp = SENHA_REGEX, message = SENHA_MSG) String novaSenha) {
    }

    /** Resposta do reenvio do convite / da confirmação. */
    public record EnvioLinkResponse(boolean enviado, String para) {
    }
}
