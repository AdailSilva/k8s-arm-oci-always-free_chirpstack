package br.com.adailsilva.chirpstackmanager.api.security.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.MotivoEncerramento;
import br.com.adailsilva.chirpstackmanager.api.exception.SessaoEncerradaException;
import br.com.adailsilva.chirpstackmanager.api.security.service.SenhaInicialService;
import br.com.adailsilva.chirpstackmanager.api.service.SessaoService;

/**
 * Confere, depois da validação do JWT, se a sessão do token ({@code sid}) continua aberta.
 * <p>
 * Sessão encerrada → 401 com {@code X-Sessao-Status: ENCERRADA}: o frontend não tenta renovar e
 * leva o usuário ao login mostrando o motivo. Token sem {@code sid} (emitido antes desta versão)
 * → 401 comum: o frontend renova e recebe um token já ligado a uma sessão.
 * <p>
 * Token com a troca da senha inicial pendente (claim {@code trocaSenha}) → 403
 * {@code TROCA_SENHA_OBRIGATORIA} em tudo, menos {@code GET /api/v1/auth/me},
 * {@code POST /api/v1/auth/me/senha} e o sinal de atividade da sessão.
 * <p>
 * Não é um {@code @Component} de propósito: é registrado só na cadeia do Spring Security
 * (ver {@code SecurityConfig}), para não rodar duas vezes como filtro do servlet.
 */
public class SessaoAtivaFilter extends OncePerRequestFilter {

    private final SessaoService sessoes;

    public SessaoAtivaFilter(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = caminho(request);
        return uri.startsWith("/api/v1/auth/logout") || uri.startsWith("/api/v1/auth/refresh")
                || uri.startsWith("/api/v1/auth/login") || !uri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            String sid = jwt.getToken().getClaimAsString("sid");
            if (sid == null || sid.isBlank()) {
                SecurityContextHolder.clearContext();
                escrever(response, null, "NAO_AUTENTICADO", "Sessão expirada ou inválida. Entre novamente.");
                return;
            }
            Optional<MotivoEncerramento> motivo = sessoes.verificar(sid);
            if (motivo.isPresent()) {
                SecurityContextHolder.clearContext();
                escrever(response, motivo.get(), SessaoEncerradaException.CODIGO, motivo.get().mensagem());
                return;
            }
            if (Boolean.TRUE.equals(jwt.getToken().getClaimAsBoolean(SenhaInicialService.CLAIM))
                    && !liberadoNaTrocaDeSenha(request)) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setHeader("Cache-Control", "no-store");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"status\":403,\"erro\":\"" + SenhaInicialService.ERRO
                        + "\",\"mensagem\":\"" + SenhaInicialService.MENSAGEM + "\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Caminho da requisição sem o context-path ({@code /chirpstack-manager}): as regras abaixo
     * comparam com {@code /api/...}, que é o caminho dentro da aplicação.
     */
    private static String caminho(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Únicas chamadas aceitas enquanto a senha inicial não for trocada. */
    private static boolean liberadoNaTrocaDeSenha(HttpServletRequest request) {
        String uri = caminho(request);
        String metodo = request.getMethod();
        return ("GET".equals(metodo) && uri.equals("/api/v1/auth/me"))
                || ("POST".equals(metodo) && uri.equals("/api/v1/auth/me/senha"))
                // sinal de atividade da sessão (monitor de inatividade do navegador)
                || ("GET".equals(metodo) && uri.equals("/api/v1/sessao"))
                || ("POST".equals(metodo) && uri.equals("/api/v1/sessao/atividade"))
                // situação do horário de acesso (aviso dos 5 minutos)
                || ("GET".equals(metodo) && uri.equals("/api/v1/horario-acesso/situacao"));
    }

    private static void escrever(HttpServletResponse res, MotivoEncerramento motivo, String erro, String mensagem)
            throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        if (motivo != null) {
            res.setHeader(SessaoEncerradaException.HEADER, SessaoEncerradaException.ENCERRADA);
            res.setHeader(SessaoEncerradaException.HEADER_MOTIVO, motivo.name());
        }
        res.setHeader("Cache-Control", "no-store");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"status\":401,\"erro\":\"" + erro + "\",\"mensagem\":\"" + mensagem.replace("\"", "'")
                + "\"" + (motivo != null ? ",\"detalhe\":null,\"motivo\":\"" + motivo.name() + "\"" : "") + "}");
    }
}
