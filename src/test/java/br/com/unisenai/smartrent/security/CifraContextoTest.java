package br.com.unisenai.smartrent.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/** v2: o contexto (tabela.coluna) e autenticado; v1 legado continua legivel; valor ilegivel nao derruba a leitura. */
class CifraContextoTest {

    private static final byte[] CHAVE = new byte[32];

    static {
        for (int i = 0; i < CHAVE.length; i++) {
            CHAVE[i] = (byte) (i + 7);
        }
    }

    private final CifraCampo cifra = new CifraCampo(CHAVE);

    @SuppressWarnings("unchecked")
    private TextoCifradoConverter conversor(Class<? extends TextoCifradoConverter> tipo) throws Exception {
        ObjectProvider<CifraCampo> provedor = Mockito.mock(ObjectProvider.class);
        Mockito.when(provedor.getObject()).thenReturn(cifra);
        return tipo.getConstructor(ObjectProvider.class).newInstance(provedor);
    }

    /** Valor v1 do jeito antigo: sem AAD. */
    private String v1(String texto) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(CHAVE, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
        byte[] saida = new byte[12 + ct.length];
        System.arraycopy(iv, 0, saida, 0, 12);
        System.arraycopy(ct, 0, saida, 12, ct.length);
        return "v1:" + Base64.getEncoder().encodeToString(saida);
    }

    @Test
    @DisplayName("CT1060 - O valor so decifra com o contexto com que foi cifrado")
    void contextoAutenticado() {
        String valor = cifra.cifrar("segredo", "smartchat_mensagens.texto_filtrado");
        assertEquals("segredo", cifra.decifrar(valor, "smartchat_mensagens.texto_filtrado"));
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar(valor, "smartchat_mensagens.texto_original"));
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar(valor, "notificacoes.mensagem"));
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar(valor));
    }

    @Test
    @DisplayName("CT1061 - O que ja estava gravado em v1 (sem AAD) continua legivel em qualquer contexto")
    void v1Legado() throws Exception {
        String antigo = v1("mensagem antiga");
        assertTrue(antigo.startsWith("v1:"));
        assertEquals("mensagem antiga", cifra.decifrar(antigo, "smartchat_mensagens.texto_filtrado"));
        assertEquals("mensagem antiga", cifra.decifrar(antigo));
        assertFalse(cifra.ehTextoPuro(antigo, "notificacoes.mensagem"));
    }

    @Test
    @DisplayName("CT1062 - Valor copiado de uma coluna para outra aparece como indisponivel, sem derrubar a leitura")
    void valorTrocadoDeColuna() throws Exception {
        TextoCifradoConverter filtrada = conversor(MensagemFiltradaConverter.class);
        TextoCifradoConverter original = conversor(MensagemOriginalConverter.class);

        String naFiltrada = filtrada.convertToDatabaseColumn("Ligue 48 99999-0000");
        assertEquals("Ligue 48 99999-0000", filtrada.convertToEntityAttribute(naFiltrada));
        assertEquals(TextoCifradoConverter.INDISPONIVEL, original.convertToEntityAttribute(naFiltrada));
        assertEquals(TextoCifradoConverter.INDISPONIVEL,
                conversor(NotificacaoMensagemConverter.class).convertToEntityAttribute(naFiltrada));
    }

    @Test
    @DisplayName("CT1063 - Valor adulterado ou de outra chave: aviso no lugar; nulo e texto puro legado seguem como antes")
    void leituraTolerante() throws Exception {
        TextoCifradoConverter conv = conversor(DenunciaDescricaoConverter.class);
        String valido = conv.convertToDatabaseColumn("descricao");
        byte[] bytes = Base64.getDecoder().decode(valido.substring(3));
        bytes[bytes.length - 1] ^= 0x01;
        String adulterado = "v2:" + Base64.getEncoder().encodeToString(bytes);

        assertEquals(TextoCifradoConverter.INDISPONIVEL, conv.convertToEntityAttribute(adulterado));
        assertEquals(TextoCifradoConverter.INDISPONIVEL, conv.convertToEntityAttribute("v2:abc"));
        assertNull(conv.convertToEntityAttribute(null));
        assertEquals("texto antigo", conv.convertToEntityAttribute("texto antigo"));
        assertFalse(TextoCifradoConverter.INDISPONIVEL.contains("descricao"), "o aviso nunca carrega conteudo");
    }
}
