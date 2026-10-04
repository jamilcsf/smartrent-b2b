package br.com.unisenai.smartrent.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.OptionalDouble;

/**
 * Le a duracao de um MP4/MOV direto do cabecalho (caixa {@code moov/mvhd}),
 * sem depender de biblioteca de video. A duracao e validada no servidor: o
 * valor que o navegador informa nao vale como prova.
 *
 * <p>Arquivo que nao e ISO-BMFF, ou sem caixa {@code mvhd} legivel, devolve
 * vazio — e o chamador o trata como invalido.
 */
public final class Mp4Duracao {

    private Mp4Duracao() {
    }

    public static OptionalDouble segundos(Path arquivo) {
        try (RandomAccessFile f = new RandomAccessFile(arquivo.toFile(), "r")) {
            return procurar(f, 0, f.length(), "moov", 0);
        } catch (IOException | RuntimeException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Percorre as caixas entre [inicio, fim). Ao achar {@code moov}, desce
     * nele atras de {@code mvhd}.
     */
    private static OptionalDouble procurar(RandomAccessFile f, long inicio, long fim,
                                           String alvo, int profundidade) throws IOException {
        long pos = inicio;
        while (pos + 8 <= fim) {
            f.seek(pos);
            long tamanho = Integer.toUnsignedLong(f.readInt());
            byte[] tipoBytes = new byte[4];
            f.readFully(tipoBytes);
            String tipo = new String(tipoBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            long cabecalho = 8;
            if (tamanho == 1) {
                tamanho = f.readLong();
                cabecalho = 16;
            } else if (tamanho == 0) {
                tamanho = fim - pos;
            }
            if (tamanho < cabecalho || pos + tamanho > fim) {
                return OptionalDouble.empty();
            }

            if (tipo.equals("moov") && profundidade == 0) {
                OptionalDouble dentro = procurar(f, pos + cabecalho, pos + tamanho, "mvhd", 1);
                if (dentro.isPresent()) {
                    return dentro;
                }
            } else if (tipo.equals("mvhd") && profundidade == 1) {
                return lerMvhd(f, pos + cabecalho);
            }
            pos += tamanho;
        }
        return OptionalDouble.empty();
    }

    private static OptionalDouble lerMvhd(RandomAccessFile f, long corpo) throws IOException {
        f.seek(corpo);
        int versao = f.readUnsignedByte();
        f.skipBytes(3); // flags
        long escala;
        long duracao;
        if (versao == 1) {
            f.skipBytes(16); // criacao + modificacao (8 + 8)
            escala = Integer.toUnsignedLong(f.readInt());
            duracao = f.readLong();
        } else {
            f.skipBytes(8); // criacao + modificacao (4 + 4)
            escala = Integer.toUnsignedLong(f.readInt());
            duracao = Integer.toUnsignedLong(f.readInt());
        }
        if (escala <= 0 || duracao < 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) duracao / escala);
    }
}
