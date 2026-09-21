package br.com.unisenai.smartrent.security;

import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SEGREDO =
            "segredo-de-teste-com-mais-de-32-bytes-para-hmac-sha-256-ok";
    private static final String OUTRO_SEGREDO =
            "outro-segredo-completamente-diferente-porem-igualmente-longo";

    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        jwtService = new JwtService(SEGREDO, 3600);

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNome("Ana Beatriz Rocha");
        usuario.setEmail("ana@smartrent.dev");
        usuario.setPapel(PapelUsuario.ANFITRIAO);
    }

    @Test
    @DisplayName("CT14 - Token emitido deve ser aceito e devolver o e-mail do dono")
    void deveEmitirEValidarToken() {
        String token = jwtService.gerarToken(usuario);

        assertNotNull(token);
        assertEquals("ana@smartrent.dev", jwtService.emailDoTokenOuNull(token));
    }

    @Test
    @DisplayName("CT15 - Token adulterado deve ser recusado")
    void deveRecusarTokenAdulterado() {
        String token = jwtService.gerarToken(usuario);
        // Troca o último caractere da assinatura.
        char ultimo = token.charAt(token.length() - 1);
        String adulterado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertNull(jwtService.emailDoTokenOuNull(adulterado));
    }

    @Test
    @DisplayName("CT16 - Token assinado com outro segredo deve ser recusado")
    void deveRecusarTokenDeOutroSegredo() {
        String tokeDeOutraOrigem = new JwtService(OUTRO_SEGREDO, 3600).gerarToken(usuario);

        assertNull(jwtService.emailDoTokenOuNull(tokeDeOutraOrigem));
    }

    @Test
    @DisplayName("CT17 - Token expirado deve ser recusado")
    void deveRecusarTokenExpirado() {
        // Validade negativa produz um token cuja expiração já passou.
        String expirado = new JwtService(SEGREDO, -60).gerarToken(usuario);

        assertNull(jwtService.emailDoTokenOuNull(expirado));
    }

    @Test
    @DisplayName("CT18 - Lixo no lugar do token não deve lançar exceção")
    void deveTratarTokenMalformado() {
        assertNull(jwtService.emailDoTokenOuNull("isto-nao-e-um-jwt"));
        assertNull(jwtService.emailDoTokenOuNull(""));
    }
}
