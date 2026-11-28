package br.com.adailsilva.chirpstackmanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A API roda sob o context-path {@code /chirpstack-manager} (application-*.properties). As regras do
 * {@code SessaoAtivaFilter} comparam o caminho dentro da aplicação ({@code /api/...}): estes testes
 * garantem que, com o prefixo, a troca obrigatória da senha e o encerramento da sessão continuam valendo.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-path;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.chirpstack.api-token=token-de-teste",
        "app.security.jwt-secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "app.security.bootstrap-admin-email=",
        "app.security.bootstrap-admin-password="
})
@AutoConfigureMockMvc
class ContextPathTests {

    private static final String CTX = "/chirpstack-manager";
    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    MockMvc mvc;

    @Test
    void regrasDaSessaoValemComOContextPath() throws Exception {
        // Senha inicial: com o prefixo, o filtro continua barrando tudo até a troca.
        String inicial = login("chirpstack", true);
        mvc.perform(comCtx(get(CTX + "/api/v1/usuarios")).header("Authorization", "Bearer " + inicial))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("TROCA_SENHA_OBRIGATORIA"));
        mvc.perform(comCtx(get(CTX + "/api/v1/auth/me")).header("Authorization", "Bearer " + inicial))
                .andExpect(status().isOk());
        mvc.perform(comCtx(post(CTX + "/api/v1/auth/me/senha")).header("Authorization", "Bearer " + inicial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"chirpstack\",\"novaSenha\":\"NovaSenha2026\"}"))
                .andExpect(status().isNoContent());

        // Sessão encerrada no logout: o token antigo passa a ser recusado (401 + X-Sessao-Status).
        String token = login("NovaSenha2026", false);
        mvc.perform(comCtx(get(CTX + "/api/v1/usuarios")).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(comCtx(post(CTX + "/api/v1/auth/logout")).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(comCtx(get(CTX + "/api/v1/usuarios")).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Sessao-Status", "ENCERRADA"));
    }

    private static MockHttpServletRequestBuilder comCtx(MockHttpServletRequestBuilder req) {
        return req.contextPath(CTX);
    }

    private String login(String senha, boolean trocaEsperada) throws Exception {
        String corpo = mvc.perform(comCtx(post(CTX + "/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@chirpstack.com.br\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(trocaEsperada))
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(corpo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
