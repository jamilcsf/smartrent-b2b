package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Midias do anuncio: upload, remocao, ordem e capa, com os limites do produto.
 *
 * <p>O limite de 14 e de imagens <em>no total</em>: foto comum e foto 360
 * dividem a mesma cota. Videos (ate 2, de ate 2 minutos) nao entram nela. A
 * contagem e feita no servidor, sobre o conjunto efetivo, com o imovel
 * travado para que dois uploads simultaneos nao estourem o limite.
 *
 * <p>Fora de edicao as alteracoes valem na hora (pre-publicacao). Em
 * {@code EM_EDICAO} elas ficam "pendentes": midia nova nasce NOVA, remover
 * marca REMOVIDA, e ordem/capa vao para o rascunho. Descartar a edicao
 * desfaz tudo; confirmar efetiva.
 */
@Service
public class MidiaService {

    public static final int MAX_IMAGENS = 14;
    public static final int MAX_VIDEOS = 2;
    public static final int MAX_DURACAO_VIDEO_SEGUNDOS = 120;

    private static final Logger log = LoggerFactory.getLogger(MidiaService.class);

    private final ImovelAcesso acesso;
    private final ImovelMidiaRepository midiaRepository;
    private final AnuncioRascunhoRepository rascunhoRepository;
    private final MidiaStorage storage;
    private final MidiaProcessador processador;
    private final Clock clock;

    public MidiaService(ImovelAcesso acesso,
                        ImovelMidiaRepository midiaRepository,
                        AnuncioRascunhoRepository rascunhoRepository,
                        MidiaStorage storage,
                        MidiaProcessador processador,
                        Clock clock) {
        this.acesso = acesso;
        this.midiaRepository = midiaRepository;
        this.rascunhoRepository = rascunhoRepository;
        this.storage = storage;
        this.processador = processador;
        this.clock = clock;
    }

    // ------------------------------------------------------------ upload

    @Transactional
    public MidiaResponse adicionar(Usuario gestor, Long imovelId, MultipartFile arquivo, TipoMidia tipo) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = exigirEditavel(imovel);

        if (arquivo == null || arquivo.isEmpty()) {
            throw new ValidacaoAnuncioException("Nenhum arquivo foi enviado.");
        }
        if (tipo == null) {
            throw new ValidacaoAnuncioException("Informe o tipo da mídia (FOTO, FOTO_360 ou VIDEO).");
        }

        List<ImovelMidia> efetivas = efetivas(imovelId);
        verificarLimite(efetivas, tipo);

        Path temporario = null;
        Path miniaturaTmp = null;
        String nomeArquivo = null;
        String nomeMiniatura = null;
        try {
            temporario = Files.createTempFile("smartrent-midia-", ".tmp");
            arquivo.transferTo(temporario);

            MidiaProcessador.Inspecao insp = tipo.imagem()
                    ? processador.inspecionarImagem(temporario, arquivo.getContentType(), tipo == TipoMidia.FOTO_360)
                    : processador.inspecionarVideo(temporario, arquivo.getContentType(), MAX_DURACAO_VIDEO_SEGUNDOS);

            String chave = UUID.randomUUID().toString();
            nomeArquivo = chave + "." + insp.extensao();
            storage.salvar(nomeArquivo, temporario);

            if (tipo.imagem()) {
                miniaturaTmp = Files.createTempFile("smartrent-mini-", ".jpg");
                if (processador.gerarMiniatura(temporario, miniaturaTmp)) {
                    nomeMiniatura = chave + "-mini.jpg";
                    storage.salvar(nomeMiniatura, miniaturaTmp);
                }
            }

            ImovelMidia m = new ImovelMidia();
            m.setImovel(imovel);
            m.setChave(chave);
            m.setTipo(tipo);
            m.setEstado(emEdicao ? EstadoMidia.NOVA : EstadoMidia.ATIVA);
            m.setArquivo(nomeArquivo);
            m.setMiniatura(nomeMiniatura);
            m.setMime(insp.mime());
            m.setTamanhoBytes(Files.size(temporario));
            m.setLargura(insp.largura());
            m.setAltura(insp.altura());
            m.setDuracaoSegundos(insp.duracaoSegundos());
            m.setOrdem(efetivas.stream().mapToInt(ImovelMidia::getOrdem).max().orElse(-1) + 1);
            // Fora de edicao a primeira imagem vira capa sozinha; em edicao a capa vive no rascunho.
            m.setCapa(!emEdicao && tipo.imagem() && efetivas.stream().noneMatch(ImovelMidia::isCapa));
            m.setDataEnvio(Agora.de(clock));
            ImovelMidia salva = midiaRepository.saveAndFlush(m);
            return MidiaResponse.de(salva);
        } catch (IOException e) {
            limpar(nomeArquivo, nomeMiniatura);
            throw new ValidacaoAnuncioException("Não foi possível processar o arquivo enviado.");
        } catch (RuntimeException e) {
            limpar(nomeArquivo, nomeMiniatura);
            throw e;
        } finally {
            apagarTemporario(temporario);
            apagarTemporario(miniaturaTmp);
        }
    }

    /** A regra dos 14: imagens comuns e 360 somadas; videos a parte. */
    private static void verificarLimite(List<ImovelMidia> efetivas, TipoMidia novo) {
        long imagens = efetivas.stream().filter(m -> m.getTipo().imagem()).count();
        long videos = efetivas.stream().filter(m -> m.getTipo() == TipoMidia.VIDEO).count();
        if (novo.imagem() && imagens >= MAX_IMAGENS) {
            throw new ValidacaoAnuncioException("Limite de " + MAX_IMAGENS
                    + " imagens atingido (fotos comuns e 360 somadas).");
        }
        if (novo == TipoMidia.VIDEO && videos >= MAX_VIDEOS) {
            throw new ValidacaoAnuncioException("Limite de " + MAX_VIDEOS + " vídeos atingido.");
        }
    }

    // ------------------------------------------------------------ remocao

    @Transactional
    public void remover(Usuario gestor, Long imovelId, Long midiaId) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = exigirEditavel(imovel);
        ImovelMidia m = daqui(imovelId, midiaId);

        if (emEdicao) {
            if (m.getEstado() == EstadoMidia.ATIVA) {
                m.setEstado(EstadoMidia.REMOVIDA); // continua no anuncio vivo ate a confirmacao
                midiaRepository.save(m);
            } else if (m.getEstado() == EstadoMidia.NOVA) {
                excluir(m);
            }
            rascunhoRepository.findByImovelId(imovelId).ifPresent(r -> {
                if (midiaId.equals(r.getCapaMidiaId())) {
                    r.setCapaMidiaId(null);
                    rascunhoRepository.save(r);
                }
            });
            return;
        }

        boolean eraCapa = m.isCapa();
        excluir(m);
        if (eraCapa) {
            efetivas(imovelId).stream().filter(x -> x.getTipo().imagem()).findFirst().ifPresent(x -> {
                x.setCapa(true);
                midiaRepository.save(x);
            });
        }
    }

    // --------------------------------------------------- ordem e capa

    @Transactional
    public void ordenar(Usuario gestor, Long imovelId, List<Long> idsNaOrdem) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = exigirEditavel(imovel);
        List<ImovelMidia> efetivas = efetivas(imovelId);

        Set<Long> esperados = efetivas.stream().map(ImovelMidia::getId).collect(Collectors.toSet());
        if (idsNaOrdem == null || idsNaOrdem.size() != esperados.size()
                || !esperados.equals(new HashSet<>(idsNaOrdem))) {
            throw new ValidacaoAnuncioException("A nova ordem deve conter exatamente as mídias do anúncio.");
        }

        if (emEdicao) {
            AnuncioRascunho r = rascunhoDe(imovelId);
            r.setMidiaOrdem(idsNaOrdem.stream().map(String::valueOf).collect(Collectors.joining(",")));
            r.setAtualizadoEm(Agora.de(clock));
            rascunhoRepository.save(r);
            return;
        }
        for (int i = 0; i < idsNaOrdem.size(); i++) {
            int posicao = i;
            Long id = idsNaOrdem.get(i);
            efetivas.stream().filter(m -> m.getId().equals(id)).findFirst().ifPresent(m -> m.setOrdem(posicao));
        }
        midiaRepository.saveAll(efetivas);
    }

    @Transactional
    public void definirCapa(Usuario gestor, Long imovelId, Long midiaId) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = exigirEditavel(imovel);
        ImovelMidia alvo = efetivas(imovelId).stream()
                .filter(m -> m.getId().equals(midiaId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mídia não encontrada."));
        if (!alvo.getTipo().imagem()) {
            throw new ValidacaoAnuncioException("A capa deve ser uma imagem.");
        }

        if (emEdicao) {
            AnuncioRascunho r = rascunhoDe(imovelId);
            r.setCapaMidiaId(midiaId);
            r.setAtualizadoEm(Agora.de(clock));
            rascunhoRepository.save(r);
            return;
        }
        List<ImovelMidia> todas = efetivas(imovelId);
        todas.forEach(m -> m.setCapa(m.getId().equals(midiaId)));
        midiaRepository.saveAll(todas);
    }

    /**
     * Marca uma imagem como comum ou 360. Imagem para 360 precisa ter proporcao
     * 2:1. Durante uma edicao, so midia NOVA pode mudar de tipo: mudar uma
     * midia do anuncio vivo tornaria o descarte inexato.
     */
    @Transactional
    public MidiaResponse alterarTipo(Usuario gestor, Long imovelId, Long midiaId, TipoMidia novoTipo) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = exigirEditavel(imovel);
        ImovelMidia m = daqui(imovelId, midiaId);
        if (novoTipo == null || !novoTipo.imagem() || !m.getTipo().imagem()) {
            throw new ValidacaoAnuncioException("Só é possível alternar uma imagem entre foto comum e foto 360.");
        }
        if (emEdicao && m.getEstado() == EstadoMidia.ATIVA) {
            throw new TransicaoInvalidaException(
                    "Esta imagem já faz parte do anúncio publicado. Remova-a e envie novamente com o tipo desejado.");
        }
        if (novoTipo == TipoMidia.FOTO_360 && !MidiaProcessador.proporcao360(m.getLargura(), m.getAltura())) {
            throw new ValidacaoAnuncioException("Foto 360 deve ter proporção 2:1 (imagem equirretangular). Esta tem "
                    + m.getLargura() + "x" + m.getAltura() + ".");
        }
        m.setTipo(novoTipo);
        return MidiaResponse.de(midiaRepository.save(m));
    }

    // ------------------------------------------------ edicao: efetivar/desfazer

    /** Confirmacao da edicao: apaga as removidas, ativa as novas, aplica ordem e capa do rascunho. */
    @Transactional
    public void aplicarRascunho(Imovel imovel, AnuncioRascunho rascunho) {
        List<ImovelMidia> todas = midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(imovel.getId());
        List<ImovelMidia> removidas = todas.stream().filter(m -> m.getEstado() == EstadoMidia.REMOVIDA).toList();
        removidas.forEach(this::excluir);

        List<ImovelMidia> restantes = ordenadas(
                todas.stream().filter(m -> m.getEstado() != EstadoMidia.REMOVIDA).toList(), rascunho);
        Long capaId = capaEfetivaId(restantes, rascunho);
        for (int i = 0; i < restantes.size(); i++) {
            ImovelMidia m = restantes.get(i);
            m.setEstado(EstadoMidia.ATIVA);
            m.setOrdem(i);
            m.setCapa(m.getId().equals(capaId));
        }
        midiaRepository.saveAll(restantes);
    }

    /** Descarte da edicao: some o que era novo e volta o que fora removido; ordem/capa nunca mudaram. */
    @Transactional
    public void desfazerRascunho(Imovel imovel) {
        List<ImovelMidia> todas = midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(imovel.getId());
        for (ImovelMidia m : todas) {
            if (m.getEstado() == EstadoMidia.NOVA) {
                excluir(m);
            } else if (m.getEstado() == EstadoMidia.REMOVIDA) {
                m.setEstado(EstadoMidia.ATIVA);
                midiaRepository.save(m);
            }
        }
    }

    // ----------------------------------------------------------- leitura

    /** Midias como o gestor as ve: efetivas ordenadas, com removidas marcadas ao final. */
    @Transactional(readOnly = true)
    public List<MidiaResponse> respostasParaGestor(Imovel imovel, AnuncioRascunho rascunho) {
        List<ImovelMidia> todas = midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(imovel.getId());
        List<ImovelMidia> efetivas = todas.stream().filter(m -> m.getEstado() != EstadoMidia.REMOVIDA).toList();
        List<ImovelMidia> ordenadas = imovel.getStatus() == StatusAnuncio.EM_EDICAO
                ? ordenadas(efetivas, rascunho)
                : efetivas;
        Long capaId = imovel.getStatus() == StatusAnuncio.EM_EDICAO
                ? capaEfetivaId(ordenadas, rascunho)
                : capaEfetivaId(ordenadas, null);

        List<MidiaResponse> saida = new ArrayList<>();
        for (int i = 0; i < ordenadas.size(); i++) {
            ImovelMidia m = ordenadas.get(i);
            saida.add(MidiaResponse.de(m, i, m.getId().equals(capaId)));
        }
        todas.stream().filter(m -> m.getEstado() == EstadoMidia.REMOVIDA)
                .forEach(m -> saida.add(MidiaResponse.de(m, saida.size(), false)));
        return saida;
    }

    // ------------------------------------------------------------ apoio

    /** Midias que contam para os limites: tudo que nao esta marcado como removido. */
    List<ImovelMidia> efetivas(Long imovelId) {
        return midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(imovelId).stream()
                .filter(m -> m.getEstado() != EstadoMidia.REMOVIDA)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static List<ImovelMidia> ordenadas(List<ImovelMidia> midias, AnuncioRascunho rascunho) {
        if (rascunho == null || rascunho.getMidiaOrdem() == null || rascunho.getMidiaOrdem().isBlank()) {
            return midias;
        }
        List<Long> ordem = new ArrayList<>();
        for (String parte : rascunho.getMidiaOrdem().split(",")) {
            try {
                ordem.add(Long.parseLong(parte.trim()));
            } catch (NumberFormatException ignorado) {
                // id corrompido no rascunho: ignora, a midia cai no fim da fila
            }
        }
        return midias.stream()
                .sorted(Comparator.comparingInt((ImovelMidia m) -> {
                    int i = ordem.indexOf(m.getId());
                    return i < 0 ? Integer.MAX_VALUE : i;
                }).thenComparingInt(ImovelMidia::getOrdem).thenComparing(ImovelMidia::getId))
                .toList();
    }

    static Long capaEfetivaId(List<ImovelMidia> efetivas, AnuncioRascunho rascunho) {
        Set<Long> ids = efetivas.stream().map(ImovelMidia::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (rascunho != null && rascunho.getCapaMidiaId() != null && ids.contains(rascunho.getCapaMidiaId())) {
            return rascunho.getCapaMidiaId();
        }
        return efetivas.stream().filter(ImovelMidia::isCapa).map(ImovelMidia::getId).findFirst()
                .or(() -> efetivas.stream().filter(m -> m.getTipo().imagem()).map(ImovelMidia::getId).findFirst())
                .orElse(null);
    }

    /** Fora de pre-publicacao e de edicao nao se mexe em midia: e preciso iniciar a edicao. */
    private static boolean exigirEditavel(Imovel imovel) {
        if (imovel.getStatus() == StatusAnuncio.EM_EDICAO) {
            return true;
        }
        if (imovel.getStatus().prePublicacao()) {
            return false;
        }
        throw new TransicaoInvalidaException(
                "Este anúncio está publicado. Inicie a edição do anúncio para alterar as mídias.");
    }

    private ImovelMidia daqui(Long imovelId, Long midiaId) {
        return midiaRepository.findById(midiaId)
                .filter(m -> m.getImovel().getId().equals(imovelId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mídia não encontrada."));
    }

    private AnuncioRascunho rascunhoDe(Long imovelId) {
        return rascunhoRepository.findByImovelId(imovelId)
                .orElseThrow(() -> new TransicaoInvalidaException("Não há edição em andamento para este anúncio."));
    }

    private void excluir(ImovelMidia m) {
        storage.remover(m.getArquivo());
        if (m.getMiniatura() != null) {
            storage.remover(m.getMiniatura());
        }
        midiaRepository.delete(m);
    }

    private void limpar(String arquivo, String miniatura) {
        if (arquivo != null) {
            storage.remover(arquivo);
        }
        if (miniatura != null) {
            storage.remover(miniatura);
        }
    }

    private static void apagarTemporario(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("Arquivo temporario nao removido: {}", p);
        }
    }
}
