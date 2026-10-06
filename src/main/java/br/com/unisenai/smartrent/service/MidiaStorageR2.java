package br.com.unisenai.smartrent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Fotos (de anuncio, miniaturas e perfil) no Cloudflare R2, pela API S3. Videos ({@code videos/...}) continuam no
 * disco: o envio em partes e o FFmpeg precisam de arquivo local, e o R2 so recebe as imagens.
 *
 * <p>Com {@code urlPublica} configurada (dominio do bucket ou r2.dev), o controller redireciona para ela e os bytes
 * nao passam pela aplicacao; sem ela, a foto e lida do bucket e entregue pelo servidor.
 */
public class MidiaStorageR2 implements MidiaStorage {

    private static final Logger log = LoggerFactory.getLogger(MidiaStorageR2.class);
    private static final String PREFIXO_VIDEO = "videos/";
    private static final String CACHE = "public, max-age=31536000, immutable"; // a chave e um UUID e o arquivo nunca muda

    private final S3Client s3;
    private final String bucket;
    private final String urlPublica;
    private final MidiaStorageDisco disco;

    public MidiaStorageR2(S3Client s3, String bucket, String urlPublica, MidiaStorageDisco disco) {
        this.s3 = s3;
        this.bucket = bucket;
        this.urlPublica = urlPublica == null || urlPublica.isBlank() ? null : urlPublica.replaceAll("/+$", "");
        this.disco = disco;
    }

    @Override
    public void salvar(String nome, Path origem) throws IOException {
        if (local(nome)) {
            disco.salvar(nome, origem);
            return;
        }
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(nome)
                    .contentType(tipo(nome)).cacheControl(CACHE).build(), RequestBody.fromFile(origem));
        } catch (RuntimeException e) {
            throw new IOException("Falha ao enviar a midia ao R2.", e); // sem a mensagem do SDK: cita bucket e chave
        }
    }

    @Override
    public Resource abrir(String nome) {
        if (local(nome)) {
            return disco.abrir(nome);
        }
        try (InputStream in = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(nome).build())) {
            return new ByteArrayResource(in.readAllBytes());
        } catch (NoSuchKeyException e) {
            return AUSENTE;
        } catch (IOException | RuntimeException e) {
            log.warn("Nao foi possivel ler a midia do R2: {}", e.getClass().getSimpleName());
            return AUSENTE;
        }
    }

    @Override
    public void remover(String nome) {
        if (local(nome)) {
            disco.remover(nome);
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(nome).build());
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel remover a midia do R2: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Path caminhoLocal(String nome) {
        if (local(nome)) {
            return disco.caminhoLocal(nome);
        }
        throw new UnsupportedOperationException("Imagens ficam no R2, sem caminho local.");
    }

    @Override
    public String urlPublica(String nome) {
        return urlPublica == null || local(nome) ? null : urlPublica + "/" + nome;
    }

    @Override
    public void removerPrefixo(String prefixo) {
        disco.removerPrefixo(prefixo); // so videos tem pasta, e eles estao no disco
    }

    private static boolean local(String nome) {
        return nome.startsWith(PREFIXO_VIDEO);
    }

    private static String tipo(String nome) {
        String n = nome.toLowerCase();
        return n.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private static final Resource AUSENTE = new AbstractResource() {
        @Override
        public String getDescription() {
            return "midia ausente";
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new IOException("Midia ausente.");
        }
    };
}
