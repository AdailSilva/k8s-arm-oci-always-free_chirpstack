package br.com.adailsilva.chirpstackmanager.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.adailsilva.chirpstackmanager.api.domain.entity.CenaItem;

public interface CenaItemRepository extends JpaRepository<CenaItem, Long> {

    List<CenaItem> findByCenaIdOrderByOrdemAsc(Long cenaId);

    @Modifying
    @Query("DELETE FROM CenaItem i WHERE i.cenaId = :cenaId")
    void apagarDaCena(@Param("cenaId") Long cenaId);
}
