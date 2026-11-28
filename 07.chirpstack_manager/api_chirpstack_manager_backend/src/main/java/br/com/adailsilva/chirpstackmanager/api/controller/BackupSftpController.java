package br.com.adailsilva.chirpstackmanager.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.AuditoriaService;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpService;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpService.ConfigRequest;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpService.ConfigResponse;
import br.com.adailsilva.chirpstackmanager.api.service.sftp.SftpService.TesteResponse;
import br.com.adailsilva.chirpstackmanager.api.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Backup → SFTP (somente ADMIN): servidor, credencial, pasta remota, retenção, teste e confirmação
 * da chave do servidor.
 */
@RestController
@RequestMapping("/api/v1/backup/sftp")
@PreAuthorize(Perfis.ADMIN)
public class BackupSftpController {

    public record ConfirmarChaveRequest(String fingerprint, String tipoChave) {
    }

    private final SftpService service;
    private final AuditoriaService auditoria;

    public BackupSftpController(SftpService service, AuditoriaService auditoria) {
        this.service = service;
        this.auditoria = auditoria;
    }

    @GetMapping
    public ConfigResponse consultar() {
        return service.consultar();
    }

    @PutMapping
    public ConfigResponse salvar(@RequestBody ConfigRequest req, @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        ConfigResponse r = service.salvar(req, jwt.getSubject());
        boolean segredo = (req.senha() != null && !req.senha().isEmpty())
                || (req.chavePrivada() != null && !req.chavePrivada().isBlank());
        auditoria.registrar(jwt.getSubject(), "CONFIGURAR_SFTP", "/backup/sftp", "PUT", 200, true,
                r.usuario() + "@" + r.host() + ":" + r.porta() + (segredo ? " (credencial alterada)" : ""),
                ClientInfo.ip(http));
        return r;
    }

    @DeleteMapping
    public ConfigResponse remover(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        ConfigResponse r = service.remover();
        auditoria.registrar(jwt.getSubject(), "REMOVER_SFTP", "/backup/sftp", "DELETE", 200, true, null,
                ClientInfo.ip(http));
        return r;
    }

    @PostMapping("/testar")
    public TesteResponse testar(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        TesteResponse r = service.testar();
        auditoria.registrar(jwt.getSubject(), "TESTAR_SFTP", "/backup/sftp/testar", "POST", 200, r.sucesso(),
                r.mensagem(), ClientInfo.ip(http));
        return r;
    }

    @PostMapping("/chave")
    public ConfigResponse confirmarChave(@RequestBody ConfirmarChaveRequest req, @AuthenticationPrincipal Jwt jwt,
                                         HttpServletRequest http) {
        ConfigResponse r = service.confirmarChave(req.fingerprint(), req.tipoChave(), jwt.getSubject());
        auditoria.registrar(jwt.getSubject(), "CONFIRMAR_CHAVE_SFTP", "/backup/sftp/chave", "POST", 200, true,
                r.host() + " " + r.fingerprint(), ClientInfo.ip(http));
        return r;
    }

    @DeleteMapping("/chave")
    public ConfigResponse redefinirChave(@AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        ConfigResponse r = service.redefinirChave(jwt.getSubject());
        auditoria.registrar(jwt.getSubject(), "REDEFINIR_CHAVE_SFTP", "/backup/sftp/chave", "DELETE", 200, true,
                r.host(), ClientInfo.ip(http));
        return r;
    }
}
