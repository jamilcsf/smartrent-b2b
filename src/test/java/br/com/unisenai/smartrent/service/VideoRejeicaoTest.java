package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.dto.MidiaResponse;
import br.com.unisenai.smartrent.model.Endereco;
import br.com.unisenai.smartrent.model.Imovel;
import br.com.unisenai.smartrent.model.Usuario;
import br.com.unisenai.smartrent.model.enums.PapelUsuario;
import br.com.unisenai.smartrent.model.enums.StatusAnuncio;
import br.com.unisenai.smartrent.model.enums.TipoImovel;
import br.com.unisenai.smartrent.repository.ImovelMidiaRepository;
import br.com.unisenai.smartrent.repository.ImovelRepository;
import br.com.unisenai.smartrent.repository.UsuarioRepository;
import br.com.unisenai.smartrent.service.erro.ValidacaoAnuncioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Com transacoes reais (sem a transacao de teste que "engole" o rollback): a
 * rejeicao de um video invalido precisa GRAVAR a limpeza, senao a midia recusada
 * ficaria no banco ocupando a cota de 2 videos.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:videosrejeicao;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({VideoUploadService.class, VideoWorker.class, MidiaService.class, MidiaProcessador.class, ImovelAcesso.class,
        MidiaStorageDisco.class, VideoPipelineTest.Config.class})
class VideoRejeicaoTest {

    @Autowired private VideoUploadService uploads;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ImovelRepository imoveis;
    @Autowired private ImovelMidiaRepository midiaRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CT606 - A rejeicao do video invalido grava a limpeza: a midia some e a cota de 2 videos nao fica presa")
    void rejeicaoGravaALimpeza() {
        Usuario dono = new Usuario();
        dono.setNome("Dono");
        dono.setEmail("dono" + System.nanoTime() + "@smartrent.dev");
        dono.setSenhaHash("x".repeat(60));
        dono.setPapel(PapelUsuario.ANFITRIAO);
        dono.setAtivo(true);
        dono = usuarios.save(dono);

        Imovel i = new Imovel();
        i.setUsuario(dono);
        i.setTitulo("Apto");
        i.setTipoImovel(TipoImovel.APARTAMENTO);
        i.setCapacidadeHospedes(4);
        i.setNumeroQuartos(1);
        i.setNumeroBanheiros(1);
        i.setValorDiariaBase(new BigDecimal("300.00"));
        i.setStatus(StatusAnuncio.PRE_PUBLICACAO_SEM_PRECO);
        Endereco e = new Endereco();
        e.setLogradouro("Rua A");
        e.setBairro("Centro");
        e.setCidade("Florianopolis");
        e.setEstado("SC");
        e.setCep("88000-000");
        i.setEndereco(e);
        Imovel alvo = imoveis.save(i);
        Usuario gestor = dono;

        byte[] conteudo = Mp4Falso.comDuracao(91, 2000);
        MidiaResponse m = uploads.iniciar(gestor, alvo.getId(), conteudo.length, "video/mp4");
        uploads.enviarParte(gestor, alvo.getId(), m.id(), 0, new ByteArrayInputStream(conteudo));
        assertThrows(ValidacaoAnuncioException.class, () -> uploads.concluir(gestor, alvo.getId(), m.id()));

        assertTrue(midiaRepository.findById(m.id()).isEmpty(), "a exclusao foi confirmada no banco");
        assertDoesNotThrow(() -> uploads.iniciar(gestor, alvo.getId(), 1000, "video/mp4"));
        assertDoesNotThrow(() -> uploads.iniciar(gestor, alvo.getId(), 1000, "video/mp4"));
    }
}
