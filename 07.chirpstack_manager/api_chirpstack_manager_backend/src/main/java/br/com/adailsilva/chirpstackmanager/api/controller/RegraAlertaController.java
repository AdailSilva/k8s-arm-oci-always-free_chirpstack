package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.RegraAlertaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.RegraAlertaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;
import br.com.adailsilva.chirpstackmanager.api.service.RegraAlertaService;
import jakarta.validation.Valid;

/** Regras de alerta: leitura para todos, cadastro para ADMIN e OPERADOR. */
@RestController
@RequestMapping("/api/v1/regras-alerta")
public class RegraAlertaController {

    private final RegraAlertaService service;
    private final DispositivoService dispositivos;

    public RegraAlertaController(RegraAlertaService service, DispositivoService dispositivos) {
        this.service = service;
        this.dispositivos = dispositivos;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    public List<RegraAlertaResponse> listar() {
        List<RegraAlerta> lista = service.listar();
        Set<Long> ids = lista.stream().map(RegraAlerta::getDispositivoId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Dispositivo> mapa = dispositivos.porIds(ids);
        return lista.stream().map(r -> RegraAlertaResponse.of(r, nome(mapa.get(r.getDispositivoId())))).toList();
    }

    @PostMapping
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.CREATED)
    public RegraAlertaResponse criar(@Valid @RequestBody RegraAlertaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return resposta(service.criar(req, jwt.getSubject()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    public RegraAlertaResponse atualizar(@PathVariable Long id, @Valid @RequestBody RegraAlertaRequest req,
                                         @AuthenticationPrincipal Jwt jwt) {
        return resposta(service.atualizar(id, req, jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    private RegraAlertaResponse resposta(RegraAlerta r) {
        Dispositivo d = r.getDispositivoId() == null ? null : dispositivos.porIds(Set.of(r.getDispositivoId())).get(r.getDispositivoId());
        return RegraAlertaResponse.of(r, nome(d));
    }

    private static String nome(Dispositivo d) {
        return d == null ? null : d.getNome();
    }
}
