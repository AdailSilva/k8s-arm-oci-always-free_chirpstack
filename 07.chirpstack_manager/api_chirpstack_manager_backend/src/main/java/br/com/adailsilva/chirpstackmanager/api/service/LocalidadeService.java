package br.com.adailsilva.chirpstackmanager.api.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Consulta de CEP (ViaCEP) para o endereço do cadastro de usuários, como nos demais sistemas.
 * Com tempo limite e resposta "não encontrado" em qualquer falha: nunca trava o formulário nem
 * devolve 5xx.
 */
@Service
public class LocalidadeService {

    private static final Logger log = LoggerFactory.getLogger(LocalidadeService.class);

    /** Endereço do CEP; {@code encontrado = false} quando o CEP não existe ou a consulta falhou. */
    public record Cep(boolean encontrado, String cep, String logradouro, String complemento, String bairro,
                      String cidade, String uf) {
        static Cep naoEncontrado() {
            return new Cep(false, null, null, null, null, null, null);
        }
    }

    private final RestClient viacep;

    public LocalidadeService() {
        JdkClientHttpRequestFactory rf = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        rf.setReadTimeout(Duration.ofSeconds(8));
        this.viacep = RestClient.builder().baseUrl("https://viacep.com.br/ws").requestFactory(rf).build();
    }

    public Cep buscarCep(String cep) {
        String limpo = cep == null ? "" : cep.replaceAll("\\D", "");
        if (limpo.length() != 8) {
            return Cep.naoEncontrado();
        }
        try {
            Map<?, ?> m = viacep.get().uri("/{cep}/json/", limpo).retrieve().body(Map.class);
            if (m == null || m.get("cep") == null || Boolean.parseBoolean(String.valueOf(m.get("erro")))) {
                return Cep.naoEncontrado();
            }
            return new Cep(true, str(m.get("cep")), str(m.get("logradouro")), str(m.get("complemento")),
                    str(m.get("bairro")), str(m.get("localidade")), str(m.get("uf")));
        } catch (Exception e) {
            log.warn("Falha ao consultar o CEP {} no ViaCEP: {}", limpo, e.getMessage());
            return Cep.naoEncontrado();
        }
    }

    private static String str(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
    }
}
