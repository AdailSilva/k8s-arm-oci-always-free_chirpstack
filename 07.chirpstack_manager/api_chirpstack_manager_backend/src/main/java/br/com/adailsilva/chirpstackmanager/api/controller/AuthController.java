package br.com.adailsilva.chirpstackmanager.api.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.LoginRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.LogoutRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.Emissao;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AuthDtos.TokenResponse;
import br.com.adailsilva.chirpstackmanager.api.config.SecurityProperties;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.PerfilUpdateRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.TrocaSenhaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.UsuarioDtos.UsuarioResponse;
import br.com.adailsilva.chirpstackmanager.api.service.UsuarioService;
import br.com.adailsilva.chirpstackmanager.api.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import br.com.adailsilva.chirpstackmanager.api.exception.CookieClearingException;
import br.com.adailsilva.chirpstackmanager.api.service.AuthService;
import br.com.adailsilva.chirpstackmanager.api.service.RecuperacaoSenhaService;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.EsqueciSenhaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.RedefinirSenhaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.ConfirmarEmailRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.ReenviarConfirmacaoRequest;
import java.util.Map;

/**
 * Autenticação. O access token volta no corpo (o frontend guarda só em memória) e o refresh token
 * vai num cookie {@code HttpOnly; SameSite=Strict} restrito a {@code /api/v1/auth}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String COOKIE = "csm_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final UsuarioService usuarios;
    private final SecurityProperties props;
    private final RecuperacaoSenhaService recuperacao;

    public AuthController(AuthService auth, UsuarioService usuarios, SecurityProperties props,
                          RecuperacaoSenhaService recuperacao) {
        this.auth = auth;
        this.usuarios = usuarios;
        this.props = props;
        this.recuperacao = recuperacao;
    }

    /** "Esqueci a senha": responde 204 sempre, exista ou não o e-mail. */
    @PostMapping("/senha/esqueci")
    public ResponseEntity<Void> esqueci(@Valid @RequestBody EsqueciSenhaRequest req, HttpServletRequest http) {
        recuperacao.solicitar(req.email(), ClientInfo.ip(http));
        return ResponseEntity.noContent().build();
    }

    /** Nova senha pelo link recebido por e-mail. */
    @PostMapping("/senha/redefinir")
    public ResponseEntity<Void> redefinir(@Valid @RequestBody RedefinirSenhaRequest req, HttpServletRequest http) {
        recuperacao.redefinir(req.token(), req.novaSenha(), ClientInfo.ip(http));
        return ResponseEntity.noContent().build();
    }

    /** Confirmação do e-mail pelo link (público). Devolve a mensagem para a tela. */
    @PostMapping("/email/confirmar")
    public Map<String, String> confirmarEmail(@Valid @RequestBody ConfirmarEmailRequest req) {
        return Map.of("mensagem", recuperacao.confirmarEmail(req.token()));
    }

    /** "Reenviar confirmação" (público): responde 204 sempre, exista ou não o e-mail. */
    @PostMapping("/email/reenviar")
    public ResponseEntity<Void> reenviarConfirmacao(@Valid @RequestBody ReenviarConfirmacaoRequest req) {
        recuperacao.reenviarConfirmacao(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        Emissao s = auth.login(req.email(), req.senha(), ClientInfo.ip(http), http.getHeader(HttpHeaders.USER_AGENT));
        return comCookie(s);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(name = COOKIE, required = false) String refresh,
                                                 HttpServletRequest http) {
        try {
            Emissao s = auth.renovar(refresh, ClientInfo.ip(http), http.getHeader(HttpHeaders.USER_AGENT));
            return comCookie(s);
        } catch (RuntimeException e) {
            // Sessão inválida: apaga o cookie antes de responder 401.
            throw new CookieClearingException(e, cookie("", Duration.ZERO));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String refresh,
                                       @AuthenticationPrincipal Jwt jwt,
                                       @RequestBody(required = false) LogoutRequest req, HttpServletRequest http) {
        String m = req == null || req.motivo() == null ? "" : req.motivo().toUpperCase();
        MotivoEncerramento motivo = switch (m) {
            case "INATIVIDADE" -> MotivoEncerramento.INATIVIDADE;
            case "FORA_HORARIO" -> MotivoEncerramento.FORA_HORARIO;
            default -> MotivoEncerramento.LOGOUT;
        };
        auth.logout(refresh, jwt != null ? jwt.getSubject() : null, jwt != null ? jwt.getClaimAsString("sid") : null,
                motivo, ClientInfo.ip(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UsuarioResponse.of(usuarios.porEmail(jwt.getSubject()));
    }

    @PutMapping("/me")
    public UsuarioResponse atualizarMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PerfilUpdateRequest req) {
        return UsuarioResponse.of(usuarios.atualizarMe(jwt.getSubject(), req.nome(), req.cadastro()));
    }

    @PostMapping("/me/senha")
    public ResponseEntity<Void> trocarSenha(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TrocaSenhaRequest req) {
        usuarios.trocarSenha(jwt.getSubject(), req.senhaAtual(), req.novaSenha());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> comCookie(Emissao s) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), s.cookieMaxAge()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(s.resposta());
    }

    /** {@code maxAge} nulo = cookie de sessão do navegador (a política "manter conectado" está desligada). */
    private ResponseCookie cookie(String valor, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(COOKIE, valor)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
        if (maxAge != null) {
            b.maxAge(maxAge);
        }
        return b.build();
    }
}
