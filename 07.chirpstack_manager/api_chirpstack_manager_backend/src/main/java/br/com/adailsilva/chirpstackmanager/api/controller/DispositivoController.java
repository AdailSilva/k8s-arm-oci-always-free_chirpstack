package br.com.adailsilva.chirpstackmanager.api.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.DispositivoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.DispositivoUpdateRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.EventoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.LeituraResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.SerieResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.response.Pagina;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.DispositivoService;
import jakarta.validation.Valid;

/** Dispositivos LoRaWAN recebidos do ChirpStack, com leituras, eventos e séries. */
@RestController
@RequestMapping("/api/v1/dispositivos")
public class DispositivoController {

    private final DispositivoService service;

    public DispositivoController(DispositivoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<DispositivoResponse> listar(@RequestParam(required = false) String busca,
                                              @RequestParam(defaultValue = "false") boolean inativos,
                                              @RequestParam(defaultValue = "0") int pagina,
                                              @RequestParam(defaultValue = "50") int tamanho) {
        Instant limite = service.limiteOnline();
        return Pagina.of(service.listar(busca, inativos, pagina, tamanho), d -> DispositivoResponse.of(d, limite));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.LEITURA)
    public DispositivoResponse buscar(@PathVariable Long id) {
        return DispositivoResponse.of(service.buscar(id), service.limiteOnline());
    }

    @PutMapping("/{id}")
    @PreAuthorize(Perfis.ESCRITA)
    public DispositivoResponse atualizar(@PathVariable Long id, @Valid @RequestBody DispositivoUpdateRequest req) {
        return DispositivoResponse.of(service.atualizar(id, req), service.limiteOnline());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Perfis.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    @GetMapping("/{id}/leituras")
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<LeituraResponse> leituras(@PathVariable Long id, @RequestParam(required = false) Instant de,
                                            @RequestParam(required = false) Instant ate,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "50") int tamanho) {
        return Pagina.of(service.leituras(id, de, ate, pagina, tamanho), LeituraResponse::of);
    }

    @GetMapping("/{id}/eventos")
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<EventoResponse> eventos(@PathVariable Long id, @RequestParam(required = false) Instant de,
                                          @RequestParam(required = false) Instant ate,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "50") int tamanho) {
        return Pagina.of(service.eventos(id, de, ate, pagina, tamanho), EventoResponse::of);
    }

    @GetMapping("/{id}/series")
    @PreAuthorize(Perfis.LEITURA)
    public List<SerieResponse> series(@PathVariable Long id, @RequestParam(required = false) Instant de,
                                      @RequestParam(required = false) Instant ate) {
        return service.series(id, de, ate);
    }
}
