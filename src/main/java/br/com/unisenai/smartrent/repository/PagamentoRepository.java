package br.com.unisenai.smartrent.repository;

import br.com.unisenai.smartrent.model.Pagamento;
import br.com.unisenai.smartrent.model.enums.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    /** Reservas dos imoveis do gestor com cobranca aprovada (base da receita bruta). */
    @Query("SELECT p.reserva.id FROM Pagamento p WHERE p.status = br.com.unisenai.smartrent.model.enums.StatusPagamento.APROVADO "
         + "AND p.reserva.imovel.usuario.id = :gestorId")
    List<Long> reservaIdsPagosDoGestor(@Param("gestorId") Long gestorId);

    long countByReservaId(Long reservaId);

    Optional<Pagamento> findByChaveIdempotencia(String chave);

    Optional<Pagamento> findFirstByReservaIdAndStatus(Long reservaId, StatusPagamento status);
}
