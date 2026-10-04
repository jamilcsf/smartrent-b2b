package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.PerfilProperties;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Foto de perfil: uma por usuario, reprocessada no servidor ({@link FotoPerfilProcessador}),
 * guardada com nome aleatorio e imprevisivel sob {@code avatares/} (o armazenamento e o mesmo
 * {@link MidiaStorage} das midias; trocar por Supabase/S3 e escrever outra implementacao).
 *
 * <p>O avatar e exibido a outros usuarios (SmartChat), por isso o acesso e publico, protegido
 * so pelo nome impossivel de adivinhar (128 bits). A URL carrega a versao para o navegador
 * nao mostrar a foto antiga depois da troca. O arquivo anterior e apagado somente depois do
 * commit; se a transacao falhar, e o arquivo novo que e descartado.
 */
@Service
public class FotoPerfilService {

    static final String PREFIXO = "avatares/";
    private static final Pattern NOME_VALIDO = Pattern.compile("^[0-9a-f]{32}\\.jpg$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final FotoPerfilProcessador processador;
    private final MidiaStorage storage;
    private final AuditoriaContaService auditoria;
    private final LimitadorDeTaxa limitador;
    private final PerfilService perfilService;
    private final PerfilProperties props;

    public FotoPerfilService(UsuarioRepository usuarioRepository, FotoPerfilProcessador processador,
                             MidiaStorage storage, AuditoriaContaService auditoria, LimitadorDeTaxa limitador,
                             PerfilService perfilService, PerfilProperties props) {
        this.usuarioRepository = usuarioRepository;
        this.processador = processador;
        this.storage = storage;
        this.auditoria = auditoria;
        this.limitador = limitador;
        this.perfilService = perfilService;
        this.props = props;
    }

    /** Limite em bytes, para o controller recusar antes de ler o arquivo inteiro para a memoria. */
    public long limiteBytes() {
        return props.fotoMaxMb() * 1024L * 1024L;
    }

    @Transactional
    public PerfilResponse enviar(Usuario sessao, byte[] bytes, String ip) {
        if (!limitador.permitir("foto:" + sessao.getId(), props.fotoUploadsPorHora(), Duration.ofHours(1))) {
            throw new LimiteExcedidoException("Muitos envios de foto em pouco tempo. Tente novamente mais tarde.");
        }
        byte[] jpeg = processador.processar(bytes); // valida e reprocessa; o original nao e guardado

        String nome = HexFormat.of().formatHex(aleatorio16()) + ".jpg";
        gravar(PREFIXO + nome, jpeg);
        descartarSeNaoConfirmar(PREFIXO + nome);

        Usuario u = perfilService.carregar(sessao);
        String anterior = u.getFotoArquivo();
        u.setFotoArquivo(nome);
        u.setFotoVersao(u.getFotoVersao() + 1);
        usuarioRepository.save(u);
        auditoria.registrar(u.getId(), AuditoriaContaService.FOTO_ALTERADA, "foto de perfil substituida", ip);
        if (anterior != null) {
            apagarAposCommit(PREFIXO + anterior);
        }
        return perfilService.montar(u);
    }

    @Transactional
    public PerfilResponse remover(Usuario sessao, String ip) {
        Usuario u = perfilService.carregar(sessao);
        String anterior = u.getFotoArquivo();
        if (anterior != null) {
            u.setFotoArquivo(null);
            u.setFotoVersao(u.getFotoVersao() + 1);
            usuarioRepository.save(u);
            auditoria.registrar(u.getId(), AuditoriaContaService.FOTO_REMOVIDA, "foto de perfil removida", ip);
            apagarAposCommit(PREFIXO + anterior);
        }
        return perfilService.montar(u);
    }

    /** Abre a foto publica; nulo se o nome nao for um dos nossos ou o arquivo nao existir. */
    public Resource abrir(String arquivo) {
        if (arquivo == null || !NOME_VALIDO.matcher(arquivo).matches()) {
            return null; // nunca chega ao armazenamento: sem "../" nem nomes arbitrarios
        }
        Resource r = storage.abrir(PREFIXO + arquivo);
        return r.exists() ? r : null;
    }

    // ------------------------------------------------------------------ apoio

    private void gravar(String chave, byte[] conteudo) {
        Path temp = null;
        try {
            temp = Files.createTempFile("avatar-", ".jpg");
            Files.write(temp, conteudo);
            storage.salvar(chave, temp);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível guardar a foto agora. Tente novamente.", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignorada) {
                    // arquivo temporario: o sistema limpa depois
                }
            }
        }
    }

    private void apagarAposCommit(String chave) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.remover(chave);
                }
            });
        } else {
            storage.remover(chave);
        }
    }

    private void descartarSeNaoConfirmar(String chave) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.remover(chave);
                    }
                }
            });
        }
    }

    private static byte[] aleatorio16() {
        byte[] b = new byte[16];
        ALEATORIO.nextBytes(b);
        return b;
    }
}
