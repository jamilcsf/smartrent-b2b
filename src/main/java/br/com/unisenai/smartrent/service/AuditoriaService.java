package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.model.AceiteTermo;
import br.com.unisenai.smartrent.model.AuditoriaAnuncio;
import br.com.unisenai.smartrent.model.HistoricoPreco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import br.com.unisenai.smartrent.repository.AceiteTermoRepository;
import br.com.unisenai.smartrent.repository.AuditoriaAnuncioRepository;
import br.com.unisenai.smartrent.repository.HistoricoPrecoRepository;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Escrita das trilhas de auditoria: aceites do termo, acoes sobre o anuncio e
 * historico de precos. Sempre participa da transacao de quem chama, para que
 * a trilha e a mudanca se confirmem (ou desfacam) juntas.
 */
@Service
public class AuditoriaService {

    private final AceiteTermoRepository aceiteRepository;
    private final AuditoriaAnuncioRepository auditoriaRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final Clock clock;

    public AuditoriaService(AceiteTermoRepository aceiteRepository,
                            AuditoriaAnuncioRepository auditoriaRepository,
                            HistoricoPrecoRepository historicoPrecoRepository,
                            Clock clock) {
        this.aceiteRepository = aceiteRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.clock = clock;
    }

    /** O aceite e requisito: sem ele, nada acontece. */
    public static void exigirAceite(Boolean aceite) {
        if (!Boolean.TRUE.equals(aceite)) {
            throw new ValidacaoAnuncioException("É necessário aceitar o termo de uso para continuar.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void aceite(Usuario usuario, Imovel imovel, ContextoAceite contexto, String ip) {
        AceiteTermo a = new AceiteTermo();
        a.setUsuario(usuario);
        a.setImovel(imovel);
        a.setVersaoTermo(TermoUso.VERSAO);
        a.setContexto(contexto);
        a.setDataHora(Agora.de(clock));
        a.setIp(ip);
        aceiteRepository.save(a);
    }

    /** Foto dos campos comerciais do anuncio (minimo de diarias, taxa de limpeza, limite de hospedes). */
    public record Comerciais(int minimoDiarias, BigDecimal taxaLimpeza, Integer limiteHospedes) {
        public static Comerciais de(Imovel i) {
            return new Comerciais(i.getMinimoDiarias(), i.getTaxaLimpeza(), i.getCapacidadeHospedes());
        }
    }

    /** Registra a mudanca desses campos, como ja acontece com o preco. Sem mudanca, nao grava nada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void camposComerciais(Imovel imovel, Long usuarioId, Comerciais antes, Comerciais depois) {
        StringBuilder sb = new StringBuilder();
        if (antes.minimoDiarias() != depois.minimoDiarias()) {
            sb.append("mínimo de diárias ").append(antes.minimoDiarias()).append(" -> ").append(depois.minimoDiarias()).append("; ");
        }
        if (antes.taxaLimpeza() == null ? depois.taxaLimpeza() != null
                : depois.taxaLimpeza() == null || antes.taxaLimpeza().compareTo(depois.taxaLimpeza()) != 0) {
            sb.append("taxa de limpeza ").append(antes.taxaLimpeza()).append(" -> ").append(depois.taxaLimpeza()).append("; ");
        }
        if (!java.util.Objects.equals(antes.limiteHospedes(), depois.limiteHospedes())) {
            sb.append("limite de hóspedes ").append(antes.limiteHospedes()).append(" -> ").append(depois.limiteHospedes()).append("; ");
        }
        if (sb.length() > 0) {
            acao(imovel, usuarioId, "CAMPOS_COMERCIAIS_ALTERADOS", sb.toString().trim());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acao(Imovel imovel, Long usuarioId, String acao, String detalhes) {
        AuditoriaAnuncio a = new AuditoriaAnuncio();
        a.setImovel(imovel);
        a.setUsuarioId(usuarioId);
        a.setAcao(acao);
        a.setDetalhes(detalhes == null || detalhes.length() <= 500 ? detalhes : detalhes.substring(0, 500));
        a.setDataHora(Agora.de(clock));
        auditoriaRepository.save(a);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void preco(Imovel imovel, BigDecimal anterior, BigDecimal novo, Usuario autor, OrigemPreco origem) {
        HistoricoPreco h = new HistoricoPreco();
        h.setImovel(imovel);
        h.setValorAnterior(anterior);
        h.setValorNovo(novo);
        h.setAutor(autor);
        h.setOrigem(origem);
        h.setDataHora(Agora.de(clock));
        historicoPrecoRepository.save(h);
    }
}
