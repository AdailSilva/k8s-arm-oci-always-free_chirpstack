package br.com.adailsilva.chirpstackmanager.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.DesbloquearRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.Preferencias;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.SuporteRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.ContaDtos.SuporteResponse;
import br.com.adailsilva.chirpstackmanager.api.service.ContaService;
import br.com.adailsilva.chirpstackmanager.api.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Conta do usuário logado (todos os perfis): preferências, tela bloqueada e suporte.
 */
@RestController
@RequestMapping("/api/v1/conta")
@Validated
public class ContaController {

    private final ContaService service;

    public ContaController(ContaService service) {
        this.service = service;
    }

    /** 204 quando o usuário ainda não salvou preferências (o navegador usa o padrão). */
    @GetMapping("/preferencias")
    public ResponseEntity<Preferencias> preferencias(@AuthenticationPrincipal Jwt jwt) {
        Preferencias p = service.preferencias(jwt.getSubject());
        return p == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(p);
    }

    @PutMapping("/preferencias")
    public Preferencias salvarPreferencias(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Preferencias p) {
        return service.salvarPreferencias(jwt.getSubject(), p);
    }

    @PostMapping("/desbloquear")
    public ResponseEntity<Void> desbloquear(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DesbloquearRequest req,
                                            HttpServletRequest http) {
        service.desbloquear(jwt.getSubject(), jwt.getClaimAsString("sid"), req.senha(), ClientInfo.ip(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/suporte")
    public SuporteResponse suporte(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuporteRequest req,
                                   HttpServletRequest http) {
        return service.abrirChamado(jwt.getSubject(), req, ClientInfo.ip(http));
    }
}
