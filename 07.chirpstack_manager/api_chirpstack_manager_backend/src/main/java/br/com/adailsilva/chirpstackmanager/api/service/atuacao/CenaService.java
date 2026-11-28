package br.com.adailsilva.chirpstackmanager.api.service.atuacao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaItemRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaItemResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.CenaResponse;
import br.com.adailsilva.chirpstackmanager.api.domain.dto.AtuacaoDtos.PoliticaRequest;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Cena;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.CenaItem;
import br.com.adailsilva.chirpstackmanager.api.domain.entity.Dispositivo;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.OrigemComando;
import br.com.adailsilva.chirpstackmanager.api.exception.BusinessException;
import br.com.adailsilva.chirpstackmanager.api.repository.CenaItemRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.CenaRepository;
import br.com.adailsilva.chirpstackmanager.api.repository.DispositivoRepository;

/** Cenas: vários comandos (em vários dispositivos) disparados de uma vez. */
@Service
public class CenaService {

    private final CenaRepository cenas;
    private final CenaItemRepository itens;
    private final DispositivoRepository dispositivos;
    private final ComandoService comandos;

    public CenaService(CenaRepository cenas, CenaItemRepository itens, DispositivoRepository dispositivos,
                       ComandoService comandos) {
        this.cenas = cenas;
        this.itens = itens;
        this.dispositivos = dispositivos;
        this.comandos = comandos;
    }

    @Transactional(readOnly = true)
    public List<CenaResponse> listar() {
        return cenas.findAllByOrderByNomeAsc().stream().map(this::resposta).toList();
    }

    @Transactional(readOnly = true)
    public Cena buscar(Long id) {
        return cenas.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Cena não encontrada."));
    }

    @Transactional(readOnly = true)
    public CenaResponse detalhe(Long id) {
        return resposta(buscar(id));
    }

    @Transactional
    public CenaResponse criar(CenaRequest req, String usuario) {
        if (cenas.existsByNomeIgnoreCase(req.nome().trim())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma cena com esse nome.");
        }
        Cena c = new Cena();
        c.setCriadoEm(Instant.now());
        return salvar(c, req, usuario);
    }

    @Transactional
    public CenaResponse atualizar(Long id, CenaRequest req, String usuario) {
        if (cenas.existsByNomeIgnoreCaseAndIdNot(req.nome().trim(), id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Já existe uma cena com esse nome.");
        }
        return salvar(buscar(id), req, usuario);
    }

    @Transactional
    public void excluir(Long id) {
        cenas.delete(buscar(id));
    }

    private CenaResponse salvar(Cena c, CenaRequest req, String usuario) {
        for (CenaItemRequest i : req.itens()) {
            comandos.validar(i.dispositivoId(), i.tipo(), i.parametros());
        }
        c.setNome(req.nome().trim());
        c.setDescricao(req.descricao() == null || req.descricao().isBlank() ? null : req.descricao().trim());
        c.setAtualizadoEm(Instant.now());
        c.setAtualizadoPor(usuario);
        c = cenas.saveAndFlush(c);
        itens.apagarDaCena(c.getId());
        int ordem = 0;
        for (CenaItemRequest i : req.itens()) {
            CenaItem it = new CenaItem();
            it.setCenaId(c.getId());
            it.setOrdem(ordem++);
            it.setDispositivoId(i.dispositivoId());
            it.setTipo(i.tipo());
            it.setParametros(comandos.escreverParametros(i.parametros()));
            itens.save(it);
        }
        return resposta(c);
    }

    /** Cria um comando por item (origem CENA). Relé crítico exige a confirmação de quem executa. */
    @Transactional
    public List<Long> executar(Long id, OrigemComando origem, String usuario, boolean confirmarCritico, PoliticaRequest politica) {
        Cena c = buscar(id);
        List<Long> criados = new ArrayList<>();
        for (CenaItem i : itens.findByCenaIdOrderByOrdemAsc(c.getId())) {
            criados.add(comandos.criar(i.getDispositivoId(), i.getTipo(), comandos.lerParametros(i.getParametros()), politica,
                    origem, c.getId(), usuario, confirmarCritico).getId());
        }
        return criados;
    }

    private CenaResponse resposta(Cena c) {
        List<CenaItem> lista = itens.findByCenaIdOrderByOrdemAsc(c.getId());
        Map<Long, String> nomes = dispositivos.findAllById(lista.stream().map(CenaItem::getDispositivoId).distinct().toList())
                .stream().collect(Collectors.toMap(Dispositivo::getId, Dispositivo::getNome));
        boolean critico = false;
        List<CenaItemResponse> rs = new ArrayList<>();
        for (CenaItem i : lista) {
            var p = comandos.lerParametros(i.getParametros());
            critico |= comandos.critico(i.getDispositivoId(), i.getTipo(), p);
            rs.add(new CenaItemResponse(i.getId(), i.getOrdem(), i.getDispositivoId(), nomes.get(i.getDispositivoId()),
                    i.getTipo(), i.getTipo().rotulo(), p, comandos.descrever(i.getDispositivoId(), i.getTipo(), p)));
        }
        return new CenaResponse(c.getId(), c.getNome(), c.getDescricao(), rs, critico, c.getAtualizadoEm(), c.getAtualizadoPor());
    }
}
