package br.com.unisenai.smartrent.service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** MP4 minimo para testes: ftyp + moov(mvhd). So o cabecalho importa para a duracao. */
final class Mp4Falso {

    private Mp4Falso() {
    }

    /** Video de {@code segundos} segundos (escala 1000) com {@code extra} bytes de "mdat" ficticio. */
    static byte[] comDuracao(int segundos, int extra) {
        ByteBuffer ftyp = ByteBuffer.allocate(24);
        ftyp.putInt(24).put("ftyp".getBytes(StandardCharsets.ISO_8859_1))
                .put("isom".getBytes(StandardCharsets.ISO_8859_1)).putInt(0)
                .put("isom".getBytes(StandardCharsets.ISO_8859_1)).put("mp41".getBytes(StandardCharsets.ISO_8859_1));
        ByteBuffer mvhd = ByteBuffer.allocate(108);
        mvhd.putInt(108).put("mvhd".getBytes(StandardCharsets.ISO_8859_1));
        mvhd.putInt(0).putInt(0).putInt(0);
        mvhd.putInt(1000).putInt(segundos * 1000);
        ByteBuffer moov = ByteBuffer.allocate(8 + 108);
        moov.putInt(8 + 108).put("moov".getBytes(StandardCharsets.ISO_8859_1)).put(mvhd.array());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ftyp.array());
        out.writeBytes(moov.array());
        out.writeBytes(new byte[extra]);
        return out.toByteArray();
    }
}
