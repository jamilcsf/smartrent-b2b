package br.com.unisenai.smartrent.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Os conversores de coluna cifrada precisam ser instanciaveis pelo conteiner do Spring (o Hibernate os
 * obtem dele). Os demais testes os constroem a mao e nao pegariam um construtor ambiguo: com dois
 * construtores e nenhum anotado, o Spring exige um construtor padrao e a aplicacao nao sobe.
 */
class ConversoresCifradosSpringTest {

    @Test
    void conteinerInstanciaOConversorBaseEOsEspecificos() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                TextoCifradoConverter.class,
                MensagemOriginalConverter.class,
                MensagemFiltradaConverter.class,
                DenunciaDescricaoConverter.class,
                NotificacaoMensagemConverter.class)) {
            assertNotNull(ctx.getBean("textoCifradoConverter", TextoCifradoConverter.class));
            assertNotNull(ctx.getBean(MensagemOriginalConverter.class));
            assertNotNull(ctx.getBean(MensagemFiltradaConverter.class));
            assertNotNull(ctx.getBean(DenunciaDescricaoConverter.class));
            assertNotNull(ctx.getBean(NotificacaoMensagemConverter.class));
        }
    }
}
