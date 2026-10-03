package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.AnuncioProperties;
import br.com.unisenai.smartrent.dto.AnuncioDados;
import br.com.unisenai.smartrent.dto.AnuncioGestorResponse;
import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** Monta a visao do gestor sobre um anuncio. Chame dentro de uma transacao. */
@Component
public class AnuncioGestorMapper {

    private final AnuncioRascunhoRepository rascunhoRepository;
    private final MidiaService midiaService;
    private final AnuncioProperties props;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AnuncioGestorMapper(AnuncioRascunhoRepository rascunhoRepository,
                               MidiaService midiaService,
                               AnuncioProperties props,
                               ObjectMapper objectMapper,
                               Clock clock) {
        this.rascunhoRepository = rascunhoRepository;
        this.midiaService = midiaService;
        this.props = props;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AnuncioGestorResponse montar(Imovel i) {
        LocalDateTime agora = LocalDateTime.now(clock);
        AnuncioRascunho rascunho = i.getStatus() == StatusAnuncio.EM_EDICAO
                ? rascunhoRepository.findByImovelId(i.getId()).orElse(null)
                : null;
        List<MidiaResponse> midias = midiaService.respostasParaGestor(i, rascunho);

        long imagens = midias.stream()
                .filter(m -> m.tipo().imagem() && m.estado() != EstadoMidia.REMOVIDA).count();
        long videos = midias.stream()
                .filter(m -> !m.tipo().imagem() && m.estado() != EstadoMidia.REMOVIDA).count();

        LocalDateTime pronto = i.getPrecoPrimeiraConfirmacaoEm() == null ? null
                : i.getPrecoPrimeiraConfirmacaoEm().plus(Duration.ofHours(props.janelaPrePublicacaoHoras()));
        boolean podePublicar = pronto != null && !agora.isBefore(pronto)
                && (i.getStatus() == StatusAnuncio.PRE_PUBLICACAO_AGUARDANDO
                || i.getStatus() == StatusAnuncio.PRONTO_PARA_PUBLICAR);

        return new AnuncioGestorResponse(
                i.getId(),
                i.getStatus(),
                i.isAtivo(),
                noCatalogo(i, agora),
                AnuncioCampos.extrair(i),
                rascunho == null ? null : lerDados(rascunho),
                midias,
                (int) imagens,
                (int) videos,
                MidiaService.MAX_IMAGENS,
                MidiaService.MAX_VIDEOS,
                agora,
                i.getDataCadastro(),
                i.getPrecoPrimeiraConfirmacaoEm(),
                pronto,
                podePublicar,
                i.getPublicadoEm(),
                i.getEdicaoIniciadaEm(),
                i.getEdicaoEstadoOrigem(),
                i.getEdicaoConfirmadaEm(),
                i.getRepublicarEm());
    }

    /** Mesma regra de visibilidade da consulta publica ({@code ImovelRepository.VISIVEL}). */
    static boolean noCatalogo(Imovel i, LocalDateTime agora) {
        if (!i.isAtivo()) {
            return false;
        }
        return i.getStatus() == StatusAnuncio.PUBLICADO
                || (i.getStatus() == StatusAnuncio.REPUBLICACAO_AGENDADA
                && i.getRepublicarEm() != null && !i.getRepublicarEm().isAfter(agora));
    }

    private AnuncioDados lerDados(AnuncioRascunho r) {
        try {
            return objectMapper.readValue(r.getDados(), AnuncioDados.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Rascunho corrompido para o imóvel " + r.getImovel().getId(), e);
        }
    }
}
