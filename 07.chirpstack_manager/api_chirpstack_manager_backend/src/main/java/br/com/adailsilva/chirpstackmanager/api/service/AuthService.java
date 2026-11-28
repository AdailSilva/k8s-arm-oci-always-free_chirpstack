package br.com.adailsilva.chirpstackmanager.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.Emissao;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.TokenResponse;
import br.com.adailsilva.chirpstackmanager.api.config.SecurityProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.PoliticaSessao;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Sessao;
import br.com.adailsilva.chirpstackmanager.api.exception.SessaoEncerradaException;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.SessaoDtos.SessaoInfo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Usuario;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.UsuarioResponse;
import br.com.adailsilva.chirpstackmanager.api.repository.UsuarioRepository;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RefreshToken;
import br.com.adailsilva.chirpstackmanager.api.repository.RefreshTokenRepository;
import br.com.adailsilva.chirpstackmanager.api.security.service.LoginRateLimiter;
import br.com.adailsilva.chirpstackmanager.api.security.service.SenhaInicialService;
import br.com.adailsilva.chirpstackmanager.api.security.service.TokenService;

/**
 * Login, renovação e encerramento de sessão.
 * <ul>
 *   <li>Mensagem única para e-mail ou senha errados (não revela se o e-mail existe);</li>
 *   <li>bloqueio temporário da conta após falhas seguidas;</li>
 *   <li>limite de tentativas por IP;</li>
 *   <li>refresh token aleatório de 256 bits, guardado só como hash e trocado a cada uso;</li>
 *   <li>cada login abre uma {@link Sessao} (visitantes e
 *       controle de logoff); a renovação só acontece enquanto ela estiver aberta.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final String MSG_INVALIDO = "E-mail ou senha inválidos.";
    /** Código do erro do login enquanto o e-mail não for confirmado (o frontend oferece reenviar o link). */
    public static final String ERRO_EMAIL_NAO_CONFIRMADO = "EMAIL_NAO_CONFIRMADO";
    /** Código do erro do login fora do horário de acesso definido pelo ADMIN. */
    public static final String ERRO_FORA_HORARIO = "FORA_HORARIO";

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final SecurityProperties props;
    private final LoginRateLimiter rateLimiter;
    private final AuditoriaService auditoria;
    private final SessaoService sessoes;
    private final SenhaInicialService senhaInicial;
    /** Horário de acesso por dia da semana (nulo só nos testes que montam o serviço à mão). */
    private final HorarioAcessoService horario;
    private final DateTimeFormatter formatoData;
    private final SecureRandom random = new SecureRandom();
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não denunciar isso. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       TokenService tokens, SecurityProperties props, LoginRateLimiter rateLimiter,
                       AuditoriaService auditoria, SessaoService sessoes, SenhaInicialService senhaInicial,
                       @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this(usuarios, refreshTokens, encoder, tokens, props, rateLimiter, auditoria, sessoes, senhaInicial, null, fuso);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       TokenService tokens, SecurityProperties props, LoginRateLimiter rateLimiter,
                       AuditoriaService auditoria, SessaoService sessoes, SenhaInicialService senhaInicial,
                       HorarioAcessoService horario,
                       @Value("${app.fuso-horario:America/Fortaleza}") String fuso) {
        this.horario = horario;
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.tokens = tokens;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.auditoria = auditoria;
        this.sessoes = sessoes;
        this.senhaInicial = senhaInicial;
        this.formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of(fuso));
        this.hashFalso = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public Emissao login(String email, String senha, String ip, String userAgent) {
        if (!rateLimiter.permitir(ip)) {
            auditoria.registrar(email, "LOGIN_BLOQUEADO_IP", "/auth/login", "POST", 429, false, "Muitas tentativas", ip);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Aguarde alguns minutos.");
        }
        String normalizado = email.trim().toLowerCase();
        Optional<Usuario> opt = usuarios.findByEmailIgnoreCase(normalizado);
        if (opt.isEmpty()) {
            encoder.matches(senha, hashFalso);
            auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 401, false, "E-mail não cadastrado", ip);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, MSG_INVALIDO);
        }
        Usuario u = opt.get();
        if (u.isBloqueado()) {
            if (u.getMotivoBloqueio() == null) {
                auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 423, false, "Conta bloqueada", ip);
                throw new BusinessException(HttpStatus.LOCKED,
                        "Conta bloqueada temporariamente por excesso de tentativas. Tente mais tarde.");
            }
            // Bloqueio do administrador: data e motivo só para quem acerta a senha
            // (quem sabe apenas o e-mail recebe a mensagem genérica).
            boolean senhaOk = encoder.matches(senha, u.getSenhaHash());
            auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", senhaOk ? 423 : 401, false,
                    "Acesso bloqueado pelo administrador", ip);
            if (!senhaOk) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, MSG_INVALIDO);
            }
            throw new BusinessException(HttpStatus.LOCKED, "Seu acesso está bloqueado até "
                    + formatoData.format(u.getBloqueadoAte()) + ". Motivo: " + u.getMotivoBloqueio());
        }
        if (!encoder.matches(senha, u.getSenhaHash())) {
            int falhas = u.getTentativasFalhas() + 1;
            u.setTentativasFalhas(falhas);
            String detalhe = "Senha incorreta (" + falhas + "/" + props.maxLoginAttempts() + ")";
            if (falhas >= props.maxLoginAttempts()) {
                u.setBloqueadoAte(Instant.now().plus(props.lockoutDuration()));
                u.setMotivoBloqueio(null);
                u.setBloqueadoPor(null);
                u.setTentativasFalhas(0);
                detalhe = "Conta bloqueada após " + falhas + " falhas";
            }
            auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 401, false, detalhe, ip);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, MSG_INVALIDO);
        }
        if (!u.isAtivo()) {
            auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 403, false, "Usuário inativo", ip);
            throw new BusinessException(HttpStatus.FORBIDDEN, "Usuário inativo. Procure um administrador.");
        }
        if (!u.isEmailVerificado()) {
            // Mesmo padrão dos demais sistemas: sem confirmar o e-mail, não entra.
            auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 403, false, "E-mail não confirmado", ip);
            throw new BusinessException(HttpStatus.FORBIDDEN, ERRO_EMAIL_NAO_CONFIRMADO,
                    "Seu e-mail ainda não foi confirmado. Use o link que enviamos ou peça um novo abaixo.");
        }
        if (horario != null) {
            // Fora do horário de acesso (exceto ADMIN): a senha está certa, então o usuário lê o
            // motivo e quando o acesso volta.
            Optional<String> recusa = horario.recusaAgora(u.getPerfil());
            if (recusa.isPresent()) {
                auditoria.registrar(normalizado, "LOGIN_FALHA", "/auth/login", "POST", 403, false,
                        "Fora do horário de acesso", ip);
                throw new BusinessException(HttpStatus.FORBIDDEN, ERRO_FORA_HORARIO, recusa.get());
            }
        }
        u.setTentativasFalhas(0);
        u.setBloqueadoAte(null);
        u.setMotivoBloqueio(null);
        u.setBloqueadoPor(null);
        u.setUltimoLogin(Instant.now());
        Sessao sessao = sessoes.iniciar(u, ip, userAgent);
        auditoria.registrar(u.getEmail(), "LOGIN", "/auth/login", "POST", 200, true, sessao.getDispositivo(), ip);
        return emitir(u, sessao, ip, userAgent);
    }

    /**
     * Sem rollback para a sessão encerrada: o motivo do encerramento (inatividade, duração máxima)
     * é gravado na mesma transação e não pode ser desfeito pela exceção.
     */
    @Transactional(noRollbackFor = {BusinessException.class, SessaoEncerradaException.class})
    public Emissao renovar(String refreshToken, String ip, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
        }
        RefreshToken atual = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente."));
        if (atual.isRevogado() && atual.getSessaoId() != null) {
            // Token revogado porque a SESSÃO foi encerrada (administrador, inatividade, limite de
            // sessões...): não é roubo; informa o motivo em vez de derrubar as outras sessões.
            Optional<Sessao> s = sessoes.buscar(atual.getSessaoId());
            if (s.isPresent() && !s.get().isAberta()) {
                throw new SessaoEncerradaException(java.util.Objects.requireNonNullElse(
                        s.get().getMotivoEncerramento(), MotivoEncerramento.INVALIDA));
            }
        }
        if (atual.isRevogado()) {
            // Reuso de um token já trocado: possível roubo. Derruba todas as sessões do usuário.
            sessoes.encerrarDoUsuario(atual.getUsuarioId(), MotivoEncerramento.REUSO_TOKEN, "sistema", null);
            refreshTokens.revogarTodos(atual.getUsuarioId());
            auditoria.registrar(String.valueOf(atual.getUsuarioId()), "REFRESH_REUTILIZADO", "/auth/refresh", "POST",
                    401, false, "Token revogado reutilizado; sessões encerradas", ip);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
        }
        if (!atual.isValido()) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente.");
        }
        Usuario u = usuarios.findById(atual.getUsuarioId())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Sessão expirada. Entre novamente."));
        if (horario != null && atual.getSessaoId() != null && horario.recusaAgora(u.getPerfil()).isPresent()) {
            // Terminou o horário de acesso: a renovação encerra a sessão com o motivo próprio.
            sessoes.encerrar(atual.getSessaoId(), MotivoEncerramento.FORA_HORARIO, "sistema");
            throw new SessaoEncerradaException(MotivoEncerramento.FORA_HORARIO);
        }
        Sessao sessao = atual.getSessaoId() == null
                // Token anterior à versão com sessões: abre uma agora.
                ? sessoes.iniciar(u, ip, userAgent)
                : sessoes.validarParaRenovar(atual.getSessaoId());
        atual.setRevogado(true);
        return emitir(u, sessao, ip, userAgent);
    }

    /**
     * Logoff: revoga o refresh token e encerra a sessão. {@code inatividade} = o navegador encerrou
     * porque o usuário ficou parado além do limite (o motivo fica no histórico de visitantes).
     */
    @Transactional
    public void logout(String refreshToken, String email, String sessaoDoToken, boolean inatividade, String ip) {
        logout(refreshToken, email, sessaoDoToken,
                inatividade ? MotivoEncerramento.INATIVIDADE : MotivoEncerramento.LOGOUT, ip);
    }

    /**
     * Logoff com o motivo informado pelo navegador: {@code LOGOUT}, {@code INATIVIDADE} ou
     * {@code FORA_HORARIO} (o horário de acesso do dia terminou).
     */
    @Transactional
    public void logout(String refreshToken, String email, String sessaoDoToken, MotivoEncerramento motivo, String ip) {
        String sessaoId = sessaoDoToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            Optional<RefreshToken> rt = refreshTokens.findByTokenHash(hash(refreshToken));
            rt.ifPresent(t -> t.setRevogado(true));
            if (rt.isPresent() && rt.get().getSessaoId() != null) {
                sessaoId = rt.get().getSessaoId();
            }
            if (email == null && rt.isPresent()) {
                // O navegador sai sem o access token (pode já ter vencido); o e-mail vem do refresh token.
                email = usuarios.findById(rt.get().getUsuarioId()).map(Usuario::getEmail).orElse(null);
            }
        }
        if (motivo != MotivoEncerramento.INATIVIDADE && motivo != MotivoEncerramento.FORA_HORARIO) {
            motivo = MotivoEncerramento.LOGOUT;
        }
        if (sessaoId != null) {
            sessoes.encerrar(sessaoId, motivo, email != null ? email : "navegador");
        }
        String acao = switch (motivo) {
            case INATIVIDADE -> "LOGOFF_INATIVIDADE";
            case FORA_HORARIO -> "LOGOFF_FORA_HORARIO";
            default -> "LOGOUT";
        };
        auditoria.registrar(email, acao, "/auth/logout", "POST", 204, true,
                null, ip);
    }

    private Emissao emitir(Usuario u, Sessao sessao, String ip,
                          String userAgent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // O refresh token nunca passa do fim absoluto da sessão.
        Instant expira = Instant.now().plus(props.refreshTokenTtl());
        if (sessao.getExpiraEm().isBefore(expira)) {
            expira = sessao.getExpiraEm();
        }
        refreshTokens.save(new RefreshToken(u.getId(), sessao.getId(), hash(refresh), expira, ip, userAgent));
        PoliticaSessao politica = sessoes.politica();
        boolean trocaSenha = senhaInicial.trocaObrigatoria(u);
        TokenResponse resp = new TokenResponse(tokens.gerarAccessToken(u, sessao.getId(), trocaSenha), "Bearer",
                tokens.accessTokenSeconds(), UsuarioResponse.of(u), SessaoInfo.of(sessao, politica), trocaSenha);
        // Cookie de sessão do navegador (sem Max-Age): fechar e reabrir o navegador sempre exige novo
        // login, como nos demais sistemas. O refresh continua limitado a "expira" no banco.
        Duration maxAge = null;
        return new Emissao(resp, refresh, maxAge);
    }

    static String hash(String valor) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Limpeza diária dos refresh tokens vencidos. */
    @Scheduled(cron = "0 30 3 * * *")
    public void limparExpirados() {
        refreshTokens.apagarExpirados(Instant.now().minusSeconds(86_400));
    }
}
