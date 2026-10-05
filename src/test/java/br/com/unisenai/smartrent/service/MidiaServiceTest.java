package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.RelogioFalso;
import br.com.unisenai.smartrent.model.AnuncioRascunho;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.ImovelMidia;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.EstadoMidia;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoMidia;
import br.com.unisenai.smartrent.repository.AnuncioRascunhoRepository;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.service.erro.AcessoNegadoException;
import br.com.unisenai.smartrent.service.erro.TransicaoInvalidaException;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Limites de midia (14 imagens somadas, 2 videos) e comportamento fora e dentro da edicao. */
@ExtendWith(MockitoExtension.class)
class MidiaServiceTest {

    @Mock
    private ImovelRepository imovelRepository;
    @Mock
    private ImovelMidiaRepository midiaRepository;
    @Mock
    private AnuncioRascunhoRepository rascunhoRepository;
    @Mock
    private MidiaStorage storage;
    @Mock
    private MidiaProcessador processador;

    private MidiaService service;
    private Usuario gestor;
    private Imovel imovel;
    private List<ImovelMidia> existentes;
    private long proximoId = 100;

    @BeforeEach
    void preparar() {
        service = new MidiaService(new ImovelAcesso(imovelRepository), midiaRepository, rascunhoRepository,
                storage, processador, new RelogioFalso(LocalDateTime.of(2026, 10, 1, 9, 0)));
        gestor = new Usuario();
        gestor.setId(10L);
        gestor.setPapel(PapelUsuario.ANFITRIAO);
        imovel = new Imovel();
        imovel.setId(1L);
        imovel.setUsuario(gestor);
        imovel.setStatus(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        existentes = new ArrayList<>();
        lenient().when(imovelRepository.findByIdParaAtualizar(1L)).thenReturn(Optional.of(imovel));
        lenient().when(midiaRepository.findByImovelIdOrderByOrdemAscIdAsc(1L)).thenAnswer(i -> List.copyOf(existentes));
        lenient().when(midiaRepository.saveAndFlush(any(ImovelMidia.class))).thenAnswer(i -> {
            ImovelMidia m = i.getArgument(0);
            m.setId(proximoId++);
            existentes.add(m);
            return m;
        });
    }

    private void preencher(int fotos, int panoramicas, int videos) {
        for (int i = 0; i < fotos; i++) {
            existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        }
        for (int i = 0; i < panoramicas; i++) {
            existentes.add(midia(TipoMidia.FOTO_360, EstadoMidia.ATIVA));
        }
        for (int i = 0; i < videos; i++) {
            existentes.add(midia(TipoMidia.VIDEO, EstadoMidia.ATIVA));
        }
    }

    private ImovelMidia midia(TipoMidia tipo, EstadoMidia estado) {
        ImovelMidia m = new ImovelMidia();
        m.setId(proximoId++);
        m.setImovel(imovel);
        m.setChave("chave-" + m.getId());
        m.setTipo(tipo);
        m.setEstado(estado);
        m.setArquivo("arq-" + m.getId() + ".jpg");
        m.setOrdem(existentes.size());
        return m;
    }

    private MockMultipartFile arquivo(String mime) {
        return new MockMultipartFile("arquivo", "x", mime, new byte[]{1, 2, 3, 4});
    }

    private void processadorAceita(TipoMidia tipo) {
        if (tipo.imagem()) {
            lenient().when(processador.inspecionarImagem(any(), any(), anyBoolean()))
                    .thenReturn(new MidiaProcessador.Inspecao("image/jpeg", "jpg", 400, 200, null));
            lenient().when(processador.sanearImagem(any(), any())).thenAnswer(i -> i.getArgument(1));
        } else {
            lenient().when(processador.inspecionarVideo(any(), any(), anyInt()))
                    .thenReturn(new MidiaProcessador.Inspecao("video/mp4", "mp4", null, null, 30));
        }
    }

    // -------------------------------------------------------- limite de 14

    @Test
    @DisplayName("CT83 - 10 comuns + 4 em 360 = 14: a 15a imagem, de qualquer tipo, e recusada")
    void limiteSomaFotosComunsE360() throws IOException {
        preencher(10, 4, 0);
        processadorAceita(TipoMidia.FOTO);

        var erro1 = assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO));
        var erro2 = assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO_360));

        assertTrue(erro1.getMessage().contains("14"));
        assertTrue(erro2.getMessage().contains("14"));
        verify(processador, never()).inspecionarImagem(any(), any(), anyBoolean());
        verify(storage, never()).salvar(anyString(), any());
    }

    @Test
    @DisplayName("CT84 - Com 13 imagens somadas ainda cabe mais uma; ao chegar em 14 fecha")
    void decimaQuartaImagemEAceita() throws IOException {
        preencher(9, 4, 0);
        processadorAceita(TipoMidia.FOTO);

        var resposta = service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO);

        assertEquals(TipoMidia.FOTO, resposta.tipo());
        assertEquals(EstadoMidia.ATIVA, resposta.estado());
        verify(storage, atLeastOnce()).salvar(anyString(), any());
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO_360));
    }

    @Test
    @DisplayName("CT85 - Videos nao entram na conta das 14 imagens")
    void videosNaoContamNoLimiteDeImagens() {
        preencher(14, 0, 1);
        processadorAceita(TipoMidia.VIDEO);

        var resposta = service.adicionar(gestor, 1L, arquivo("video/mp4"), TipoMidia.VIDEO);

        assertEquals(TipoMidia.VIDEO, resposta.tipo());
    }

    @Test
    @DisplayName("CT86 - Mais de 2 videos e recusado")
    void limiteDeDoisVideos() {
        preencher(0, 0, 2);
        var erro = assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("video/mp4"), TipoMidia.VIDEO));
        assertTrue(erro.getMessage().contains("2"));
    }

    @Test
    @DisplayName("CT87 - Primeira imagem enviada vira a capa automaticamente")
    void primeiraImagemViraCapa() {
        processadorAceita(TipoMidia.FOTO);
        var primeira = service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO);
        var segunda = service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO);

        assertTrue(primeira.capa());
        assertFalse(segunda.capa());
    }

    @Test
    @DisplayName("CT88 - Arquivo invalido nao deixa lixo no armazenamento nem no banco")
    void arquivoInvalidoNaoPersiste() throws IOException {
        when(processador.inspecionarImagem(any(), any(), anyBoolean()))
                .thenThrow(new ValidacaoAnuncioException("Foto 360 deve ter proporção 2:1"));

        assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO_360));

        verify(storage, never()).salvar(anyString(), any());
        verify(midiaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("CT89 - Foto 360 e validada como panoramica; foto comum, nao")
    void panoramicaEhValidadaComoTal() {
        processadorAceita(TipoMidia.FOTO);
        service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO_360);
        service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO);

        ArgumentCaptor<Boolean> panoramica = ArgumentCaptor.forClass(Boolean.class);
        verify(processador, times(2)).inspecionarImagem(any(), any(), panoramica.capture());
        assertEquals(List.of(true, false), panoramica.getAllValues());
    }

    // ---------------------------------------------------- autorizacao e estado

    @Test
    @DisplayName("CT90 - Gestor nao envia midia para imovel de outro gestor")
    void naoEnviaParaImovelAlheio() {
        Usuario outro = new Usuario();
        outro.setId(99L);
        outro.setPapel(PapelUsuario.ANFITRIAO);
        assertThrows(AcessoNegadoException.class,
                () -> service.adicionar(outro, 1L, arquivo("image/jpeg"), TipoMidia.FOTO));
    }

    @Test
    @DisplayName("CT91 - Anuncio publicado nao aceita midia direto: e preciso iniciar a edicao")
    void publicadoExigeEdicao() {
        imovel.setStatus(StatusAnuncio.PUBLICADO);
        assertThrows(TransicaoInvalidaException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO));
        assertThrows(TransicaoInvalidaException.class, () -> service.remover(gestor, 1L, 5L));
    }

    // ------------------------------------------------------------ em edicao

    @Test
    @DisplayName("CT92 - Em edicao, midia nova nasce NOVA e a removida de um anuncio vivo vira REMOVIDA (nao some)")
    void edicaoMantemAnuncioVivoIntacto() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        ImovelMidia viva = existentes.get(0);
        when(midiaRepository.findById(viva.getId())).thenReturn(Optional.of(viva));
        processadorAceita(TipoMidia.FOTO);

        var nova = service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO);
        service.remover(gestor, 1L, viva.getId());

        assertEquals(EstadoMidia.NOVA, nova.estado());
        assertEquals(EstadoMidia.REMOVIDA, viva.getEstado());
        verify(midiaRepository, never()).delete(viva);
        verify(storage, never()).remover(viva.getArquivo());
    }

    @Test
    @DisplayName("CT93 - Em edicao, o limite conta NOVAS e nao conta REMOVIDAS")
    void limiteEmEdicao() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        for (int i = 0; i < 13; i++) {
            existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        }
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.NOVA));   // 14 efetivas
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.REMOVIDA)); // nao conta
        processadorAceita(TipoMidia.FOTO);

        assertThrows(ValidacaoAnuncioException.class,
                () -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO));

        existentes.get(0).setEstado(EstadoMidia.REMOVIDA); // libera uma vaga
        assertDoesNotThrow(() -> service.adicionar(gestor, 1L, arquivo("image/jpeg"), TipoMidia.FOTO));
    }

    @Test
    @DisplayName("CT94 - Em edicao, a nova ordem e a capa vao para o rascunho, nao para as midias vivas")
    void ordemECapaVaoParaORascunho() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        Long a = existentes.get(0).getId();
        Long b = existentes.get(1).getId();
        existentes.get(0).setCapa(true);
        AnuncioRascunho rascunho = new AnuncioRascunho();
        when(rascunhoRepository.findByImovelId(1L)).thenReturn(Optional.of(rascunho));

        service.ordenar(gestor, 1L, List.of(b, a));
        service.definirCapa(gestor, 1L, b);

        assertEquals(b + "," + a, rascunho.getMidiaOrdem());
        assertEquals(b, rascunho.getCapaMidiaId());
        assertEquals(0, existentes.get(0).getOrdem(), "ordem original intacta");
        assertTrue(existentes.get(0).isCapa(), "capa original intacta");
    }

    @Test
    @DisplayName("CT95 - Descartar desfaz: NOVAS somem (com arquivo) e REMOVIDAS voltam a ATIVA")
    void desfazerRascunhoRestauraMidias() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.REMOVIDA));
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.NOVA));
        ImovelMidia removida = existentes.get(1);
        ImovelMidia nova = existentes.get(2);

        service.desfazerRascunho(imovel);

        assertEquals(EstadoMidia.ATIVA, removida.getEstado());
        verify(midiaRepository).delete(nova);
        verify(storage).remover(nova.getArquivo());
        verify(midiaRepository, never()).delete(removida);
    }

    @Test
    @DisplayName("CT96 - Confirmar efetiva: REMOVIDAS saem, NOVAS ficam ATIVAS e ordem/capa do rascunho valem")
    void aplicarRascunhoEfetiva() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.ATIVA));
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.REMOVIDA));
        existentes.add(midia(TipoMidia.FOTO, EstadoMidia.NOVA));
        ImovelMidia viva = existentes.get(0);
        ImovelMidia removida = existentes.get(1);
        ImovelMidia nova = existentes.get(2);
        AnuncioRascunho rascunho = new AnuncioRascunho();
        rascunho.setMidiaOrdem(nova.getId() + "," + viva.getId());
        rascunho.setCapaMidiaId(nova.getId());

        service.aplicarRascunho(imovel, rascunho);

        verify(midiaRepository).delete(removida);
        assertEquals(EstadoMidia.ATIVA, nova.getEstado());
        assertEquals(0, nova.getOrdem());
        assertEquals(1, viva.getOrdem());
        assertTrue(nova.isCapa());
        assertFalse(viva.isCapa());
    }

    // -------------------------------------------------- alternar comum/360

    @Test
    @DisplayName("CT97 - Marcar imagem como 360 exige proporcao 2:1; voltar a comum sempre pode")
    void alternarTipoValidaProporcao() {
        ImovelMidia larga = midia(TipoMidia.FOTO, EstadoMidia.ATIVA);
        larga.setLargura(400);
        larga.setAltura(200);
        ImovelMidia quadrada = midia(TipoMidia.FOTO, EstadoMidia.ATIVA);
        quadrada.setLargura(300);
        quadrada.setAltura(200);
        existentes.add(larga);
        existentes.add(quadrada);
        when(midiaRepository.findById(larga.getId())).thenReturn(Optional.of(larga));
        when(midiaRepository.findById(quadrada.getId())).thenReturn(Optional.of(quadrada));
        lenient().when(midiaRepository.save(any(ImovelMidia.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals(TipoMidia.FOTO_360, service.alterarTipo(gestor, 1L, larga.getId(), TipoMidia.FOTO_360).tipo());
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.alterarTipo(gestor, 1L, quadrada.getId(), TipoMidia.FOTO_360));
        assertEquals(TipoMidia.FOTO, service.alterarTipo(gestor, 1L, larga.getId(), TipoMidia.FOTO).tipo());
        assertThrows(ValidacaoAnuncioException.class,
                () -> service.alterarTipo(gestor, 1L, larga.getId(), TipoMidia.VIDEO));
    }

    @Test
    @DisplayName("CT98 - Em edicao, imagem do anuncio vivo nao muda de tipo (descarte ficaria inexato); a NOVA pode")
    void alternarTipoEmEdicao() {
        imovel.setStatus(StatusAnuncio.EM_EDICAO);
        ImovelMidia viva = midia(TipoMidia.FOTO, EstadoMidia.ATIVA);
        ImovelMidia nova = midia(TipoMidia.FOTO, EstadoMidia.NOVA);
        viva.setLargura(400);
        viva.setAltura(200);
        nova.setLargura(400);
        nova.setAltura(200);
        when(midiaRepository.findById(viva.getId())).thenReturn(Optional.of(viva));
        when(midiaRepository.findById(nova.getId())).thenReturn(Optional.of(nova));
        lenient().when(midiaRepository.save(any(ImovelMidia.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(TransicaoInvalidaException.class,
                () -> service.alterarTipo(gestor, 1L, viva.getId(), TipoMidia.FOTO_360));
        assertEquals(TipoMidia.FOTO_360, service.alterarTipo(gestor, 1L, nova.getId(), TipoMidia.FOTO_360).tipo());
        assertEquals(TipoMidia.FOTO, viva.getTipo());
    }
}
