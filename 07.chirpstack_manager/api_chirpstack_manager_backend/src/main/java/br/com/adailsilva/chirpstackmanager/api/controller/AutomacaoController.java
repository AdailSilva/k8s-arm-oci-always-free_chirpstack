package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AutomacaoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.AutomacaoResponse;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.AutomacaoService;
import jakarta.validation.Valid;

/** Automações: alerta dispara comando ou cena. */
@RestController
@RequestMapping("/api/v1/automacoes")
public class AutomacaoController {

    private final AutomacaoService service;

    public AutomacaoController(AutomacaoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    public List<AutomacaoResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.CREATED)
    public AutomacaoResponse criar(@Valid @RequestBody AutomacaoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(req, jwt.getSubject());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    public AutomacaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AutomacaoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.atualizar(id, req, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
