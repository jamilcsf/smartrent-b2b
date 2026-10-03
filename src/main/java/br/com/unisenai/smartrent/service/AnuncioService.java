package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.AnuncioGestorResponse;
import br.com.unisenai.smartrent.dto.CriarAnuncioRequest;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ciclo de vida do anuncio ate a publicacao: cadastro, edicao direta em
 * pre-publicacao, definicao de preco, janela de 24h e publicacao.
 *
 * <p>A edicao de anuncio ja publicado vive em {@link AnuncioEdicaoService}.
 *
 * <p>Toda decisao que depende de "agora" usa o {@link Clock} injetado, e o
 * servidor e quem decide: nem a tela nem um job puramente visual liberam a
 * publicacao antes da hora.
 */
@Service
public class AnuncioService {

    private static final BigDecimal PRECO_MINIMO = new BigDecimal("1.00");
    private static final BigDecimal PRECO_MAXIMO = new BigDecimal("100000.00");

    private final ImovelRepository imovelRepository;
    private final ImovelAcesso acesso;
    private final AuditoriaService auditoria;
    private final AnuncioGestorMapper mapper;
    private final AnuncioProperties props;
    private final Clock clock;

    public AnuncioService(ImovelRepository imovelRepository,
                          ImovelAcesso acesso,
                          AuditoriaService auditoria,
                          AnuncioGestorMapper mapper,
                          AnuncioProperties props,
                          Clock clock) {
        this.imovelRepository = imovelRepository;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------ cadastro

    @Transactional
    public AnuncioGestorResponse criar(Usuario gestor, CriarAnuncioRequest req, String ip) {
        acesso.exigirGestor(gestor);
        AuditoriaService.exigirAceite(req.aceiteTermo());

        Imovel imovel = new Imovel();
        imovel.setUsuario(gestor);
        AnuncioCampos.aplicar(imovel, req.dados());
        imovel.setStatus(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        imovel.setValorDiariaBase(null);
        imovel.setAtivo(true);
        imovel = imovelRepository.save(imovel);

        auditoria.aceite(gestor, imovel, ContextoAceite.CADASTRO, ip);
        auditoria.acao(imovel, gestor.getId(), "CRIADO", "Anúncio cadastrado em pré-publicação.");
        return mapper.montar(imovel);
    }

    /** Edicao direta: so enquanto o anuncio ainda nao foi publicado. */
    @Transactional
    public AnuncioGestorResponse atualizar(Usuario gestor, Long id, AnuncioDados dados) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        if (!imovel.getStatus().prePublicacao()) {
            throw new TransicaoInvalidaException(
                    "Este anúncio já foi publicado. Para alterá-lo, inicie a edição do anúncio.");
        }
        AnuncioCampos.aplicar(imovel, dados);
        imovelRepository.save(imovel);
        auditoria.acao(imovel, gestor.getId(), "ATUALIZADO", "Cadastro alterado em pré-publicação.");
        return mapper.montar(imovel);
    }

    // -------------------------------------------------------------- leitura

    @Transactional
    public List<AnuncioGestorResponse> listarMeus(Usuario gestor) {
        acesso.exigirGestor(gestor);
        promoverVencidos(); // a lista nunca mostra estado atrasado em relacao ao relogio
        List<Imovel> imoveis = gestor.getPapel() == PapelUsuario.ADMIN
                ? imovelRepository.findAll()
                : imovelRepository.findByUsuarioIdOrderByIdDesc(gestor.getId());
        return imoveis.stream().map(mapper::montar).toList();
    }

    @Transactional
    public AnuncioGestorResponse obter(Usuario gestor, Long id) {
        Imovel imovel = acesso.doGestor(gestor, id);
        promoverSeVencido(imovel);
        return mapper.montar(imovel);
    }

    // ---------------------------------------------------------------- preco

    /**
     * Define ou altera o preco em pre-publicacao. A primeira confirmacao inicia
     * a janela de 24h e seu instante e gravado uma unica vez: alteracoes
     * seguintes (manuais ou por IA) mudam o valor, jamais o relogio.
     */
    @Transactional
    public AnuncioGestorResponse definirPreco(Usuario gestor, Long id, BigDecimal valor, OrigemPreco origem) {
        BigDecimal novo = validarPreco(valor);
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        promoverSeVencido(imovel);

        if (!imovel.getStatus().prePublicacao()) {
            throw new TransicaoInvalidaException(
                    "O preço de um anúncio publicado é alterado pela edição do anúncio.");
        }

        BigDecimal anterior = imovel.getValorDiariaBase();
        if (anterior != null && anterior.compareTo(novo) == 0) {
            return mapper.montar(imovel); // nada mudou: nem historico, nem relogio
        }

        if (imovel.getStatus() == StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO) {
            LocalDateTime agora = Agora.de(clock);
            if (imovel.getPrecoPrimeiraConfirmacaoEm() == null) {
                imovel.setPrecoPrimeiraConfirmacaoEm(agora);
            }
            MaquinaDeEstados.mover(imovel, StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO);
        }
        imovel.setValorDiariaBase(novo);
        imovelRepository.save(imovel);

        auditoria.preco(imovel, anterior, novo, gestor, origem == null ? OrigemPreco.MANUAL : origem);
        auditoria.acao(imovel, gestor.getId(), "PRECO_DEFINIDO",
                "Preço " + (anterior == null ? "definido" : "alterado") + " para R$ " + novo + ".");
        return mapper.montar(imovel);
    }

    static BigDecimal validarPreco(BigDecimal valor) {
        if (valor == null) {
            throw new ValidacaoAnuncioException("Informe o valor da diária.");
        }
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
        if (arredondado.compareTo(PRECO_MINIMO) < 0 || arredondado.compareTo(PRECO_MAXIMO) > 0) {
            throw new ValidacaoAnuncioException("O valor da diária deve estar entre R$ 1,00 e R$ 100.000,00.");
        }
        return arredondado;
    }

    // ------------------------------------------------------------ publicacao

    @Transactional
    public AnuncioGestorResponse publicar(Usuario gestor, Long id, Boolean aceiteTermo, String ip) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        promoverSeVencido(imovel);

        switch (imovel.getStatus()) {
            case PRONTO_PARA_PUBLICAR -> { /* segue */ }
            case PRE_PUBLICACAO_SEM_PRECO -> throw new TransicaoInvalidaException(
                    "Defina e confirme o preço da diária antes de publicar.");
            case PRE_PUBLICACAO_AGUARDANDO -> throw new TransicaoInvalidaException(
                    "A publicação só é liberada " + props.janelaPrePublicacaoHoras() + " horas após a primeira confirmação de preço. "
                            + "Libera em " + descrever(restanteDaJanela(imovel)) + ".");
            default -> throw new TransicaoInvalidaException(
                    "Este anúncio não está pronto para publicação (situação atual: " + imovel.getStatus() + ").");
        }
        AuditoriaService.exigirAceite(aceiteTermo);

        LocalDateTime agora = Agora.de(clock);
        MaquinaDeEstados.mover(imovel, StatusAnuncio.PUBLICADO);
        imovel.setPublicadoEm(agora);
        imovel.setAtivo(true);
        imovelRepository.save(imovel);

        auditoria.aceite(gestor, imovel, ContextoAceite.PUBLICACAO, ip);
        auditoria.acao(imovel, gestor.getId(), "PUBLICADO", "Anúncio publicado no catálogo.");
        return mapper.montar(imovel);
    }

    // ------------------------------------------------- relogio da pre-publicacao

    /**
     * Promove AGUARDANDO para PRONTO quando a janela, contada da primeira
     * confirmacao de preco, ja passou. A decisao e do servidor e e refeita a
     * cada leitura/escrita: nao depende de o job ter rodado.
     */
    boolean promoverSeVencido(Imovel imovel) {
        if (imovel.getStatus() == StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO
                && janelaCumprida(imovel, Agora.de(clock))) {
            MaquinaDeEstados.mover(imovel, StatusAnuncio.PRONTO_PARA_PUBLICAR);
            imovelRepository.save(imovel);
            return true;
        }
        return false;
    }

    private boolean janelaCumprida(Imovel imovel, LocalDateTime agora) {
        LocalDateTime inicio = imovel.getPrecoPrimeiraConfirmacaoEm();
        return inicio != null
                && !agora.isBefore(inicio.plus(Duration.ofHours(props.janelaPrePublicacaoHoras())));
    }

    private Duration restanteDaJanela(Imovel imovel) {
        LocalDateTime fim = imovel.getPrecoPrimeiraConfirmacaoEm()
                .plus(Duration.ofHours(props.janelaPrePublicacaoHoras()));
        return Duration.between(Agora.de(clock), fim);
    }

    private static String descrever(Duration d) {
        long minutos = Math.max(0, d.toMinutes());
        return (minutos / 60) + "h" + String.format("%02d", minutos % 60) + "min";
    }

    /**
     * Promocoes em lote por tempo. Idempotente: cada UPDATE so afeta linhas
     * ainda vencidas, entao rodar duas vezes (ou atrasado) nao muda o resultado.
     *
     * @return quantas linhas mudaram (republicados + prontos para publicar)
     */
    @Transactional
    public int promoverVencidos() {
        LocalDateTime agora = Agora.de(clock);
        int republicados = imovelRepository.promoverRepublicacoesVencidas(agora);
        int prontos = imovelRepository.promoverProntosParaPublicar(
                agora.minus(Duration.ofHours(props.janelaPrePublicacaoHoras())));
        return republicados + prontos;
    }
}
