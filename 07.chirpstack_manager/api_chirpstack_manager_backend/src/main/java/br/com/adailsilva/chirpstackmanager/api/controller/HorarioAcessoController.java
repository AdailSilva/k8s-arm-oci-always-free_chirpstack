package br.com.adailsilva.chirpstackmanager.api.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.Dia;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.SalvarRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.SalvarResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.HorarioAcessoDtos.Situacao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Perfil;
import br.com.adailsilva.chirpstackmanager.api.security.Perfis;
import br.com.adailsilva.chirpstackmanager.api.service.AuditoriaService;
import br.com.adailsilva.chirpstackmanager.api.service.HorarioAcessoService;
import br.com.adailsilva.chirpstackmanager.api.web.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Sistema → Horário de acesso (ADMIN) e a situação do usuário logado (aviso dos 5 minutos).
 */
@RestController
@RequestMapping("/api/v1/horario-acesso")
@Validated
public class HorarioAcessoController {

    private final HorarioAcessoService service;
    private final AuditoriaService auditoria;

    public HorarioAcessoController(HorarioAcessoService service, AuditoriaService auditoria) {
        this.service = service;
        this.auditoria = auditoria;
    }

    @GetMapping
    @PreAuthorize(Perfis.ADMIN)
    public List<Dia> listar() {
        return service.listar();
    }

    @PutMapping
    @PreAuthorize(Perfis.ADMIN)
    public SalvarResponse salvar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SalvarRequest req,
                                 HttpServletRequest http) {
        List<Dia> dias = service.salvar(req.dias(), jwt.getSubject());
        int encerradas = Boolean.TRUE.equals(req.aplicarAgora()) ? service.aplicarAgora() : 0;
        auditoria.registrar(jwt.getSubject(), "HORARIO_ACESSO", "/horario-acesso", "PUT", 200, true,
                resumo(dias) + (encerradas > 0 ? "; " + encerradas + " sessão(ões) encerrada(s)" : ""),
                ClientInfo.ip(http));
        return new SalvarResponse(dias, encerradas);
    }

    @GetMapping("/situacao")
    public Situacao situacao(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        Perfil perfil = roles != null && roles.contains(Perfil.ADMIN.name()) ? Perfil.ADMIN : Perfil.VISUALIZADOR;
        return service.situacao(perfil);
    }

    private static String resumo(List<Dia> dias) {
        StringBuilder sb = new StringBuilder();
        for (Dia d : dias) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            String nome = d.diaNome() == null ? String.valueOf(d.diaSemana()) : d.diaNome().substring(0, 3);
            sb.append(nome).append(' ');
            if (!Boolean.TRUE.equals(d.permitido())) {
                sb.append("sem acesso");
            } else if (d.horaInicio() == null) {
                sb.append("livre");
            } else {
                sb.append(d.horaInicio()).append('-').append(d.horaFim());
            }
        }
        return sb.toString();
    }
}
