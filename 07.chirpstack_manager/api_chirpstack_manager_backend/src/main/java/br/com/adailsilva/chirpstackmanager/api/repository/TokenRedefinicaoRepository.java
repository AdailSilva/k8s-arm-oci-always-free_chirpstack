package br.com.adailsilva.chirpstackmanager.api.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.TokenRedefinicao;
import br.com.adailsilva.chirpstackmanager.api.domain.enums.TipoToken;

public interface TokenRedefinicaoRepository extends JpaRepository<TokenRedefinicao, Long> {

    Optional<TokenRedefinicao> findByTokenHash(String tokenHash);

    boolean existsByUsuarioIdAndCriadoEmAfter(Long usuarioId, Instant desde);

    /** Invalida os pedidos anteriores do usuário (só o último link vale). */
    @Modifying
    @Query("UPDATE TokenRedefinicao t SET t.usadoEm = :agora WHERE t.usuarioId = :usuarioId AND t.usadoEm IS NULL")
    int invalidarDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    boolean existsByUsuarioIdAndTipoAndCriadoEmAfter(Long usuarioId, TipoToken tipo, Instant desde);

    /** Invalida os links anteriores do usuário com a mesma finalidade (só o último vale). */
    @Modifying
    @Query("UPDATE TokenRedefinicao t SET t.usadoEm = :agora WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usadoEm IS NULL")
    int invalidarDoTipo(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo, @Param("agora") Instant agora);

    @Modifying
    @Query("DELETE FROM TokenRedefinicao t WHERE t.expiraEm < :limite")
    int apagarExpirados(@Param("limite") Instant limite);
}
