package br.com.adailsilva.chirpstackmanager.api.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EnergiaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EquipamentoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.EquipamentoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ModeloResponse;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.EnergiaService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.EquipamentoService;
import jakarta.validation.Valid;

/** Modelo de firmware, periféricos (relés, medidores) e consumo de energia de cada dispositivo. */
@RestController
@RequestMapping("/api/v1")
public class EquipamentoController {

    private final EquipamentoService service;
    private final EnergiaService energia;

    public EquipamentoController(EquipamentoService service, EnergiaService energia) {
        this.service = service;
        this.energia = energia;
    }

    @GetMapping("/equipamentos/modelos")
    @PreAuthorize(Perfis.LEITURA)
    public List<ModeloResponse> modelos() {
        return service.modelos();
    }

    @GetMapping("/dispositivos/{id}/equipamento")
    @PreAuthorize(Perfis.LEITURA)
    public EquipamentoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PutMapping("/dispositivos/{id}/equipamento")
    @PreAuthorize(Perfis.ESCRITA)
    public EquipamentoResponse salvar(@PathVariable Long id, @Valid @RequestBody EquipamentoRequest req,
                                      @AuthenticationPrincipal Jwt jwt) {
        return service.salvar(id, req, jwt.getSubject());
    }

    @DeleteMapping("/dispositivos/{id}/equipamento")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        service.remover(id);
    }

    @GetMapping("/dispositivos/{id}/energia")
    @PreAuthorize(Perfis.LEITURA)
    public EnergiaResponse energia(@PathVariable Long id, @RequestParam(defaultValue = "0") int canal,
                                   @RequestParam(required = false) Instant de, @RequestParam(required = false) Instant ate) {
        return energia.consumo(id, canal, de, ate);
    }
}
