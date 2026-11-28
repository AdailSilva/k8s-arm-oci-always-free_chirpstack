package br.com.adailsilva.chirpstackmanager.api.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.MonitoramentoDtos.RegraAlertaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.RegraAlerta;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.Severidade;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoRegra;
import br.com.adailsilva.chirpstackmanager.api.domain.validation.Emails;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.RegraAlertaRepository;

/** Cadastro das regras de alerta. */
@Service
public class RegraAlertaService {

    private final RegraAlertaRepository regras;
    private final DispositivoRepository dispositivos;

    public RegraAlertaService(RegraAlertaRepository regras, DispositivoRepository dispositivos) {
        this.regras = regras;
        this.dispositivos = dispositivos;
    }

    @Transactional(readOnly = true)
    public List<RegraAlerta> listar() {
        return regras.findAllByOrderByNomeAsc();
    }

    @Transactional(readOnly = true)
    public RegraAlerta buscar(Long id) {
        return regras.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Regra não encontrada."));
    }

    @Transactional
    public RegraAlerta criar(RegraAlertaRequest req, String usuario) {
        RegraAlerta r = new RegraAlerta();
        r.setCriadoEm(Instant.now());
        return salvar(r, req, usuario);
    }

    @Transactional
    public RegraAlerta atualizar(Long id, RegraAlertaRequest req, String usuario) {
        return salvar(buscar(id), req, usuario);
    }

    /** Excluir a regra apaga os alertas dela (FK em cascata). */
    @Transactional
    public void excluir(Long id) {
        regras.delete(buscar(id));
    }

    private RegraAlerta salvar(RegraAlerta r, RegraAlertaRequest req, String usuario) {
        if (req.dispositivoId() != null && !dispositivos.existsById(req.dispositivoId())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Dispositivo não encontrado.");
        }
        if (req.tipo() == TipoRegra.VALOR) {
            if (req.campo() == null || req.campo().isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe o campo (ex.: temperatura, rssi, bateria).");
            }
            if (!req.campo().trim().matches("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Campo inválido: use letras, números e _ separados por ponto (ex.: sensor.temperatura).");
            }
            if (req.operador() == null) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe a comparação.");
            }
        } else if (req.limite().signum() <= 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe os minutos sem comunicação (maior que zero).");
        }
        String destinatarios = req.destinatarios() == null ? null
                : String.join(", ", Arrays.stream(req.destinatarios().split("[,;\\s]+")).filter(s -> !s.isBlank()).toList());
        if (req.notificarEmail()) {
            if (destinatarios == null || destinatarios.isBlank()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe ao menos um destinatário do e-mail.");
            }
            for (String e : destinatarios.split(", ")) {
                if (!Emails.valido(e)) {
                    throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "E-mail inválido: " + e);
                }
            }
        }
        r.setNome(req.nome().trim());
        r.setTipo(req.tipo());
        r.setDispositivoId(req.dispositivoId());
        r.setCampo(req.tipo() == TipoRegra.VALOR ? req.campo().trim() : null);
        r.setOperador(req.tipo() == TipoRegra.VALOR ? req.operador() : null);
        r.setLimite(req.limite());
        r.setSeveridade(req.severidade() == null ? Severidade.AVISO : req.severidade());
        r.setNotificarEmail(req.notificarEmail());
        r.setDestinatarios(destinatarios == null || destinatarios.isBlank() ? null : destinatarios);
        r.setAtivo(req.ativo() == null || req.ativo());
        r.setAtualizadoEm(Instant.now());
        r.setAtualizadoPor(usuario);
        return regras.save(r);
    }
}
