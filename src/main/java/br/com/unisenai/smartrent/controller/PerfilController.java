package br.com.unisenai.smartrent.controller;

import br.com.unisenai.smartrent.dto.AtualizarPerfilRequest;
import br.com.unisenai.smartrent.dto.AuthResponse;
import br.com.unisenai.smartrent.dto.ConfirmarEmailRequest;
import br.com.unisenai.smartrent.dto.EmailRequest;
import br.com.unisenai.smartrent.dto.ExclusaoDadosDtos;
import br.com.unisenai.smartrent.dto.MensagemResponse;
import br.com.unisenai.smartrent.dto.PerfilResponse;
import br.com.unisenai.smartrent.dto.SenhaRequest;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.service.ExclusaoDadosService;
import br.com.unisenai.smartrent.service.FotoPerfilService;
import br.com.unisenai.smartrent.service.PerfilService;
import br.com.unisenai.smartrent.service.SenhaService;
import br.com.unisenai.smartrent.service.TrocaEmailService;
import br.com.unisenai.smartrent.service.VerificacaoEmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Perfil do proprio usuario. Nenhum endpoint recebe id de usuario (nem na URL, nem no
 * corpo): tudo age sobre quem esta autenticado, o que elimina IDOR por construcao.
 */
@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;
    private final FotoPerfilService fotoService;
    private final SenhaService senhaService;
    private final TrocaEmailService trocaEmailService;
    private final VerificacaoEmailService verificacaoEmail;
    private final ExclusaoDadosService exclusaoService;

    public PerfilController(PerfilService perfilService, FotoPerfilService fotoService, SenhaService senhaService,
                            TrocaEmailService trocaEmailService, ExclusaoDadosService exclusaoService,
                            VerificacaoEmailService verificacaoEmail) {
        this.verificacaoEmail = verificacaoEmail;
        this.exclusaoService = exclusaoService;
        this.perfilService = perfilService;
        this.fotoService = fotoService;
        this.senhaService = senhaService;
        this.trocaEmailService = trocaEmailService;
    }

    @GetMapping
    public PerfilResponse obter(@AuthenticationPrincipal Usuario usuario) {
        return perfilService.obter(usuario);
    }

    @PatchMapping
    public PerfilResponse atualizar(@AuthenticationPrincipal Usuario usuario,
                                    @RequestBody AtualizarPerfilRequest req, HttpServletRequest http) {
        return perfilService.atualizarNome(usuario, req.nome(), http.getRemoteAddr());
    }

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilResponse enviarFoto(@AuthenticationPrincipal Usuario usuario,
                                     @RequestParam("arquivo") MultipartFile arquivo,
                                     HttpServletRequest http) throws IOException {
        // Recusa pelo tamanho declarado antes de ler o arquivo para a memoria.
        if (arquivo.getSize() > fotoService.limiteBytes()) {
            throw new IllegalArgumentException(
                    "Use uma imagem PNG ou JPG de até " + fotoService.limiteBytes() / (1024 * 1024) + " MB.");
        }
        return fotoService.enviar(usuario, arquivo.getBytes(), http.getRemoteAddr());
    }

    @DeleteMapping("/foto")
    public PerfilResponse removerFoto(@AuthenticationPrincipal Usuario usuario, HttpServletRequest http) {
        return fotoService.remover(usuario, http.getRemoteAddr());
    }

    /** Troca a senha e devolve um token novo para esta sessao (as demais deixam de valer). */
    @PostMapping("/senha")
    public AuthResponse alterarSenha(@AuthenticationPrincipal Usuario usuario, @RequestBody SenhaRequest req,
                                     HttpServletRequest http) {
        return senhaService.alterar(usuario, req, http.getRemoteAddr());
    }

    /** Pede a troca do e-mail de login. A resposta e sempre a mesma, exista ou nao conta com o endereco. */
    @PostMapping("/email")
    public MensagemResponse solicitarTrocaEmail(@AuthenticationPrincipal Usuario usuario, @RequestBody EmailRequest req,
                                                HttpServletRequest http) {
        return new MensagemResponse(trocaEmailService.solicitar(usuario, req.novoEmail(), req.senhaAtual(),
                req.credencialGoogle(), http.getRemoteAddr()));
    }

    /** Confirma o link recebido por e-mail. Publico: o token de uso unico e a prova (ver SecurityConfig). */
    @PostMapping("/email/confirmar")
    public MensagemResponse confirmarTrocaEmail(@RequestBody ConfirmarEmailRequest req, HttpServletRequest http) {
        trocaEmailService.confirmar(req.token(), http.getRemoteAddr());
        return new MensagemResponse("E-mail alterado com sucesso. Entre novamente com o novo endereço.");
    }

    /** Reenvia o link de verificacao do e-mail da conta (com limite de taxa). */
    @PostMapping("/email/verificacao/reenviar")
    public MensagemResponse reenviarVerificacao(@AuthenticationPrincipal Usuario usuario, HttpServletRequest http) {
        return new MensagemResponse(verificacaoEmail.reenviar(usuario, http.getRemoteAddr()));
    }

    /** Confirma o link de verificacao. Publico: o token de uso unico e a prova (ver SecurityConfig). */
    @PostMapping("/email/verificar")
    public MensagemResponse verificarEmail(@RequestBody ConfirmarEmailRequest req, HttpServletRequest http) {
        verificacaoEmail.confirmar(req.token(), http.getRemoteAddr());
        return new MensagemResponse("E-mail verificado. Agora você pode enviar mensagens no SmartChat.");
    }

    // ------------------------------------------------------------ exclusao de dados

    /** Situacao do pedido (se houver) mais os textos e as acoes que ficam restritas durante a analise. */
    @GetMapping("/exclusao-dados")
    public ExclusaoDadosDtos.Status exclusaoStatus(@AuthenticationPrincipal Usuario usuario) {
        return exclusaoService.status(usuario);
    }

    /** Registra o pedido (PENDENTE). Nada e excluido: a equipe analisa antes de qualquer exclusao. */
    @PostMapping("/exclusao-dados")
    public ExclusaoDadosDtos.Status solicitarExclusao(@AuthenticationPrincipal Usuario usuario,
                                                      @RequestBody ExclusaoDadosDtos.Pedido pedido, HttpServletRequest http) {
        return exclusaoService.solicitar(usuario, pedido.motivo(), pedido.senhaAtual(), pedido.credencialGoogle(),
                http.getRemoteAddr());
    }

    @DeleteMapping("/exclusao-dados")
    public ExclusaoDadosDtos.Status cancelarExclusao(@AuthenticationPrincipal Usuario usuario, HttpServletRequest http) {
        return exclusaoService.cancelar(usuario, http.getRemoteAddr());
    }

    /** "Nao fui eu": cancela pelo link do e-mail. Publico; o token de uso unico e a prova (ver SecurityConfig). */
    @PostMapping("/exclusao-dados/cancelar")
    public MensagemResponse cancelarExclusaoPorLink(@RequestBody ExclusaoDadosDtos.CancelamentoPorLink req,
                                                    HttpServletRequest http) {
        return new MensagemResponse(exclusaoService.cancelarPorLink(req.token(), http.getRemoteAddr()));
    }
}
