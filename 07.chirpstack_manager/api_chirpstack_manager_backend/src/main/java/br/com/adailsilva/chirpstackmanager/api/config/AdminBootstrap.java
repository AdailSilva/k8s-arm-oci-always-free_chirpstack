package br.com.adailsilva.chirpstackmanager.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;

/**
 * Cria o primeiro administrador quando a base está vazia (idempotente: com qualquer usuário
 * cadastrado não faz nada, então um administrador existente nunca tem a senha alterada).
 * <p>
 * Credenciais iniciais: {@code APP_ADMIN_EMAIL}/{@code APP_ADMIN_PASSWORD}, com padrão
 * {@code admin@chirpstack.com.br}/{@code chirpstack}. No primeiro acesso a API exige a troca da
 * senha antes de liberar o resto do sistema (ver {@code SenhaInicialService}).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final SecurityProperties props;

    public AdminBootstrap(UsuarioRepository repository, PasswordEncoder encoder, SecurityProperties props) {
        this.repository = repository;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Usuario u = new Usuario();
        u.setNome("Administrador");
        u.setEmail(props.bootstrapAdminEmail().trim().toLowerCase());
        u.setSenhaHash(encoder.encode(props.bootstrapAdminPassword()));
        u.setPerfil(Perfil.ADMIN);
        u.setAtivo(true);
        repository.save(u);
        log.info("Primeiro administrador criado: {} (troca de senha obrigatória no primeiro acesso).", u.getEmail());
    }
}
