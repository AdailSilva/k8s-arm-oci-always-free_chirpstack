package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
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

import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.AlterarSenhaEmailRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.ContaEmailResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.EmailDtos.TestarSenhaEmailRequest;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.ConfiguracaoEmailService;
import jakarta.validation.Valid;

/** Sistema → Conta de e-mail (somente ADMIN). A senha nunca volta na resposta. */
@RestController
@RequestMapping("/api/v1/conta-email")
@PreAuthorize(Perfis.ADMIN)
public class ContaEmailController {

    private final ConfiguracaoEmailService service;

    public ContaEmailController(ConfiguracaoEmailService service) {
        this.service = service;
    }

    @GetMapping
    public ContaEmailResponse situacao() {
        return service.situacao();
    }

    @PutMapping
    public ContaEmailResponse alterar(@Valid @RequestBody AlterarSenhaEmailRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.alterar(req, jwt.getSubject());
    }

    @DeleteMapping
    public ContaEmailResponse remover(@AuthenticationPrincipal Jwt jwt) {
        return service.remover(jwt.getSubject());
    }

    /** Conecta e autentica com a senha informada (ou a vigente), sem gravar. */
    @PostMapping("/testar")
    public ResponseEntity<Void> testar(@Valid @RequestBody(required = false) TestarSenhaEmailRequest req) {
        service.testar(req == null ? null : req.senha());
        return ResponseEntity.noContent().build();
    }

    /** Envia um e-mail de teste para o próprio usuário. */
    @PostMapping("/enviar-teste")
    public Map<String, String> enviarTeste(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("destinatario", service.enviarTeste(jwt.getSubject()));
    }
}
