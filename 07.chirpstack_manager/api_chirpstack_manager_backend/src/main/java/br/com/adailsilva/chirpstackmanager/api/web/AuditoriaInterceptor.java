package br.com.adailsilva.chirpstackmanager.api.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.adailsilva.chirpstackmanager.api.service.AuditoriaService;

/**
 * Audita toda requisição de escrita (POST, PUT, DELETE) feita aos recursos do ChirpStack, ao
 * monitoramento (dispositivos monitorados, regras de alerta e alertas) e aos usuários e à conta
 * de e-mail do painel. Corpos não são gravados, pois podem conter chaves e senhas.
 */
@Component
public class AuditoriaInterceptor implements HandlerInterceptor {

    private final AuditoriaService service;

    public AuditoriaInterceptor(AuditoriaService service) {
        this.service = service;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String metodo = request.getMethod();
        if ("GET".equals(metodo) || "OPTIONS".equals(metodo) || "HEAD".equals(metodo)) {
            return;
        }
        // Caminho sem o context-path (/chirpstack-manager): a auditoria grava "/usuarios/3", não o prefixo.
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth != null && auth.isAuthenticated() ? auth.getName() : null;
        int status = response.getStatus();
        service.registrar(usuario, acao(metodo, uri), uri.replaceFirst("^/api/v1", ""), metodo, status,
                status < 400 && ex == null, null, ClientInfo.ip(request));
    }

    public static String acao(String metodo, String uri) {
        if (uri.endsWith("/reconhecer")) {
            return "RECONHECER";
        }
        if (uri.endsWith("/get-random-dev-addr") || uri.endsWith("/get-next-f-cnt-down")) {
            return "CONSULTAR";
        }
        if (uri.endsWith("/generate-certificate") || uri.endsWith("/mqtt/certificate")) {
            return "GERAR_CERTIFICADO";
        }
        if (uri.endsWith("/activate")) {
            return "ATIVAR";
        }
        if (uri.endsWith("/queue") && "POST".equals(metodo)) {
            return "ENFILEIRAR";
        }
        if (uri.endsWith("/password")) {
            return "ALTERAR_SENHA";
        }
        if (uri.endsWith("/cancelar")) {
            return "CANCELAR";
        }
        if (uri.endsWith("/reenviar")) {
            return "REENVIAR";
        }
        if (uri.endsWith("/executar")) {
            return "EXECUTAR";
        }
        if (uri.endsWith("/comandos") && "POST".equals(metodo)) {
            return "ENVIAR_COMANDO";
        }
        return switch (metodo) {
            case "POST" -> "CRIAR";
            case "PUT" -> "ATUALIZAR";
            case "DELETE" -> "EXCLUIR";
            default -> metodo;
        };
    }
}
