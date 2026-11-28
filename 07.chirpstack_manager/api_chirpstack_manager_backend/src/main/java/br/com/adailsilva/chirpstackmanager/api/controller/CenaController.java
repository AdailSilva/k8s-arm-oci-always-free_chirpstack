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

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ExecucaoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ExecucaoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.CenaService;
import jakarta.validation.Valid;

/** Cenas: vários comandos disparados de uma vez. */
@RestController
@RequestMapping("/api/v1/cenas")
public class CenaController {

    private final CenaService service;

    public CenaController(CenaService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    public List<CenaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.LEITURA)
    public CenaResponse buscar(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @PostMapping
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.CREATED)
    public CenaResponse criar(@Valid @RequestBody CenaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.criar(req, jwt.getSubject());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    public CenaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CenaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.atualizar(id, req, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    @PostMapping("/{id}/executar")
    @PreAuthorize(Perfis.ESCRITA)
    public ExecucaoResponse executar(@PathVariable Long id, @RequestBody(required = false) ExecucaoRequest req,
                                     @AuthenticationPrincipal Jwt jwt) {
        return new ExecucaoResponse(service.executar(id, OrigemComando.CENA, jwt.getSubject(),
                req != null && req.confirmarCritico(), null));
    }
}
