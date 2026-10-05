package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.security.CifraCampo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CifraCampoConfigTest {

    private static final String CHAVE_OK = Base64.getEncoder().encodeToString(new byte[32]);

    private static ChatProperties props(String chave) {
        return new ChatProperties(java.util.List.of(), "", false, 1000, 20, 10, 300, chave, false);
    }

    private static MockEnvironment ambiente(String... perfis) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfis);
        return env;
    }

    @Test
    @DisplayName("sem chave, fora de dev/test, a inicializacao falha com mensagem clara")
    void semChaveEmProducao() {
        CifraCampoConfig config = new CifraCampoConfig();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.cifraCampo(props(""), ambiente()));
        assertTrue(e.getMessage().contains("SMARTCHAT_CRYPTO_KEY"));
        assertThrows(IllegalStateException.class, () -> config.cifraCampo(props(null), ambiente("prod")));
    }

    @Test
    @DisplayName("chave com tamanho errado falha mesmo em dev, sem ecoar o valor")
    void tamanhoErrado() {
        CifraCampoConfig config = new CifraCampoConfig();
        String curta = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.cifraCampo(props(curta), ambiente("dev")));
        assertFalse(e.getMessage().contains(curta));
        assertThrows(IllegalStateException.class, () -> config.cifraCampo(props("nao-e-base64!"), ambiente()));
    }

    @Test
    @DisplayName("chave valida sobe em qualquer perfil; sem chave, dev e test usam a chave publica de desenvolvimento")
    void caminhosFelizes() {
        CifraCampoConfig config = new CifraCampoConfig();
        CifraCampo producao = config.cifraCampo(props(CHAVE_OK), ambiente());
        assertEquals("ola", producao.decifrar(producao.cifrar("ola")));
        for (String perfil : new String[]{"dev", "test"}) {
            CifraCampo dev = config.cifraCampo(props(""), ambiente(perfil));
            assertEquals("ola", dev.decifrar(dev.cifrar("ola")));
        }
    }

    @Test
    @DisplayName("HMAC: sem chave fora de dev/test a inicializacao falha; em dev/test usa a chave publica de desenvolvimento")
    void chaveDoHmac() {
        CifraCampoConfig config = new CifraCampoConfig();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> config.hmacTexto(ChatSegurancaProperties.padrao(), ambiente()));
        assertTrue(e.getMessage().contains("SMARTCHAT_HMAC_KEY"));
        assertNotNull(config.hmacTexto(ChatSegurancaProperties.padrao(), ambiente("dev")));
        assertNotNull(config.hmacTexto(new ChatSegurancaProperties(CHAVE_OK, 5, 3, 10, 7, 3, 60, 60), ambiente()));
    }

    @Test
    @DisplayName("o toString das propriedades nao revela a chave")
    void toStringSemChave() {
        assertFalse(props(CHAVE_OK).toString().contains(CHAVE_OK));
    }
}
