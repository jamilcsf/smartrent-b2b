package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatSegurancaProperties;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.DecisaoDenunciaRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.MensagemAdminRequest;
import br.com.unisenai.smartrent.dto.ModeracaoDtos.RevisaoAlertaRequest;
import br.com.unisenai.smartrent.model.AcaoModeracao;
import br.com.unisenai.smartrent.model.AlertaInterno;
import br.com.unisenai.smartrent.model.Comunicado;
import br.com.unisenai.smartrent.model.DenunciaChat;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAlertaInterno;
import br.com.unisenai.smartrent.model.enums.TipoAcaoModeracao;
import br.com.unisenai.smartrent.model.enums.TipoAlertaInterno;
import br.com.unisenai.smartrent.repository.AcaoModeracaoRepository;
import br.com.unisenai.smartrent.repository.AlertaInternoRepository;
import br.com.unisenai.smartrent.repository.ComunicadoRepository;
import br.com.unisenai.smartrent.repository.DenunciaChatRepository;
import br.com.unisenai.smartrent.repository.SmartChatMensagemRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.LimiteExcedidoException;
import br.com.unisenai.smartrent.service.erro.RecursoNaoEncontradoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras da moderacao: o que um admin pode fazer, o que fica registrado e o que o usuario recebe. */
class ModeracaoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-08T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private UsuarioRepository usuarios;
    private DenunciaChatRepository denuncias;
    private AlertaInternoRepository alertas;
    private AcaoModeracaoRepository acoes;
    private ComunicadoRepository comunicados;
    private AuditoriaContaService auditoria;
    private EmailSender email;
    private ModeracaoService service;

    private Usuario admin;
    private Usuario cliente;
    private Usuario gestor;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        denuncias = mock(DenunciaChatRepository.class);
        alertas = mock(AlertaInternoRepository.class);
        acoes = mock(AcaoModeracaoRepository.class);
        comunicados = mock(ComunicadoRepository.class);
        auditoria = mock(AuditoriaContaService.class);
        email = mock(EmailSender.class);
        when(comunicados.save(any(Comunicado.class))).thenAnswer(i -> i.getArgument(0));
        when(acoes.findFirstByAlertaIdAndTipoInOrderByIdDesc(any(), any())).thenReturn(Optional.empty());
        ChatSegurancaProperties chat = new ChatSegurancaProperties("", 5, 3, 10, 7, 3, 60, 60);
        service = new ModeracaoService(usuarios, denuncias, alertas, mock(SmartChatMensagemRepository.class), acoes, comunicados,
                auditoria, email, chat, new LimitadorDeTaxa(RELOGIO), RELOGIO);
        admin = usuario(1, PapelUsuario.ADMIN, true);
        cliente = usuario(2, PapelUsuario.CLIENTE, true);
        gestor = usuario(3, PapelUsuario.ANFITRIAO, true);
        when(usuarios.findById(1L)).thenReturn(Optional.of(admin));
        when(usuarios.findById(2L)).thenReturn(Optional.of(cliente));
        when(usuarios.findById(3L)).thenReturn(Optional.of(gestor));
    }

    private static Usuario usuario(long id, PapelUsuario papel, boolean ativo) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Pessoa " + id);
        u.setEmail("p" + id + "@t.dev");
        u.setPapel(papel);
        u.setAtivo(ativo);
        return u;
    }

    private DenunciaChat denuncia(long id, String status) {
        DenunciaChat d = new DenunciaChat();
        d.setDenunciante(cliente);
        d.setDenunciado(gestor);
        d.setMotivo("TENTATIVA_DE_GOLPE");
        d.setStatus(status);
        d.setCriadaEm(RELOGIO.instant());
        when(denuncias.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    private List<AcaoModeracao> acoesGravadas(int vezes) {
        ArgumentCaptor<AcaoModeracao> c = ArgumentCaptor.forClass(AcaoModeracao.class);
        verify(acoes, times(vezes)).save(c.capture());
        return c.getAllValues();
    }

    @Test
    @DisplayName("CT1126 - Suspender conta: exige motivo, desativa, registra a acao e a auditoria, deixa aviso na caixa e manda e-mail")
    void suspende() {
        var r = service.suspender(admin, 2L, "Tentativa de golpe confirmada", "1.1.1.1");
        assertFalse(r.ativo());
        assertFalse(cliente.isAtivo());
        verify(usuarios).save(cliente);
        AcaoModeracao a = acoesGravadas(1).get(0);
        assertEquals(TipoAcaoModeracao.SUSPENSAO, a.getTipo());
        assertEquals("Tentativa de golpe confirmada", a.getMotivo());
        verify(auditoria).registrar(2L, "ADMIN_CONTA_DESATIVADA", "Por administrador #1", "1.1.1.1");
        ArgumentCaptor<Comunicado> c = ArgumentCaptor.forClass(Comunicado.class);
        verify(comunicados).save(c.capture());
        assertEquals("Sua conta foi suspensa", c.getValue().getAssunto());
        assertTrue(c.getValue().getTexto().contains("Tentativa de golpe confirmada"));
        verify(email).enviar(eq("p2@t.dev"), anyString(), anyString());
    }

    @Test
    @DisplayName("CT1127 - Suspender: sem motivo, na propria conta, em outro admin ou em conta ja suspensa nao faz nada")
    void suspensaoRecusada() {
        assertThrows(IllegalArgumentException.class, () -> service.suspender(admin, 2L, "  ", "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.suspender(admin, 2L, "curt", "ip"));
        assertThrows(AcessoNegadoException.class, () -> service.suspender(admin, 1L, "motivo valido", "ip"));
        Usuario outroAdmin = usuario(9, PapelUsuario.ADMIN, true);
        when(usuarios.findById(9L)).thenReturn(Optional.of(outroAdmin));
        assertThrows(AcessoNegadoException.class, () -> service.suspender(admin, 9L, "motivo valido", "ip"));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.suspender(admin, 99L, "motivo valido", "ip"));
        cliente.setAtivo(false);
        assertThrows(TransicaoInvalidaException.class, () -> service.suspender(admin, 2L, "motivo valido", "ip"));
        verify(usuarios, never()).save(any());
        verify(comunicados, never()).save(any());
        verify(acoes, never()).save(any());
    }

    @Test
    @DisplayName("CT1128 - Reativar: exige conta suspensa e motivo; volta a ativa e avisa o usuario")
    void reativa() {
        cliente.setAtivo(false);
        assertTrue(service.reativar(admin, 2L, "Contestacao aceita", "ip").ativo());
        assertEquals(TipoAcaoModeracao.REATIVACAO, acoesGravadas(1).get(0).getTipo());
        verify(auditoria).registrar(2L, "ADMIN_CONTA_ATIVADA", "Por administrador #1", "ip");
        assertThrows(TransicaoInvalidaException.class, () -> service.reativar(admin, 2L, "outra vez ok", "ip"));
    }

    @Test
    @DisplayName("CT1129 - Denuncia procedente com suspensao e avisos: status, decisao, suspensao do denunciado e dois comunicados")
    void denunciaProcedente() {
        DenunciaChat d = denuncia(10, "PENDENTE");
        service.decidirDenuncia(admin, 10L, new DecisaoDenunciaRequest("procedente", "Pediu pagamento por fora", true, true, true), "ip");
        assertEquals("PROCEDENTE", d.getStatus());
        verify(denuncias).save(d);
        assertFalse(gestor.isAtivo(), "o denunciado foi suspenso");
        List<AcaoModeracao> a = acoesGravadas(2);
        assertEquals(TipoAcaoModeracao.DENUNCIA_PROCEDENTE, a.get(0).getTipo());
        assertEquals(TipoAcaoModeracao.SUSPENSAO, a.get(1).getTipo());
        assertTrue(a.get(1).getMotivo().contains("Denúncia #") && a.get(1).getMotivo().contains("Pediu pagamento por fora"));
        ArgumentCaptor<Comunicado> c = ArgumentCaptor.forClass(Comunicado.class);
        verify(comunicados, times(3)).save(c.capture()); // suspensao + denunciante + denunciado
        List<Long> destinos = c.getAllValues().stream().map(x -> x.getDestinatario().getId()).toList();
        assertTrue(destinos.contains(2L) && destinos.contains(3L));
        String aoDenunciante = c.getAllValues().stream().filter(x -> x.getDestinatario().getId() == 2L).findFirst().orElseThrow().getTexto();
        assertFalse(aoDenunciante.contains("suspens"), "o denunciante nao e informado da sancao aplicada");
    }

    @Test
    @DisplayName("CT1130 - Denuncia improcedente: nao suspende, nao avisa o denunciado; ja decidida ou justificativa curta e recusada")
    void denunciaImprocedente() {
        DenunciaChat d = denuncia(11, "PENDENTE");
        assertThrows(IllegalArgumentException.class,
                () -> service.decidirDenuncia(admin, 11L, new DecisaoDenunciaRequest("IMPROCEDENTE", "ok", false, true, false), "ip"));
        assertThrows(IllegalArgumentException.class,
                () -> service.decidirDenuncia(admin, 11L, new DecisaoDenunciaRequest("IMPROCEDENTE", "sem violacao", true, true, false), "ip"));
        assertThrows(IllegalArgumentException.class,
                () -> service.decidirDenuncia(admin, 11L, new DecisaoDenunciaRequest("TALVEZ", "sem violacao", false, true, false), "ip"));
        assertEquals("PENDENTE", d.getStatus());

        service.decidirDenuncia(admin, 11L, new DecisaoDenunciaRequest("IMPROCEDENTE", "Sem violacao das regras", false, true, true), "ip");
        assertEquals("IMPROCEDENTE", d.getStatus());
        assertTrue(gestor.isAtivo());
        ArgumentCaptor<Comunicado> c = ArgumentCaptor.forClass(Comunicado.class);
        verify(comunicados).save(c.capture()); // so o denunciante; o denunciado nao fica sabendo de denuncia improcedente
        assertEquals(2L, c.getValue().getDestinatario().getId());

        assertThrows(TransicaoInvalidaException.class,
                () -> service.decidirDenuncia(admin, 11L, new DecisaoDenunciaRequest("PROCEDENTE", "tarde demais", false, false, false), "ip"));
    }

    @Test
    @DisplayName("CT1131 - Alerta automatico: descartar (falso positivo) encerra o efeito; so se revisa uma vez; decisao e nota ficam registradas")
    void revisaAlerta() {
        AlertaInterno a = new AlertaInterno();
        a.setUsuario(cliente);
        a.setTipo(TipoAlertaInterno.ENVIO_EM_MASSA);
        a.setContagem(6);
        a.setJanelaInicio(RELOGIO.instant().minusSeconds(600));
        a.setJanelaFim(RELOGIO.instant());
        a.setCriadoEm(RELOGIO.instant().minusSeconds(60));
        when(alertas.findById(5L)).thenReturn(Optional.of(a));

        var antes = service.revisarAlerta(admin, 5L, new RevisaoAlertaRequest("REVISADO", "Confirmado, acompanhar"));
        assertTrue(antes.efeitoAtivo(), "revisado mantem a restricao de envio dentro do prazo");
        assertTrue(antes.efeito().contains("3 mensagens/min"));
        assertThrows(TransicaoInvalidaException.class, () -> service.revisarAlerta(admin, 5L, new RevisaoAlertaRequest("DESCARTADO", "mudei de ideia")));

        a.setStatus(StatusAlertaInterno.ABERTO);
        var depois = service.revisarAlerta(admin, 5L, new RevisaoAlertaRequest("DESCARTADO", "Imobiliaria enviando aviso legitimo"));
        assertEquals("DESCARTADO", depois.status());
        assertFalse(depois.efeitoAtivo());
        assertTrue(depois.efeito().contains("falso positivo"));
        assertEquals(TipoAcaoModeracao.ALERTA_DESCARTADO, acoesGravadas(2).get(1).getTipo());
        assertThrows(IllegalArgumentException.class, () -> {
            a.setStatus(StatusAlertaInterno.ABERTO);
            service.revisarAlerta(admin, 5L, new RevisaoAlertaRequest("DESCARTADO", ""));
        });
    }

    @Test
    @DisplayName("CT1132 - Mensagem da administracao: qualquer papel, em nome de um nivel, sem HTML, registrada; nao para si mesmo")
    void mensagem() {
        var r = service.enviarMensagem(admin, 3L, new MensagemAdminRequest("suporte", "<b>Atualização</b> do cadastro", "Olá <script>alert(1)</script> precisamos de um documento.", false));
        assertEquals("Suporte", r.nivel());
        assertEquals("Atualização do cadastro", r.assunto());
        assertFalse(r.texto().contains("<script"));
        assertEquals(TipoAcaoModeracao.MENSAGEM, acoesGravadas(1).get(0).getTipo());
        verify(email, never()).enviar(anyString(), anyString(), anyString());

        service.enviarMensagem(admin, 2L, new MensagemAdminRequest(null, "Aviso geral", "Manutenção programada hoje.", true));
        verify(email).enviar(eq("p2@t.dev"), anyString(), anyString());

        assertThrows(IllegalArgumentException.class, () -> service.enviarMensagem(admin, 1L, new MensagemAdminRequest("SUPORTE", "Assunto", "Texto valido aqui", false)));
        assertThrows(IllegalArgumentException.class, () -> service.enviarMensagem(admin, 2L, new MensagemAdminRequest("CEO", "Assunto", "Texto valido aqui", false)));
        assertThrows(IllegalArgumentException.class, () -> service.enviarMensagem(admin, 2L, new MensagemAdminRequest("SUPORTE", "ab", "Texto valido aqui", false)));
        assertThrows(IllegalArgumentException.class, () -> service.enviarMensagem(admin, 2L, new MensagemAdminRequest("SUPORTE", "Assunto", "x".repeat(2001), false)));
        assertThrows(RecursoNaoEncontradoException.class, () -> service.enviarMensagem(admin, 99L, new MensagemAdminRequest("SUPORTE", "Assunto", "Texto valido aqui", false)));
    }

    @Test
    @DisplayName("CT1133 - Limite de envio de avisos por admin: acima de 30 por hora e recusado")
    void limiteDeAvisos() {
        for (int i = 0; i < ModeracaoService.MENSAGENS_POR_HORA; i++) {
            service.enviarMensagem(admin, 2L, new MensagemAdminRequest("SUPORTE", "Assunto " + i, "Texto valido aqui", false));
        }
        assertThrows(LimiteExcedidoException.class,
                () -> service.enviarMensagem(admin, 2L, new MensagemAdminRequest("SUPORTE", "Um a mais", "Texto valido aqui", false)));
    }

    @Test
    @DisplayName("CT1134 - Falha no envio do e-mail nao desfaz a acao: o aviso fica na caixa do usuario")
    void falhaDeEmail() {
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp fora")).when(email).enviar(anyString(), anyString(), anyString());
        service.suspender(admin, 2L, "Golpe confirmado agora", "ip");
        assertFalse(cliente.isAtivo());
        verify(comunicados).save(any(Comunicado.class));
    }
}
