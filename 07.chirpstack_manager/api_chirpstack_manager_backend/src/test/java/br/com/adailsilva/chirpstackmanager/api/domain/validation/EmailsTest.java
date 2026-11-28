package br.com.adailsilva.chirpstackmanager.api.domain.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailsTest {

    @Test
    void aceitaValidosEVazio() {
        assertTrue(Emails.valido("fulano@empresa.com.br"));
        assertTrue(Emails.valido("admin@chirpstack.com.br"));
        assertTrue(Emails.valido("nome.sobrenome+tag@sub.dominio.io"));
        assertTrue(Emails.valido(null));
        assertTrue(Emails.valido("  "));
    }

    @Test
    void recusaInvalidos() {
        assertFalse(Emails.valido("admin"));
        assertFalse(Emails.valido("sem-arroba.com"));
        assertFalse(Emails.valido("a@b@c.com"));
        assertFalse(Emails.valido("espaco no@meio.com"));
        assertFalse(Emails.valido("fulano@-dominio.com"));
        assertFalse(Emails.valido("x".repeat(150) + "@dominio.com"));
    }
}
