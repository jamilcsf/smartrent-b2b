package br.com.unisenai.smartrent.service;

import br.com.unisenai.smartrent.config.ChatProperties;
import br.com.unisenai.smartrent.service.MessageFilterService.Categoria;
import br.com.unisenai.smartrent.service.MessageFilterService.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Motor de deteccao do SmartChat: o que e borrado, o que passa e o que nunca vaza no texto de saida. */
class MessageFilterServiceTest {

    private final MessageFilterService filtro = new MessageFilterService(ChatProperties.padrao());

    private Resultado f(String texto) {
        return filtro.filtrar(texto);
    }

    private static void assertSemOriginal(Resultado r, String... trechosReais) {
        for (String t : trechosReais) {
            assertFalse(r.texto().toLowerCase().contains(t.toLowerCase()), "o texto filtrado ainda contem: " + t + " -> " + r.texto());
        }
    }

    // -------------------------------------------------------------- telefones

    @ParameterizedTest(name = "telefone: {0}")
    @DisplayName("CT400 - Telefones em varios formatos (DDD, +55, espacos, pontos, hifens, parenteses) sao borrados")
    @ValueSource(strings = {
            "me liga 48 99999-0000", "(48) 99999-0000", "(48)99999-0000", "+55 48 99999-0000", "+5548999990000",
            "48999990000", "48 9 9999 0000", "48.99999.0000", "48-99999-0000", "99999-0000", "999990000",
            "(48) 3333-4444", "48 3333 4444", "3333-4444", "0 48 99999-0000", "55 (48) 9 9999-0000"})
    void telefones(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.TELEFONE), texto + " -> " + r.texto());
        assertSemOriginal(r, "9999", "3333", "4444");
    }

    @ParameterizedTest(name = "disfarcado: {0}")
    @DisplayName("CT401 - Telefones disfarçados (digitos separados, por extenso, letras no lugar de numeros) sao borrados")
    @ValueSource(strings = {
            "4 8 9 9 9 9 9 0 0 0 0", "4-8-9-9-9-9-9-0-0-0-0", "4.8.9.9.9.9.9.0.0.0.0",
            "quatro oito nove nove nove nove nove zero zero zero zero",
            "QUATRO OITO NOVE NOVE NOVE NOVE NOVE ZERO ZERO ZERO ZERO",
            "4l 9999-OOOO l", "48 9999 OO0O"})
    void telefonesDisfarcados(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.TELEFONE), texto + " -> " + r.texto());
    }

    @ParameterizedTest(name = "legitimo: {0}")
    @DisplayName("CT402 - Datas, horas, valores em R$, quantidades, codigos da plataforma e CEP NAO sao borrados")
    @ValueSource(strings = {
            "check-in em 10/12/2026 as 14:00", "check-in dia 10-12-2026", "check-out 2026-12-13 11:00",
            "o total fica R$ 1.234,56 por 3 diárias", "R$ 980,00", "somos 4 pessoas e 2 crianças", "preciso de 3 noites e 2 quartos",
            "reserva #12345", "imóvel IMV-000123", "reserva RES-0042", "CEP 88054-000", "CEP 88054000", "valor 350,00 a diária",
            "são 12 hóspedes em 6 quartos", "chego as 14h30", "ano 2026 de 2027", "2026-2027"})
    void dadosLegitimos(String texto) {
        Resultado r = f(texto);
        assertEquals(0, r.ocorrencias(), texto + " -> " + r.texto());
        assertEquals(texto, r.texto());
    }

    // ------------------------------------------------------------------ links

    @ParameterizedTest(name = "link externo: {0}")
    @DisplayName("CT410 - Links externos (com e sem http, www, encurtadores, redes e mensageiros) sao borrados")
    @ValueSource(strings = {
            "veja https://site.com.br/oferta", "http://golpe.example.org/x?y=1", "www.exemplo.com", "exemplo.com.br",
            "olhe site.com.br agora", "bit.ly/3abc", "wa.me/5548999990000", "t.me/grupo", "instagram.com/fulano", "linktr.ee/fulano",
            "discord.gg/abc123", "youtu.be/xyz", "me chama no @fulano_oficial", "meusite ponto com", "meusite (ponto) com ponto br",
            "meusite dot com", "fulano arroba gmail ponto com"})
    void linksExternos(String texto) {
        Resultado r = f(texto);
        assertTrue(r.alterado(), texto + " -> " + r.texto());
        assertTrue(r.categorias().contains(Categoria.LINK) || r.categorias().contains(Categoria.EMAIL), texto + " -> " + r.categorias());
    }

    @Test
    @DisplayName("CT411 - E-mail e borrado como categoria propria")
    void email() {
        Resultado r = f("fale comigo: fulano.silva@gmail.com");
        assertTrue(r.categorias().contains(Categoria.EMAIL));
        assertSemOriginal(r, "fulano", "gmail");
    }

    @Test
    @DisplayName("CT412 - Links do dominio da plataforma (e subdominios) passam; os de fora, no mesmo texto, nao")
    void dominioDaPlataformaPassa() {
        String ok = "veja https://smartrent.com.br/imoveis/1 e também www.smartrent.com.br e app.smartrent.com.br/x e http://localhost:8081/imoveis/2";
        assertEquals(0, f(ok).ocorrencias(), f(ok).texto());

        Resultado misto = f("veja smartrent.com.br/imoveis/1 mas não https://outro.com/y");
        assertEquals(1, misto.ocorrencias());
        assertTrue(misto.texto().contains("smartrent.com.br/imoveis/1"));
        assertSemOriginal(misto, "outro.com");
    }

    @Test
    @DisplayName("CT413 - Dominio parecido com o da plataforma, mas de outro dono, e borrado")
    void dominioParecidoNaoPassa() {
        assertTrue(f("https://smartrent.com.br.golpe.com/x").alterado());
        assertTrue(f("https://notsmartrent.com.br/x").alterado());
        assertTrue(f("https://smartrent.com.br@golpe.com/x").alterado());
    }

    @ParameterizedTest(name = "nao e link: {0}")
    @DisplayName("CT414 - Nomes de arquivo, abreviacoes e frases comuns com ponto nao sao tomados por link")
    @ValueSource(strings = {"mandei a foto.jpg e o contrato.pdf", "ex.: casa de praia", "Sr.Silva chega amanhã", "tudo certo.Obrigado",
            "fica no ponto com a melhor vista", "no ponto com mais gente", "cheguei às 10h. Até logo.", "versão 1.5 do app"})
    void naoELink(String texto) {
        assertEquals(0, f(texto).ocorrencias(), texto + " -> " + f(texto).texto());
    }

    // --------------------------------------------------- ofensas e sexuais

    @ParameterizedTest(name = "ofensa: {0}")
    @DisplayName("CT420 - Xingamentos sao borrados, inclusive com acento, letras repetidas, simbolos e espacamento")
    @ValueSource(strings = {"seu idiota", "SEU IDIOTA", "seu iidiiota", "seu id1ota", "seu i.d.i.o.t.a", "seu i d i o t a",
            "que merda de lugar", "m3rd@", "isso é uma porra", "p0rr4", "vai tomar no cu", "vai se foder", "filho da puta", "puuuuta",
            "arrombado", "desgraçado", "imbecil", "FDP", "vtnc"})
    void ofensas(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.OFENSA), texto + " -> " + r.texto());
    }

    @ParameterizedTest(name = "sexual: {0}")
    @DisplayName("CT421 - Termos sexuais explicitos sao borrados")
    @ValueSource(strings = {"manda nudes", "isso é putaria", "que buceta", "vídeo pornô", "sexo anal", "b u c e t a", "b.u.c.e.t.a"})
    void sexuais(String texto) {
        Resultado r = f(texto);
        assertTrue(r.categorias().contains(Categoria.SEXUAL) || r.categorias().contains(Categoria.OFENSA), texto + " -> " + r.texto());
    }

    @ParameterizedTest(name = "palavra comum: {0}")
    @DisplayName("CT422 - Palavras comuns que contem um termo (computador, disputa, cuidado, escutar) nao sao borradas")
    @ValueSource(strings = {"o computador está na sala", "sem disputa de preço", "cuidado com o degrau", "vou escutar com atenção",
            "a reputação do imóvel", "adorei a cutia no quintal", "tem um cupom de desconto", "o jogo de pelada no campo"})
    void palavrasComuns(String texto) {
        assertEquals(0, f(texto).ocorrencias(), texto + " -> " + f(texto).texto());
    }

    // ------------------------------------------------- marcador e seguranca

    @Test
    @DisplayName("CT430 - O texto original NAO aparece na saida: o trecho vira marcador com preenchimento aleatorio")
    void originalNaoVaza() {
        Resultado r = f("me liga 48 99999-0000 ou veja www.golpe.com seu idiota");
        assertEquals(3, r.ocorrencias());
        assertSemOriginal(r, "99999", "0000", "golpe", "idiota");
        assertTrue(r.texto().startsWith("me liga " + MessageFilterService.ABRE + "T" + MessageFilterService.MEIO));
        assertTrue(r.categorias().containsAll(java.util.Set.of(Categoria.TELEFONE, Categoria.LINK, Categoria.OFENSA)));
    }

    @Test
    @DisplayName("CT431 - O preenchimento nao e derivado do original (dois borroes do mesmo numero diferem)")
    void preenchimentoNaoDerivaDoOriginal() {
        String a = f("48 99999-0000").texto();
        String b = f("48 99999-0000").texto();
        String conteudoA = a.substring(a.indexOf(MessageFilterService.MEIO) + 1, a.indexOf(MessageFilterService.FECHA));
        assertFalse(conteudoA.matches(".*\\d.*"), "o preenchimento nao tem digitos: " + conteudoA);
        assertNotEquals(a, b, "preenchimento aleatorio: nao repete");
    }

    @Test
    @DisplayName("CT432 - Marcadores digitados pelo usuario sao descartados: ninguem forja nem desfaz um borrao")
    void marcadoresForjadosSaoRemovidos() {
        String forjado = MessageFilterService.ABRE + "T" + MessageFilterService.MEIO + "oi" + MessageFilterService.FECHA + " ola";
        Resultado r = f(forjado);
        assertEquals(0, r.ocorrencias());
        assertEquals("T" + "oi ola", r.texto());
        assertFalse(r.texto().contains(String.valueOf(MessageFilterService.ABRE)));
    }

    @Test
    @DisplayName("CT433 - Contagem de ocorrencias e categorias para metricas; trechos que se tocam viram um so")
    void contagemEUniao() {
        Resultado r = f("ligue 48 99999-0000 e 48 98888-1111");
        assertEquals(2, r.ocorrencias());
        assertEquals(java.util.Set.of(Categoria.TELEFONE), r.categorias());
        assertEquals(1, f("http://x.com/a.html").ocorrencias());
    }

    @Test
    @DisplayName("CT434 - Texto vazio ou nulo nao quebra; semMarcadores troca o borrao por uma etiqueta neutra")
    void bordas() {
        assertEquals(0, f(null).ocorrencias());
        assertEquals("", f("").texto());
        String comMarcador = f("liga 48 99999-0000 agora").texto();
        assertEquals("liga [ocultado] agora", MessageFilterService.semMarcadores(comMarcador));
        assertFalse(MessageFilterService.semMarcadores(comMarcador).contains("9999"));
    }

    @Test
    @DisplayName("CT435 - Moderacao de imagens fica preparada, mas ainda nao implementada (sem anexos nesta etapa)")
    void imagemNaoImplementada() {
        assertThrows(UnsupportedOperationException.class, () -> filtro.moderarImagem(new byte[]{1}, "image/png"));
    }

    // ------------------------------------------------------- suspeita de fraude

    @ParameterizedTest(name = "fraude: {0}")
    @DisplayName("Fraude - termos de pagamento/contato por fora sao detectados, com variacoes de acento, caixa, simbolos e espacos")
    @ValueSource(strings = {
            "preciso de um depósito antecipado", "deposito antecipado por favor", "DEPÓSITO ANTECIPADO",
            "dá pra fazer desconto por fora?", "pagamento por fora fica mais barato", "podemos pagar por fora",
            "pix direto pra mim", "faz um pix  direto", "pix, direto", "transferência direta", "transferencia direta de R$ 300",
            "me passa a chave pix", "chave PIX é meu cpf", "mando um sinal adiantado", "sinal adiantaaado",
            "vamos combinar fora da plataforma", "fora do site é melhor", "fora do s1te",
            "me chama no zap", "me chama no whats", "me chama no whatsapp", "me chama no telegram", "me  chama  no   ZAP",
            "p a g a r  p o r  f o r a", "pagam3nto por f0ra", "d3pósito antecipado"})
    void termosDeFraude(String texto) {
        Resultado r = f(texto);
        assertTrue(r.suspeitaFraude(), texto + " -> " + r.categorias());
        assertTrue(r.categorias().contains(Categoria.SUSPEITA_FRAUDE));
    }

    @ParameterizedTest(name = "legitima: {0}")
    @DisplayName("Fraude - frases comuns sobre preco, datas e pagamento pela plataforma nao disparam")
    @ValueSource(strings = {
            "O valor da diária é R$ 350 e o pagamento é feito pela plataforma.", "Posso fazer o check-in às 14h do dia 10/12?",
            "Qual o valor do depósito caução?", "A chave da casa fica na portaria.", "Aceitam pix na reserva pelo site?",
            "O pagamento foi confirmado, obrigado!", "Tem desconto para 7 diárias?", "Fica fora do centro, mas perto da praia.",
            "Posso pagar com cartão de crédito na plataforma?", "O sinal de wifi é bom?", "Vou chegar adiantado, pode ser?",
            "Fiz a transferência da reserva pelo SmartRent", "O proprietário me chama quando chegar.", "Preciso do site do condomínio.",
            "me chama aqui no chat quando chegar", "pagamento antecipado pela plataforma"})
    void frasesLegitimasNaoDisparam(String texto) {
        Resultado r = f(texto);
        assertFalse(r.suspeitaFraude(), texto + " -> " + r.categorias());
    }

    @Test
    @DisplayName("Fraude - nao mascara nem bloqueia: o texto segue como digitado e as ocorrencias nao mudam")
    void fraudeNaoMascara() {
        String texto = "Faz um pix direto que eu dou desconto por fora";
        Resultado r = f(texto);
        assertTrue(r.suspeitaFraude());
        assertEquals(texto, r.texto());
        assertEquals(0, r.ocorrencias());
        assertFalse(r.alterado());
        // junto de telefone: so o telefone e borrado; a fraude continua sinalizada
        Resultado r2 = f("pagamento por fora no 48 99999-0000");
        assertTrue(r2.suspeitaFraude());
        assertTrue(r2.categorias().contains(Categoria.TELEFONE));
        assertEquals(1, r2.ocorrencias());
        assertTrue(r2.texto().contains("pagamento por fora"));
    }

    @Test
    @DisplayName("Fraude - lista configuravel: arquivo proprio substitui; sem linhas de fraude, mantem a lista padrao")
    void listaConfiguravel() throws java.io.IOException {
        java.nio.file.Path arq = java.nio.file.Files.createTempFile("termos", ".txt");
        java.nio.file.Files.writeString(arq, "SUSPEITA_FRAUDE|cripto direto\nOFENSA|bobalhao\n");
        ChatProperties props = new ChatProperties(java.util.List.of("smartrent.com.br"), arq.toString(), false, 1000, 20, 10, 300, "", false);
        MessageFilterService custom = new MessageFilterService(props);
        assertTrue(custom.filtrar("aceito cripto direto").suspeitaFraude());
        assertFalse(custom.filtrar("pix direto").suspeitaFraude(), "o arquivo proprio substitui a lista");

        java.nio.file.Files.writeString(arq, "OFENSA|bobalhao\n");
        MessageFilterService semFraude = new MessageFilterService(props);
        assertTrue(semFraude.filtrar("pix direto").suspeitaFraude(), "sem linhas da categoria, vale a lista padrao");
    }

    @Test
    @DisplayName("CT436 - Desempenho: 1000 caracteres de texto adversarial sao filtrados em poucos milissegundos")
    void desempenho() {
        String ruim = "a".repeat(400) + " 1 ".repeat(150) + "puuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuta ".repeat(5);
        long ini = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            f(ruim.substring(0, Math.min(1000, ruim.length())));
        }
        long msPorChamada = (System.nanoTime() - ini) / 20 / 1_000_000;
        assertTrue(msPorChamada < 100, "filtro lento: " + msPorChamada + " ms por chamada");
    }
}
