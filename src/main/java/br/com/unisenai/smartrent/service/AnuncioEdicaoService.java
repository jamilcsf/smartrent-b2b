package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.AnuncioGestorResponse;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.ContextoAceite;
import br.com.unisenai.smartrent.model.enums.OrigemPreco;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Edicao de anuncio ja publicado: iniciar, salvar rascunho, confirmar e descartar.
 *
 * <p>Principios que este servico garante:
 * <ul>
 *   <li>O anuncio sai do catalogo ao <em>iniciar</em> e so volta por duas vias:
 *       descarte (na hora, nada mudou) ou republicacao automatica 2h depois de
 *       <em>confirmar</em>. O tempo gasto editando nao conta.</li>
 *   <li>Nao existe prazo maximo de edicao: nenhum codigo aqui nem em job
 *       expira, republica ou descarta uma edicao sozinho.</li>
 *   <li>O rascunho nunca toca o anuncio vivo; so a confirmacao o aplica.</li>
 *   <li>O descarte nunca aplica a suspensao de 2h e nunca e possivel depois de
 *       confirmar.</li>
 * </ul>
 */
@Service
public class AnuncioEdicaoService {

    private final ImovelRepository imovelRepository;
    private final AnuncioRascunhoRepository rascunhoRepository;
    private final ImovelAcesso acesso;
    private final AuditoriaService auditoria;
    private final AnuncioGestorMapper mapper;
    private final MidiaService midiaService;
    private final AnuncioProperties props;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AnuncioEdicaoService(ImovelRepository imovelRepository,
                                AnuncioRascunhoRepository rascunhoRepository,
                                ImovelAcesso acesso,
                                AuditoriaService auditoria,
                                AnuncioGestorMapper mapper,
                                MidiaService midiaService,
                                AnuncioProperties props,
                                Clock clock,
                                ObjectMapper objectMapper,
                                Validator validator) {
        this.imovelRepository = imovelRepository;
        this.rascunhoRepository = rascunhoRepository;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.mapper = mapper;
        this.midiaService = midiaService;
        this.props = props;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    // ---------------------------------------------------------------- iniciar

    /**
     * Tira o anuncio do ar e abre o rascunho. {@code confirmado} e o "sim" do
     * modal de aviso: sem ele nada muda e o anuncio continua no ar.
     */
    @Transactional
    public AnuncioGestorResponse iniciar(Usuario gestor, Long id, Boolean confirmado) {
        if (!Boolean.TRUE.equals(confirmado)) {
            throw new ValidacaoAnuncioException(
                    "Confirme que o anúncio sairá do ar durante a edição para continuar.");
        }
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        LocalDateTime agora = Agora.de(clock);
        republicarSeVencido(imovel, agora);

        StatusAnuncio origem = imovel.getStatus();
        if (origem == StatusAnuncio.EM_EDICAO) {
            throw new TransicaoInvalidaException("Este anúncio já está em edição.");
        }
        if (origem.prePublicacao()) {
            throw new TransicaoInvalidaException(
                    "Anúncios em pré-publicação são editados diretamente, sem sair do ar.");
        }

        // Guarda de onde veio para o descarte poder restaurar. Se a origem era
        // uma republicacao agendada, ela e suspensa: o ciclo de 2h recomeca so
        // no proximo "Confirmar alteracao".
        imovel.setEdicaoEstadoOrigem(origem);
        imovel.setEdicaoIniciadaEm(agora);
        imovel.setRepublicarOriginalEm(origem == StatusAnuncio.REPUBLICACAO_AGENDADA ? imovel.getRepublicarEm() : null);
        imovel.setRepublicarEm(null);
        imovel.setEdicaoConfirmadaEm(null);
        MaquinaDeEstados.mover(imovel, StatusAnuncio.EM_EDICAO);
        imovelRepository.save(imovel);

        AnuncioRascunho r = new AnuncioRascunho();
        r.setImovel(imovel);
        r.setDados(serializar(AnuncioCampos.extrair(imovel)));
        r.setCriadoEm(agora);
        r.setAtualizadoEm(agora);
        rascunhoRepository.save(r);

        auditoria.acao(imovel, gestor.getId(), "EDICAO_INICIADA",
                "Anúncio retirado do catálogo para edição (origem: " + origem + ").");
        return mapper.montar(imovel);
    }

    // --------------------------------------------------------------- rascunho

    /** Salva o progresso. Nao toca o anuncio vivo; so o rascunho. */
    @Transactional
    public AnuncioGestorResponse salvarRascunho(Usuario gestor, Long id, AnuncioDados dados) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        exigirEmEdicao(imovel);
        AnuncioRascunho r = rascunhoRepository.findByImovelId(id)
                .orElseThrow(() -> new TransicaoInvalidaException("Não há rascunho para este anúncio."));
        if (dados == null) {
            throw new ValidacaoAnuncioException("Informe os dados do anúncio.");
        }
        if (dados.valorDiaria() != null) {
            AnuncioService.validarPreco(dados.valorDiaria());
        }
        r.setDados(serializar(AnuncioCampos.sanitizar(dados)));
        r.setAtualizadoEm(Agora.de(clock));
        rascunhoRepository.save(r);
        return mapper.montar(imovel);
    }

    // -------------------------------------------------------------- confirmar

    /**
     * Aplica o rascunho e agenda a republicacao para daqui a 2h (configuravel).
     * So aqui o relogio comeca, e o valor e fixo: {@code republicar_em} nao e
     * recalculado por nada.
     */
    @Transactional
    public AnuncioGestorResponse confirmar(Usuario gestor, Long id, Boolean aceiteTermo, String ip) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        exigirEmEdicao(imovel);
        AuditoriaService.exigirAceite(aceiteTermo);
        midiaService.exigirVideosProntos(id); // video enviando, processando ou com falha impede confirmar

        AnuncioRascunho r = rascunhoRepository.findByImovelId(id)
                .orElseThrow(() -> new TransicaoInvalidaException("Não há rascunho para este anúncio."));
        AnuncioDados dados = lerDados(r);
        validar(dados);

        BigDecimal precoAnterior = imovel.getValorDiariaBase();
        BigDecimal precoNovo = dados.valorDiaria() == null ? precoAnterior : AnuncioService.validarPreco(dados.valorDiaria());

        LocalDateTime agora = Agora.de(clock);
        var comerciaisAntes = AuditoriaService.Comerciais.de(imovel);
        AnuncioCampos.aplicar(imovel, dados);
        imovel.setValorDiariaBase(precoNovo);
        midiaService.aplicarRascunho(imovel, r);

        imovel.setEdicaoConfirmadaEm(agora);
        imovel.setRepublicarEm(Agora.somar(agora, Duration.ofHours(props.republicacaoHoras()), clock));
        imovel.setEdicaoIniciadaEm(null);
        imovel.setEdicaoEstadoOrigem(null);
        imovel.setRepublicarOriginalEm(null);
        MaquinaDeEstados.mover(imovel, StatusAnuncio.REPUBLICACAO_AGENDADA);
        imovelRepository.save(imovel);
        rascunhoRepository.delete(r);

        if (precoNovo != null && (precoAnterior == null || precoAnterior.compareTo(precoNovo) != 0)) {
            auditoria.preco(imovel, precoAnterior, precoNovo, gestor, OrigemPreco.MANUAL);
        }
        auditoria.camposComerciais(imovel, gestor.getId(), comerciaisAntes, AuditoriaService.Comerciais.de(imovel));
        auditoria.aceite(gestor, imovel, ContextoAceite.CONFIRMACAO_EDICAO, ip);
        auditoria.acao(imovel, gestor.getId(), "EDICAO_CONFIRMADA",
                "Alterações aplicadas; republicação agendada para " + imovel.getRepublicarEm() + ".");
        return mapper.montar(imovel);
    }

    // --------------------------------------------------------------- descartar

    /**
     * Abandona a edicao: apaga o rascunho e devolve o anuncio, intacto e
     * <em>na hora</em>, ao estado de origem. Sem suspensao de 2h e sem novo
     * aceite (nada foi alterado). So possivel enquanto em EM_EDICAO.
     */
    @Transactional
    public AnuncioGestorResponse descartar(Usuario gestor, Long id, Boolean confirmado) {
        if (!Boolean.TRUE.equals(confirmado)) {
            throw new ValidacaoAnuncioException(
                    "Confirme o descarte: as alterações não salvas serão perdidas.");
        }
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, id);
        if (imovel.getStatus() == StatusAnuncio.REPUBLICACAO_AGENDADA) {
            throw new TransicaoInvalidaException(
                    "As alterações já foram confirmadas e não podem mais ser descartadas. "
                            + "Para mudar de novo, inicie uma nova edição.");
        }
        exigirEmEdicao(imovel);

        LocalDateTime agora = Agora.de(clock);
        StatusAnuncio origem = imovel.getEdicaoEstadoOrigem();
        LocalDateTime republicarOriginal = imovel.getRepublicarOriginalEm();
        boolean restauraAgendamento = origem == StatusAnuncio.REPUBLICACAO_AGENDADA
                && republicarOriginal != null && republicarOriginal.isAfter(agora);

        midiaService.desfazerRascunho(imovel);
        rascunhoRepository.deleteByImovelId(id);

        if (restauraAgendamento) {
            imovel.setRepublicarEm(republicarOriginal);
            MaquinaDeEstados.mover(imovel, StatusAnuncio.REPUBLICACAO_AGENDADA);
        } else {
            // Origem PUBLICADO, ou agendamento cujo horario ja passou: volta ao ar agora.
            imovel.setRepublicarEm(null);
            MaquinaDeEstados.mover(imovel, StatusAnuncio.PUBLICADO);
        }
        imovel.setEdicaoIniciadaEm(null);
        imovel.setEdicaoEstadoOrigem(null);
        imovel.setRepublicarOriginalEm(null);
        imovelRepository.save(imovel);

        auditoria.acao(imovel, gestor.getId(), "EDICAO_DESCARTADA",
                "Edição descartada; anúncio restaurado como " + imovel.getStatus() + ".");
        return mapper.montar(imovel);
    }

    // ------------------------------------------------------------------ apoio

    private static void exigirEmEdicao(Imovel imovel) {
        if (imovel.getStatus() != StatusAnuncio.EM_EDICAO) {
            throw new TransicaoInvalidaException(
                    "Não há edição em andamento para este anúncio (situação atual: " + imovel.getStatus() + ").");
        }
    }

    /** Uma republicacao cujo horario ja chegou conta como publicada, mesmo que o job ainda nao tenha rodado. */
    private void republicarSeVencido(Imovel imovel, LocalDateTime agora) {
        if (imovel.getStatus() == StatusAnuncio.REPUBLICACAO_AGENDADA
                && imovel.getRepublicarEm() != null && !imovel.getRepublicarEm().isAfter(agora)) {
            MaquinaDeEstados.mover(imovel, StatusAnuncio.PUBLICADO);
            imovel.setRepublicarEm(null);
        }
    }

    private void validar(AnuncioDados dados) {
        Set<ConstraintViolation<AnuncioDados>> violacoes = validator.validate(dados);
        if (!violacoes.isEmpty()) {
            Map<String, String> campos = new LinkedHashMap<>();
            violacoes.forEach(v -> campos.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
            throw new ValidacaoAnuncioException(
                    "Há campos inválidos no rascunho. Corrija-os para confirmar a alteração.", campos);
        }
    }

    private String serializar(AnuncioDados dados) {
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o rascunho.", e);
        }
    }

    private AnuncioDados lerDados(AnuncioRascunho r) {
        try {
            return objectMapper.readValue(r.getDados(), AnuncioDados.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Rascunho corrompido.", e);
        }
    }
}
