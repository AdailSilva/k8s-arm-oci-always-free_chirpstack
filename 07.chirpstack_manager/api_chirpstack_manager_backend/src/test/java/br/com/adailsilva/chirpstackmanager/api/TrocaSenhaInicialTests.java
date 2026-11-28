package br.com.adailsilva.chirpstackmanager.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * Credenciais iniciais padrão (admin@chirpstack.com.br / chirpstack) e troca obrigatória da senha
 * no primeiro acesso.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:troca-senha;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "app.chirpstack.api-token=token-de-teste",
        "app.security.jwt-secret=segredo-de-teste-com-mais-de-32-caracteres-123",
        "app.security.bootstrap-admin-email=",
        "app.security.bootstrap-admin-password="
})
@AutoConfigureMockMvc
class TrocaSenhaInicialTests {

    private static final Pattern TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    MockMvc mvc;

    @Test
    void primeiroAcessoExigeTrocaDaSenha() throws Exception {
        String token = login("admin@chirpstack.com.br", "chirpstack", true);

        // Bloqueado até trocar a senha
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("TROCA_SENHA_OBRIGATORIA"));
        // Os próprios dados continuam acessíveis
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@chirpstack.com.br"));
        // A nova senha segue a regra de senha forte
        mvc.perform(post("/api/v1/auth/me/senha").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"chirpstack\",\"novaSenha\":\"fraca\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/auth/me/senha").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"chirpstack\",\"novaSenha\":\"NovaSenha2026\"}"))
                .andExpect(status().isNoContent());

        // Senha inicial não vale mais; a nova libera o sistema
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@chirpstack.com.br\",\"senha\":\"chirpstack\"}"))
                .andExpect(status().isUnauthorized());
        String novo = login("admin@chirpstack.com.br", "NovaSenha2026", false);
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + novo))
                .andExpect(status().isOk());
    }

    private String login(String email, String senha, boolean trocaEsperada) throws Exception {
        String corpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trocaSenhaObrigatoria").value(trocaEsperada))
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(corpo);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
