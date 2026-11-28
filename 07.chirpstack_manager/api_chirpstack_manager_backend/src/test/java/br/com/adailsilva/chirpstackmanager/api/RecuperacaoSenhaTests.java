package br.com.adailsilva.chirpstackmanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.adailsilva.chirpstackmanager.api.repository.TokenRedefinicaoRepository;

/**
 * "Esqueci a senha": o pedido responde 204 exista ou não o e-mail, e só um token válido redefine.
 * (O envio do e-mail falha sem servidor SMTP, o que não afeta o pedido.)
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recuperacao;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.security.jwt-secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "app.chirpstack.api-token=token-de-teste",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1"
})
@AutoConfigureMockMvc
class RecuperacaoSenhaTests {

    @Autowired MockMvc mvc;
    @Autowired TokenRedefinicaoRepository tokens;

    @Test
    void pedidoNaoRevelaEmailETokenInvalidoNaoRedefine() throws Exception {
        mvc.perform(post("/api/v1/auth/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ninguem@exemplo.com\"}")).andExpect(status().isNoContent());
        assertThat(tokens.count()).isZero();

        mvc.perform(post("/api/v1/auth/senha/esqueci").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@chirpstack.com.br\"}")).andExpect(status().isNoContent());
        assertThat(tokens.count()).isEqualTo(1);

        mvc.perform(post("/api/v1/auth/senha/redefinir").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"token-errado\",\"novaSenha\":\"NovaSenha2026\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
