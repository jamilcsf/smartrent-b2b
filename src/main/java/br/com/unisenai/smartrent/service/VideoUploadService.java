package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.VideoProperties;
import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.StatusVideo;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.service.erro.ConflitoComDetalhesException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Envio de video em partes, retomavel: {@code iniciar} -> varias {@code enviarParte}
 * (a cada parte, o cliente sabe quantos bytes o servidor ja tem e continua dali apos
 * uma queda de conexao) -> {@code concluir}, que valida no SERVIDOR (tipo real pelo
 * conteudo, tamanho, trilha de video, duracao e resolucao) e so entao enfileira o
 * processamento assincrono. O que o navegador declara (duracao, tipo) nunca e confiado.
 *
 * <p>Nota de arquitetura: o enunciado recomenda URL pre-assinada de um armazenamento de
 * objetos; com disco local isto nao se aplica, entao as partes passam pelo servidor mas
 * em requisicoes pequenas e retomaveis. Com S3, trocar por multipart pre-assinado
 * mantendo o mesmo contrato de estados.
 */
@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);
    private static final Set<String> MIMES = Set.of("video/mp4", "video/quicktime", "video/webm");

    private final ImovelAcesso acesso;
    private final ImovelMidiaRepository midiaRepository;
    private final MidiaService midiaService;
    private final MidiaStorage storage;
    private final VideoProcessingService processamento;
    private final VideoProperties props;
    private final Clock clock;

    public VideoUploadService(ImovelAcesso acesso, ImovelMidiaRepository midiaRepository, MidiaService midiaService,
                              MidiaStorage storage, VideoProcessingService processamento, VideoProperties props, Clock clock) {
        this.acesso = acesso;
        this.midiaRepository = midiaRepository;
        this.midiaService = midiaService;
        this.storage = storage;
        this.processamento = processamento;
        this.props = props;
        this.clock = clock;
    }

    public long tamanhoDaParte() {
        return props.parteMb() * 1024L * 1024L;
    }

    // ---------------------------------------------------------------- iniciar

    @Transactional
    public MidiaResponse iniciar(Usuario gestor, Long imovelId, long tamanho, String mime) {
        Imovel imovel = acesso.doGestorParaAtualizar(gestor, imovelId);
        boolean emEdicao = MidiaService.exigirEditavel(imovel);

        String tipo = mime == null ? "" : mime.trim().toLowerCase();
        if (!MIMES.contains(tipo)) {
            throw new ValidacaoAnuncioException("Formato de vídeo não aceito. Envie MP4, MOV ou WebM.");
        }
        if (tipo.equals("video/webm") && !processamento.aceitaWebm()) {
            throw new ValidacaoAnuncioException("WebM exige o processamento com FFmpeg, indisponível neste ambiente. Envie MP4 ou MOV.");
        }
        if (tamanho <= 0) {
            throw new ValidacaoAnuncioException("Informe o tamanho do arquivo de vídeo.");
        }
        long max = props.tamanhoMaxMb() * 1024L * 1024L;
        if (tamanho > max) {
            throw new ValidacaoAnuncioException("O vídeo excede o tamanho máximo de " + props.tamanhoMaxMb() + " MB.");
        }

        List<ImovelMidia> efetivas = midiaService.efetivas(imovelId);
        MidiaService.verificarLimite(efetivas, TipoMidia.VIDEO);

        String chave = UUID.randomUUID().toString();
        String original = "videos/" + chave + "/original." + extensao(tipo);
        Instant agora = clock.instant();

        ImovelMidia m = new ImovelMidia();
        m.setImovel(imovel);
        m.setChave(chave);
        m.setTipo(TipoMidia.VIDEO);
        m.setEstado(emEdicao ? EstadoMidia.NOVA : EstadoMidia.ATIVA);
        m.setArquivo(original);
        m.setOriginalArquivo(original);
        m.setMime(tipo.equals("video/quicktime") ? "video/quicktime" : tipo);
        m.setTamanhoBytes(tamanho);
        m.setTamanhoTotal(tamanho);
        m.setBytesRecebidos(0L);
        m.setStatusProcessamento(StatusVideo.ENVIANDO);
        m.setOrdem(efetivas.stream().mapToInt(ImovelMidia::getOrdem).max().orElse(-1) + 1);
        m.setCapa(false);
        m.setDataEnvio(Agora.de(clock));
        m.setAtualizadoEm(agora);
        ImovelMidia salva = midiaRepository.saveAndFlush(m);
        try {
            Path parcial = parcial(salva);
            Files.createDirectories(parcial.getParent());
            Files.deleteIfExists(parcial);
            Files.createFile(parcial);
        } catch (IOException e) {
            throw new ValidacaoAnuncioException("Não foi possível preparar o envio do vídeo.");
        }
        return MidiaResponse.de(salva);
    }

    // ----------------------------------------------------------------- partes

    /**
     * Recebe uma parte a partir de {@code offset}. O servidor so aceita a parte
     * seguinte ao que ja recebeu: se o cliente reenviar uma parte que ja chegou (a
     * resposta se perdeu), nada muda; se pular uma, recebe 409 com os bytes ja gravados.
     */
    @Transactional
    public MidiaResponse enviarParte(Usuario gestor, Long imovelId, Long midiaId, long offset, InputStream corpo) {
        ImovelMidia m = doEnvio(gestor, imovelId, midiaId);
        long recebidos = m.getBytesRecebidos() == null ? 0 : m.getBytesRecebidos();
        long total = m.getTamanhoTotal();
        if (offset > recebidos) {
            throw new ConflitoComDetalhesException("OFFSET_INESPERADO",
                    "Parte fora de ordem: o servidor já tem " + recebidos + " bytes. Continue deste ponto.",
                    List.of(Map.of("bytesRecebidos", recebidos)));
        }
        try (RandomAccessFile f = new RandomAccessFile(parcial(m).toFile(), "rw")) {
            f.seek(offset);
            byte[] buf = new byte[64 * 1024];
            long gravados = 0;
            int n;
            while ((n = corpo.read(buf)) >= 0) {
                if (offset + gravados + n > total) {
                    throw new ValidacaoAnuncioException("A parte enviada passa do tamanho informado para o vídeo.");
                }
                f.write(buf, 0, n);
                gravados += n;
            }
            long novo = Math.max(recebidos, offset + gravados);
            m.setBytesRecebidos(novo);
            m.setAtualizadoEm(clock.instant());
            return MidiaResponse.de(midiaRepository.save(m));
        } catch (IOException e) {
            throw new ValidacaoAnuncioException("Não foi possível gravar a parte enviada. Tente novamente.");
        }
    }

    /** Estado do envio, para retomar apos uma queda: o cliente continua de {@code bytesRecebidos}. */
    @Transactional(readOnly = true)
    public MidiaResponse estado(Usuario gestor, Long imovelId, Long midiaId) {
        acesso.doGestor(gestor, imovelId);
        return MidiaResponse.de(daqui(imovelId, midiaId));
    }

    // --------------------------------------------------------------- concluir

    /**
     * Valida o arquivo inteiro no servidor. Invalido (tipo real, tamanho, sem trilha de
     * video, mais de 1:30, resolucao alta demais): apaga o envio e a midia e devolve o motivo.
     */
    @Transactional(noRollbackFor = ValidacaoAnuncioException.class) // a limpeza do arquivo invalido precisa ser gravada
    public MidiaResponse concluir(Usuario gestor, Long imovelId, Long midiaId) {
        ImovelMidia m = doEnvio(gestor, imovelId, midiaId);
        long recebidos = m.getBytesRecebidos() == null ? 0 : m.getBytesRecebidos();
        if (recebidos != m.getTamanhoTotal()) {
            throw new ValidacaoAnuncioException("Envio incompleto: faltam " + (m.getTamanhoTotal() - recebidos) + " bytes.");
        }
        Path parcial = parcial(m);
        Path original = storage.caminhoLocal(m.getOriginalArquivo());
        try {
            Files.move(parcial, original, StandardCopyOption.REPLACE_EXISTING);
            validar(original, m);
        } catch (VideoProcessingService.VideoInvalidoException | ValidacaoAnuncioException e) {
            descartar(m);
            midiaRepository.flush();
            throw e instanceof ValidacaoAnuncioException ? e : new ValidacaoAnuncioException(e.getMessage());
        } catch (IOException e) {
            descartar(m);
            throw new ValidacaoAnuncioException("Não foi possível ler o vídeo enviado.");
        }
        Instant agora = clock.instant();
        m.setStatusProcessamento(StatusVideo.PROCESSANDO);
        m.setTentativas(0);
        m.setProximaTentativaEm(agora);
        m.setMotivoFalha(null);
        m.setAtualizadoEm(agora);
        return MidiaResponse.de(midiaRepository.save(m));
    }

    private void validar(Path arquivo, ImovelMidia m) throws IOException {
        String real = tipoReal(arquivo);
        String declarado = m.getMime();
        boolean familiaMp4 = "mp4".equals(real) || "mov".equals(real);
        if (real == null || ("video/webm".equals(declarado) ? !"webm".equals(real) : !familiaMp4)) {
            throw new ValidacaoAnuncioException("O arquivo não é um vídeo " + extensaoLegivel(declarado)
                    + " válido (o conteúdo não corresponde ao formato informado).");
        }
        if (Files.size(arquivo) > props.tamanhoMaxMb() * 1024L * 1024L) {
            throw new ValidacaoAnuncioException("O vídeo excede o tamanho máximo de " + props.tamanhoMaxMb() + " MB.");
        }
        VideoProcessingService.Metadados meta = processamento.inspecionar(arquivo);
        if (meta.duracaoSegundos() > props.duracaoMaxSegundos()) {
            throw new ValidacaoAnuncioException(MidiaProcessador.mensagemDuracao(meta.duracaoSegundos(), props.duracaoMaxSegundos()));
        }
        if (meta.altura() > props.alturaMaxEntrada()) {
            throw new ValidacaoAnuncioException("A resolução do vídeo (" + meta.altura() + "p) excede o máximo aceito ("
                    + props.alturaMaxEntrada() + "p).");
        }
        m.setDuracaoSegundos((int) Math.ceil(meta.duracaoSegundos()));
        m.setLargura(meta.largura() > 0 ? meta.largura() : null);
        m.setAltura(meta.altura() > 0 ? meta.altura() : null);
    }

    // --------------------------------------------------------------- cancelar

    /** Cancela um envio em andamento (ou falho): apaga a parte gravada e a midia. */
    @Transactional
    public void cancelar(Usuario gestor, Long imovelId, Long midiaId) {
        acesso.doGestorParaAtualizar(gestor, imovelId);
        ImovelMidia m = daqui(imovelId, midiaId);
        if (m.getTipo() != TipoMidia.VIDEO) {
            throw new ValidacaoAnuncioException("Esta mídia não é um vídeo.");
        }
        descartar(m);
    }

    /** Job: envios iniciados e nunca concluidos ha mais do que o limite. */
    @Transactional
    public int limparOrfaos() {
        Instant limite = clock.instant().minus(props.orfaoHoras(), ChronoUnit.HOURS);
        List<ImovelMidia> orfaos = midiaRepository.findByStatusProcessamentoAndAtualizadoEmBefore(StatusVideo.ENVIANDO, limite);
        orfaos.forEach(this::descartar);
        if (!orfaos.isEmpty()) {
            log.info("{} envio(s) de video abandonado(s) removido(s).", orfaos.size());
        }
        return orfaos.size();
    }

    // ----------------------------------------------------------------- apoio

    private void descartar(ImovelMidia m) {
        storage.removerPrefixo("videos/" + m.getChave());
        midiaRepository.delete(m);
    }

    private ImovelMidia doEnvio(Usuario gestor, Long imovelId, Long midiaId) {
        acesso.doGestor(gestor, imovelId);
        ImovelMidia m = daqui(imovelId, midiaId);
        if (m.getTipo() != TipoMidia.VIDEO || m.getStatusProcessamento() != StatusVideo.ENVIANDO) {
            throw new TransicaoInvalidaException("Este vídeo não está em envio.");
        }
        return m;
    }

    private ImovelMidia daqui(Long imovelId, Long midiaId) {
        return midiaRepository.findById(midiaId)
                .filter(x -> x.getImovel().getId().equals(imovelId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mídia não encontrada."));
    }

    private Path parcial(ImovelMidia m) {
        return storage.caminhoLocal("videos/" + m.getChave() + "/parcial.part");
    }

    /** Tipo pelo conteudo: caixa "ftyp" (MP4/MOV) ou cabecalho EBML (WebM/Matroska). */
    static String tipoReal(Path arquivo) throws IOException {
        byte[] b = new byte[12];
        try (InputStream in = Files.newInputStream(arquivo)) {
            if (in.readNBytes(b, 0, 12) < 12) {
                return null;
            }
        }
        if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return b[8] == 'q' && b[9] == 't' ? "mov" : "mp4";
        }
        if ((b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45 && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3) {
            return "webm";
        }
        return null;
    }

    private static String extensao(String mime) {
        return switch (mime) {
            case "video/quicktime" -> "mov";
            case "video/webm" -> "webm";
            default -> "mp4";
        };
    }

    private static String extensaoLegivel(String mime) {
        return extensao(mime).toUpperCase();
    }
}
