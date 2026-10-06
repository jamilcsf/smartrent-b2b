package br.com.unisenai.smartrent.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Inventario dos controllers e de como cada um e protegido pelo {@code SecurityConfig}. O SecurityConfig termina em
 * {@code anyRequest().permitAll()} (a inversao para negar por padrao e Visao Futura, V2): por isso, criar um controller
 * novo quebra este teste ate que alguem decida, por escrito, se a rota e publica ou protegida.
 */
class InventarioDeRotasTest {

    private static final String PUBLICA = "PUBLICA (por desenho)";
    private static final String GESTOR = "GESTOR/ADMIN (SecurityConfig)";
    private static final String AUTENTICADA = "AUTENTICADA (SecurityConfig + posse no servico)";
    private static final String MISTA = "MISTA (rotas publicas e autenticadas, ver SecurityConfig)";

    private static final Map<String, String> ESPERADO = Map.ofEntries(
            Map.entry("AuthController", MISTA),                    // login/cadastro publicos, /me autenticado
            Map.entry("CalendarioController", GESTOR),             // /api/gestor/**
            Map.entry("ConfigController", PUBLICA),                // hora do servidor
            Map.entry("DisponibilidadeController", PUBLICA),       // datas indisponiveis do anuncio
            Map.entry("EstatisticasController", GESTOR),
            Map.entry("FotoPerfilController", PUBLICA),            // nome de arquivo imprevisivel
            Map.entry("GestorImovelController", GESTOR),
            Map.entry("ImovelController", PUBLICA),                // catalogo
            Map.entry("MidiaPublicaController", PUBLICA),          // midias do catalogo (chave UUID)
            Map.entry("NotificacaoController", GESTOR),
            Map.entry("PerfilController", MISTA),
            Map.entry("PoliticaController", PUBLICA),
            Map.entry("PrecificacaoGestorController", GESTOR),
            Map.entry("ReservaClienteController", AUTENTICADA),
            Map.entry("ReservaController", GESTOR),
            Map.entry("SmartChatController", AUTENTICADA),         // /stream usa ticket de uso unico
            Map.entry("SugestaoPrecoController", GESTOR),          // /api/precificacao/** (grava e consulta a IA)
            Map.entry("TermoController", PUBLICA),
            Map.entry("VideoGestorController", GESTOR));

    @Test
    @DisplayName("CT1024 - Todo controller REST esta no inventario de rotas; um novo exige decisao explicita")
    void inventario() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, String> encontrados = new TreeMap<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("br.com.unisenai.smartrent.controller")) {
            if (bd.getBeanClassName().contains("$")) {
                continue; // controllers falsos aninhados em testes
            }
            String nome = bd.getBeanClassName().substring(bd.getBeanClassName().lastIndexOf('.') + 1);
            encontrados.put(nome, ESPERADO.getOrDefault(nome, "NAO CLASSIFICADO: decida e registre aqui"));
        }
        assertEquals(new TreeMap<>(ESPERADO), encontrados,
                "Controller novo ou removido: atualize o inventario e confira o SecurityConfig.");
    }
}
