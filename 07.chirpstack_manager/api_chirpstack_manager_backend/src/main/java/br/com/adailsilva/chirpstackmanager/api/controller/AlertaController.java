package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.AlertaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.response.Pagina;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Alerta;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusAlerta;
import br.com.adailsilva.chirpstackmanager.api.repository.RegraAlertaRepository;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.AlertaService;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;

/** Alertas disparados: lista para todos, reconhecer para ADMIN e OPERADOR. */
@RestController
@RequestMapping("/api/v1/alertas")
public class AlertaController {

    private final AlertaService service;
    private final RegraAlertaRepository regras;
    private final DispositivoService dispositivos;

    public AlertaController(AlertaService service, RegraAlertaRepository regras, DispositivoService dispositivos) {
        this.service = service;
        this.regras = regras;
        this.dispositivos = dispositivos;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<AlertaResponse> listar(@RequestParam(required = false) StatusAlerta status,
                                         @RequestParam(required = false) Long dispositivoId,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "50") int tamanho) {
        Page<Alerta> p = service.listar(status, dispositivoId, pagina, tamanho);
        Map<Long, RegraAlerta> rs = regras.findAllById(p.stream().map(Alerta::getRegraId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(RegraAlerta::getId, Function.identity()));
        Map<Long, Dispositivo> ds = dispositivos.porIds(p.stream().map(Alerta::getDispositivoId).collect(Collectors.toSet()));
        return Pagina.of(p, a -> AlertaResponse.of(a, rs.get(a.getRegraId()), ds.get(a.getDispositivoId())));
    }

    @PostMapping("/{id}/reconhecer")
    @PreAuthorize(Perfis.ESCRITA)
    public AlertaResponse reconhecer(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Alerta a = service.reconhecer(id, jwt.getSubject());
        RegraAlerta r = regras.findById(a.getRegraId()).orElse(null);
        Dispositivo d = dispositivos.porIds(Set.of(a.getDispositivoId())).get(a.getDispositivoId());
        return AlertaResponse.of(a, r, d);
    }
}
