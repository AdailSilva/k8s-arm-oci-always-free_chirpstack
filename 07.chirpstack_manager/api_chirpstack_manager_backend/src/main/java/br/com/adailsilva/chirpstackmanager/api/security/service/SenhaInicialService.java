package br.com.adailsilva.chirpstackmanager.api.security.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.adailsilva.chirpstackmanager.api.config.SecurityProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;

/**
 * Troca obrigatória da senha inicial.
 * <p>
 * O administrador nasce com a senha inicial ({@code app.security.bootstrap-admin-password}, padrão
 * {@code chirpstack}). Enquanto a senha guardada for essa, o usuário só pode consultar os próprios
 * dados e trocar a senha. A marca é a própria senha: ao trocar, a marca some, sem coluna extra na
 * tabela. A senha inicial não passa na regra de senha forte, então não volta a ser escolhida.
 */
@Service
public class SenhaInicialService {

    /** Claim do access token que indica a troca pendente. */
    public static final String CLAIM = "trocaSenha";
    /** Código de erro devolvido enquanto a troca estiver pendente. */
    public static final String ERRO = "TROCA_SENHA_OBRIGATORIA";
    public static final String MENSAGEM = "Troque a senha inicial para continuar.";

    private final PasswordEncoder encoder;
    private final SecurityProperties props;

    public SenhaInicialService(PasswordEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String senhaInicial() {
        return props.bootstrapAdminPassword();
    }

    /** O usuário ainda usa a senha inicial e precisa trocá-la antes de usar o sistema. */
    public boolean trocaObrigatoria(Usuario u) {
        return u.getSenhaHash() != null && encoder.matches(senhaInicial(), u.getSenhaHash());
    }

    /** A senha informada é a senha inicial (não pode ser a nova senha). */
    public boolean ehSenhaInicial(String senha) {
        return senhaInicial().equals(senha);
    }
}
