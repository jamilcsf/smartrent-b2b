package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.MidiaProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Roteamento entre R2 (imagens) e disco (videos), sem rede: o S3Client e simulado. */
class MidiaStorageR2Test {

    @TempDir
    Path pasta;

    private S3Client s3;
    private MidiaStorageDisco disco;

    private MidiaStorageR2 criar(String urlPublica) {
        s3 = mock(S3Client.class);
        disco = new MidiaStorageDisco(new MidiaProperties(pasta.resolve("midias").toString(), 10, 100));
        return new MidiaStorageR2(s3, "fotos", urlPublica, disco);
    }

    @Test
    @DisplayName("Foto vai ao R2 com tipo e cache longo; video fica no disco e nao toca o R2")
    void roteiaPorTipo() throws IOException {
        MidiaStorageR2 r2 = criar(null);
        Path foto = Files.write(pasta.resolve("f.tmp"), new byte[]{1, 2, 3});

        r2.salvar("abc.jpg", foto);
        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(req.capture(), any(RequestBody.class));
        assertEquals("fotos", req.getValue().bucket());
        assertEquals("abc.jpg", req.getValue().key());
        assertEquals("image/jpeg", req.getValue().contentType());
        assertTrue(req.getValue().cacheControl().contains("immutable"));

        r2.salvar("videos/k/original.mp4", foto);
        verifyNoMoreInteractions(s3);
        assertTrue(Files.isRegularFile(r2.caminhoLocal("videos/k/original.mp4")));
        assertThrows(UnsupportedOperationException.class, () -> r2.caminhoLocal("abc.jpg"));
    }

    @Test
    @DisplayName("Falha do R2 ao enviar vira IOException sem vazar a mensagem do SDK")
    void falhaDeEnvio() throws IOException {
        MidiaStorageR2 r2 = criar(null);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("segredo no bucket fotos").build());
        Path foto = Files.write(pasta.resolve("f.tmp"), new byte[]{1});
        IOException e = assertThrows(IOException.class, () -> r2.salvar("abc.jpg", foto));
        assertFalse(e.getMessage().contains("segredo"));
    }

    @Test
    @DisplayName("Leitura: devolve os bytes quando existe e um recurso inexistente quando a chave nao existe")
    void abrir() throws IOException {
        MidiaStorageR2 r2 = criar(null);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(new byte[]{9, 8}))));
        Resource ok = r2.abrir("abc.jpg");
        assertTrue(ok.exists());
        assertEquals(2, ok.contentLength());

        when(s3.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        assertFalse(r2.abrir("nao.jpg").exists());
    }

    @Test
    @DisplayName("URL publica so existe para imagens e com a base configurada")
    void urlPublica() {
        assertEquals("https://cdn.exemplo.com/abc.jpg", criar("https://cdn.exemplo.com/").urlPublica("abc.jpg"));
        assertNull(criar("https://cdn.exemplo.com").urlPublica("videos/k/poster.jpg"));
        assertNull(criar("").urlPublica("abc.jpg"));
        assertNull(criar(null).urlPublica("abc.jpg"));
    }

    @Test
    @DisplayName("Remover apaga a chave no R2 e nao propaga falha")
    void remover() {
        MidiaStorageR2 r2 = criar(null);
        r2.remover("abc.jpg");
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().build());
        assertDoesNotThrow(() -> r2.remover("abc.jpg"));
    }
}
