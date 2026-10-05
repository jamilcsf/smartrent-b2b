package br.com.unisenai.smartrent.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CifraCampoTest {

    private static byte[] chave(int semente) {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (semente + i);
        }
        return b;
    }

    private final CifraCampo cifra = new CifraCampo(chave(1));

    @Test
    @DisplayName("ida e volta preserva acentos, emoji e multiplas linhas")
    void idaEVolta() {
        String texto = "Olá, ação não é São João 😀🏖️\nsegunda linha — “aspas”";
        String cifrado = cifra.cifrar(texto);
        assertTrue(cifrado.startsWith("v1:"));
        assertFalse(cifrado.contains("Olá"));
        assertEquals(texto, cifra.decifrar(cifrado));
        assertEquals("", cifra.decifrar(cifra.cifrar("")));
    }

    @Test
    @DisplayName("o mesmo texto cifrado duas vezes gera valores diferentes (IV aleatorio)")
    void ivAleatorio() {
        assertNotEquals(cifra.cifrar("mesmo texto"), cifra.cifrar("mesmo texto"));
    }

    @Test
    @DisplayName("ciphertext adulterado lanca excecao")
    void adulterado() {
        String cifrado = cifra.cifrar("mensagem importante");
        byte[] bytes = Base64.getDecoder().decode(cifrado.substring(3));
        bytes[bytes.length - 1] ^= 0x01; // mexe na tag
        String adulterado = "v1:" + Base64.getEncoder().encodeToString(bytes);
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar(adulterado));

        byte[] corpo = Base64.getDecoder().decode(cifrado.substring(3));
        corpo[13] ^= 0x01; // mexe no texto cifrado
        String adulterado2 = "v1:" + Base64.getEncoder().encodeToString(corpo);
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar(adulterado2));
    }

    @Test
    @DisplayName("valor truncado, Base64 invalido e versao desconhecida lancam excecao")
    void malformado() {
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar("v1:abc"));
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar("v1:%%%nao-e-base64%%%"));
        assertThrows(FalhaDecifragemException.class, () -> cifra.decifrar("v2:" + cifra.cifrar("x").substring(3)));
    }

    @Test
    @DisplayName("chave errada lanca excecao e a mensagem nao vaza o texto")
    void chaveErrada() {
        String cifrado = cifra.cifrar("segredo do cliente");
        CifraCampo outra = new CifraCampo(chave(77));
        FalhaDecifragemException e = assertThrows(FalhaDecifragemException.class, () -> outra.decifrar(cifrado));
        assertFalse(String.valueOf(e.getMessage()).contains("segredo"));
    }

    @Test
    @DisplayName("valor legado sem prefixo de versao volta intacto")
    void legado() {
        assertEquals("texto puro antigo", cifra.decifrar("texto puro antigo"));
        assertEquals("vou chegar às 14h: ok", cifra.decifrar("vou chegar às 14h: ok"));
        assertFalse(CifraCampo.estaCifrado("texto puro antigo"));
        assertTrue(CifraCampo.estaCifrado(cifra.cifrar("x")));
    }

    @Test
    @DisplayName("null entra, null sai")
    void nulo() {
        assertNull(cifra.cifrar(null));
        assertNull(cifra.decifrar(null));
    }

    @Test
    @DisplayName("chave Base64 precisa ter 32 bytes")
    void tamanhoDaChave() {
        assertThrows(IllegalArgumentException.class, () -> CifraCampo.deBase64(null));
        assertThrows(IllegalArgumentException.class, () -> CifraCampo.deBase64("  "));
        assertThrows(IllegalArgumentException.class, () -> CifraCampo.deBase64("%%%"));
        assertThrows(IllegalArgumentException.class,
                () -> CifraCampo.deBase64(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalArgumentException.class, () -> new CifraCampo(new byte[31]));
        assertNotNull(CifraCampo.deBase64(Base64.getEncoder().encodeToString(chave(5))));
    }

    @Test
    @DisplayName("conversor JPA cifra ao gravar, decifra ao ler e preserva null")
    @SuppressWarnings("unchecked")
    void conversor() {
        ObjectProvider<CifraCampo> provedor = Mockito.mock(ObjectProvider.class);
        Mockito.when(provedor.getObject()).thenReturn(cifra);
        TextoCifradoConverter conversor = new TextoCifradoConverter(provedor);

        String coluna = conversor.convertToDatabaseColumn("Oi, tudo bem?");
        assertTrue(coluna.startsWith("v1:"));
        assertEquals("Oi, tudo bem?", conversor.convertToEntityAttribute(coluna));
        assertNull(conversor.convertToDatabaseColumn(null));
        assertNull(conversor.convertToEntityAttribute(null));
        assertEquals("legado", conversor.convertToEntityAttribute("legado"));
    }
}
