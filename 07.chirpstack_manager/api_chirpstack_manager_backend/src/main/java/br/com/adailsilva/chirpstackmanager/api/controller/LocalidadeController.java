package br.com.adailsilva.chirpstackmanager.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.service.LocalidadeService;
import br.com.adailsilva.chirpstackmanager.api.service.LocalidadeService.Cep;

/** Localidades para os formulários com endereço (usuário autenticado). */
@RestController
@RequestMapping("/api/v1/localidades")
public class LocalidadeController {

    private final LocalidadeService service;

    public LocalidadeController(LocalidadeService service) {
        this.service = service;
    }

    @GetMapping("/cep/{cep}")
    public Cep cep(@PathVariable String cep) {
        return service.buscarCep(cep);
    }
}
