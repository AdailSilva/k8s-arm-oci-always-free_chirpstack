package br.com.adailsilva.chirpstackmanager.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.TokenRedefinicao;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoToken;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.RefreshTokenRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.TokenRedefinicaoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;
import br.com.adailsilva.chirpstackmanager.api.security.service.SenhaInicialService;
import br.com.adailsilva.chirpstackmanager.api.service.email.EmailService;

/**
 * Links de uso único enviados por e-mail (só o SHA-256 do token fica no banco):
 * <ul>
 *   <li><b>Esqueci a senha</b>: o pedido sempre responde igual (não revela se o e-mail existe); para
 *       um usuário ativo, envia um link que vale {@value #VALIDADE_MINUTOS} minutos;</li>
 *   <li><b>Convite</b>: o usuário criado pelo administrador define a própria senha (vale
 *       {@value #VALIDADE_CONVITE_HORAS} h). Definir a senha pelo link também confirma o e-mail;</li>
 *   <li><b>Confirmação de e-mail</b>: usuário criado com senha ou com o e-mail alterado; até
 *       confirmar, o login é recusado com {@code EMAIL_NAO_CONFIRMADO} (vale
 *       {@value #VALIDADE_CONFIRMACAO_HORAS} h).</li>
 * </ul>
 * Os e-mails só saem depois do commit, para o link nunca apontar para um token que não foi gravado.
 * Redefinir ou definir a senha encerra todas as sessões do usuário e desbloqueia a conta.
 */
@Service
public class RecuperacaoSenhaService {

    static final long VALIDADE_MINUTOS = 30;
    static final long VALIDADE_CONVITE_HORAS = 72;
    static final long VALIDADE_CONFIRMACAO_HORAS = 48;
    private static final Duration INTERVALO_MINIMO = Duration.ofMinutes(2);
    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);

    private final UsuarioRepository usuarios;
    private final TokenRedefinicaoRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final SessaoService sessoes;
    private final PasswordEncoder encoder;
    private final SenhaInicialService senhaInicial;
    private final EmailService email;
    private final String urlPublica;
    private final SecureRandom aleatorio = new SecureRandom();

    public RecuperacaoSenhaService(UsuarioRepository usuarios, TokenRedefinicaoRepository tokens,
                                   RefreshTokenRepository refreshTokens, SessaoService sessoes, PasswordEncoder encoder,
                                   SenhaInicialService senhaInicial, EmailService email,
                                   @Value("${app.url-publica:http://localhost:4201}") String urlPublica) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.sessoes = sessoes;
        this.encoder = encoder;
        this.senhaInicial = senhaInicial;
        this.email = email;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    // ── Esqueci a senha ──────────────────────────────────────────────────────

    @Transactional
    public void solicitar(String emailInformado, String ip) {
        Usuario u = usuarios.findByEmailIgnoreCase(emailInformado.trim()).orElse(null);
        if (u == null || !u.isAtivo()) {
            log.info("Redefinição pedida para e-mail sem usuário ativo (ip {}).", ip);
            return;
        }
        if (u.isConvitePendente()) {
            // Convidado que ainda não definiu a senha: manda de novo o convite (o mesmo efeito).
            if (!recente(u, TipoToken.CONVITE)) {
                enviarConvite(u, null);
            }
            return;
        }
        if (recente(u, TipoToken.RECUPERACAO_SENHA)) {
            return; // evita disparar vários e-mails seguidos
        }
        String token = emitir(u, TipoToken.RECUPERACAO_SENHA, Duration.ofMinutes(VALIDADE_MINUTOS), ip);
        String link = urlPublica + "/auth/nova-senha?token=" + token;
        String para = u.getEmail();
        String nome = u.getNome();
        depoisDoCommit(() -> email.enviar(new String[] {para}, "Redefinição de senha",
                email.corpoRedefinicao(nome, link, VALIDADE_MINUTOS)));
        log.info("Link de redefinição de senha enviado para {}.", para);
    }

    /** Nova senha pelo link: vale para "Esqueci a senha" e para o convite. */
    @Transactional
    public void redefinir(String token, String novaSenha, String ip) {
        TokenRedefinicao t = tokenValido(token)
                .filter(x -> x.getTipo() == TipoToken.RECUPERACAO_SENHA || x.getTipo() == TipoToken.CONVITE)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Link inválido ou vencido. Peça um novo em \"Esqueci a senha\"."));
        boolean convite = t.getTipo() == TipoToken.CONVITE;
        Usuario u = usuarios.findById(t.getUsuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Usuário inativo."));
        if (senhaInicial.ehSenhaInicial(novaSenha)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Escolha uma senha diferente da senha inicial.");
        }
        u.setSenhaHash(encoder.encode(novaSenha));
        u.setTentativasFalhas(0);
        u.setBloqueadoAte(null);
        u.setConvitePendente(false);
        // O link chegou pelo e-mail: o endereço está confirmado.
        marcarConfirmado(u);
        usuarios.save(u);
        Instant agora = Instant.now();
        t.setUsadoEm(agora);
        tokens.save(t);
        tokens.invalidarDoTipo(u.getId(), t.getTipo(), agora);
        tokens.invalidarDoTipo(u.getId(), TipoToken.VERIFICACAO_EMAIL, agora);
        refreshTokens.revogarTodos(u.getId());
        sessoes.encerrarDoUsuario(u.getId(), MotivoEncerramento.SENHA_ALTERADA, u.getEmail(), null);
        log.info("Senha de {} {} pelo link de e-mail (ip {}).", u.getEmail(), convite ? "definida (convite)" : "redefinida", ip);
    }

    // ── Convite ──────────────────────────────────────────────────────────────

    /**
     * Envia (ou reenvia) o convite: invalida os anteriores e manda um link novo. Chamar com o usuário
     * já gravado; o e-mail sai depois do commit da transação atual.
     */
    @Transactional
    public void enviarConvite(Usuario u, String convidadoPor) {
        String token = emitir(u, TipoToken.CONVITE, Duration.ofHours(VALIDADE_CONVITE_HORAS), null);
        String link = urlPublica + "/auth/nova-senha?convite=1&token=" + token;
        String para = u.getEmail();
        String nome = u.getNome();
        depoisDoCommit(() -> email.enviar(new String[] {para}, "Convite de acesso",
                email.corpoConvite(nome, convidadoPor, link, VALIDADE_CONVITE_HORAS)));
        log.info("Convite enviado para {}.", para);
    }

    // ── Confirmação de e-mail ────────────────────────────────────────────────

    /** Envia o link de confirmação para o e-mail atual do usuário (que fica sem confirmação). */
    @Transactional
    public void enviarConfirmacao(Usuario u) {
        u.setEmailVerificado(false);
        u.setEmailVerificadoEm(null);
        String token = emitir(u, TipoToken.VERIFICACAO_EMAIL, Duration.ofHours(VALIDADE_CONFIRMACAO_HORAS), null);
        String link = urlPublica + "/auth/confirmar-email?token=" + token;
        String para = u.getEmail();
        String nome = u.getNome();
        depoisDoCommit(() -> email.enviar(new String[] {para}, "Confirmação de e-mail",
                email.corpoConfirmacao(nome, link, VALIDADE_CONFIRMACAO_HORAS)));
        log.info("Link de confirmação de e-mail enviado para {}.", para);
    }

    @Transactional
    public String confirmarEmail(String token) {
        TokenRedefinicao t = tokenValido(token)
                .filter(x -> x.getTipo() == TipoToken.VERIFICACAO_EMAIL)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Link de confirmação inválido ou vencido. Peça um novo na tela de login."));
        Usuario u = usuarios.findById(t.getUsuarioId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Usuário não encontrado."));
        Instant agora = Instant.now();
        t.setUsadoEm(agora);
        tokens.save(t);
        tokens.invalidarDoTipo(u.getId(), TipoToken.VERIFICACAO_EMAIL, agora);
        if (u.isEmailVerificado()) {
            return "Seu e-mail já estava confirmado. Você pode entrar normalmente.";
        }
        marcarConfirmado(u);
        usuarios.save(u);
        log.info("E-mail de {} confirmado.", u.getEmail());
        return u.isConvitePendente()
                ? "E-mail confirmado. Defina a sua senha pelo link do convite."
                : "E-mail confirmado. Você já pode entrar no sistema.";
    }

    /** "Reenviar confirmação" no login: responde igual exista ou não o e-mail. */
    @Transactional
    public void reenviarConfirmacao(String emailInformado) {
        Usuario u = usuarios.findByEmailIgnoreCase(emailInformado.trim()).orElse(null);
        if (u == null || !u.isAtivo()) {
            return;
        }
        if (u.isConvitePendente()) {
            if (!recente(u, TipoToken.CONVITE)) {
                enviarConvite(u, null);
            }
            return;
        }
        if (!u.isEmailVerificado() && !recente(u, TipoToken.VERIFICACAO_EMAIL)) {
            enviarConfirmacao(u);
        }
    }

    // ── manutenção ───────────────────────────────────────────────────────────

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void limpar() {
        tokens.apagarExpirados(Instant.now().minus(Duration.ofDays(7)));
    }

    // ── apoio ────────────────────────────────────────────────────────────────

    private boolean recente(Usuario u, TipoToken tipo) {
        return tokens.existsByUsuarioIdAndTipoAndCriadoEmAfter(u.getId(), tipo, Instant.now().minus(INTERVALO_MINIMO));
    }

    /** Invalida os links anteriores com a mesma finalidade e grava um novo; devolve o token em claro. */
    private String emitir(Usuario u, TipoToken tipo, Duration validade, String ip) {
        Instant agora = Instant.now();
        tokens.invalidarDoTipo(u.getId(), tipo, agora);
        byte[] bruto = new byte[32];
        aleatorio.nextBytes(bruto);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        TokenRedefinicao t = new TokenRedefinicao();
        t.setUsuarioId(u.getId());
        t.setTipo(tipo);
        t.setTokenHash(hash(token));
        t.setCriadoEm(agora);
        t.setExpiraEm(agora.plus(validade));
        t.setIp(ip);
        tokens.save(t);
        return token;
    }

    private java.util.Optional<TokenRedefinicao> tokenValido(String token) {
        return tokens.findByTokenHash(hash(token == null ? "" : token.trim()))
                .filter(x -> x.getUsadoEm() == null && x.getExpiraEm().isAfter(Instant.now()));
    }

    private static void marcarConfirmado(Usuario u) {
        if (!u.isEmailVerificado()) {
            u.setEmailVerificado(true);
            u.setEmailVerificadoEm(Instant.now());
        }
    }

    private static void depoisDoCommit(Runnable envio) {
        Runnable assincrono = () -> Thread.ofVirtual().start(envio);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    assincrono.run();
                }
            });
        } else {
            assincrono.run();
        }
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
