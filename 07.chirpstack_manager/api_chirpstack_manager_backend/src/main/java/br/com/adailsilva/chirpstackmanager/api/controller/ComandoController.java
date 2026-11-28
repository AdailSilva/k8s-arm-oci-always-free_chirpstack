package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoDetalheResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ComandoResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ExecucaoRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.ResumoComandosResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.response.Pagina;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Comando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.StatusComando;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.ComandoService;
import br.com.adailsilva.chirpstackmanager.api.service.atuacao.FilaComandosService;
import jakarta.validation.Valid;

/** Fila de comandos (downlinks) com novas tentativas. */
@RestController
@RequestMapping("/api/v1")
public class ComandoController {

    private final ComandoService service;
    private final FilaComandosService fila;

    public ComandoController(ComandoService service, FilaComandosService fila) {
        this.service = service;
        this.fila = fila;
    }

    @PostMapping("/dispositivos/{id}/comandos")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.CREATED)
    public ComandoResponse enviar(@PathVariable Long id, @Valid @RequestBody ComandoRequest req, @AuthenticationPrincipal Jwt jwt) {
        Comando c = service.criar(id, req.tipo(), req.parametros(), req.politica(), OrigemComando.MANUAL, null,
                jwt.getSubject(), req.confirmarCritico());
        return service.resposta(c, service.nomes(List.of(c)));
    }

    @GetMapping("/dispositivos/{id}/comandos")
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<ComandoResponse> doDispositivo(@PathVariable Long id, @RequestParam(required = false) List<StatusComando> status,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "50") int tamanho) {
        return pagina(service.listar(id, status, pagina, tamanho));
    }

    @GetMapping("/comandos")
    @PreAuthorize(Perfis.LEITURA)
    public Pagina<ComandoResponse> listar(@RequestParam(required = false) Long dispositivoId,
                                          @RequestParam(required = false) List<StatusComando> status,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "50") int tamanho) {
        return pagina(service.listar(dispositivoId, status, pagina, tamanho));
    }

    @GetMapping("/comandos/resumo")
    @PreAuthorize(Perfis.LEITURA)
    public ResumoComandosResponse resumo() {
        return service.resumo();
    }

    @GetMapping("/comandos/{id}")
    @PreAuthorize(Perfis.LEITURA)
    public ComandoDetalheResponse detalhe(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @PostMapping("/comandos/{id}/cancelar")
    @PreAuthorize(Perfis.ESCRITA)
    public ComandoResponse cancelar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Comando c = fila.cancelar(id, jwt.getSubject());
        return service.resposta(c, service.nomes(List.of(c)));
    }

    @PostMapping("/comandos/{id}/reenviar")
    @PreAuthorize(Perfis.ESCRITA)
    @ResponseStatus(HttpStatus.CREATED)
    public ComandoResponse reenviar(@PathVariable Long id, @RequestBody(required = false) ExecucaoRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        Comando c = service.reenviar(id, jwt.getSubject(), req != null && req.confirmarCritico());
        return service.resposta(c, service.nomes(List.of(c)));
    }

    private Pagina<ComandoResponse> pagina(org.springframework.data.domain.Page<Comando> p) {
        return new Pagina<>(service.respostas(p.getContent(), r -> r), p.getTotalElements());
    }
}
