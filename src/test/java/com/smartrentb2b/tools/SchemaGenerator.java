package com.smartrentb2b.tools;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.tool.schema.spi.DelayedDropRegistryNotAvailableImpl;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera o DDL PostgreSQL a partir das entidades JPA, sem abrir conexão com banco.
 *
 * <p>A geração é feita no modo <em>scripts-only</em> do Hibernate: o dialeto é
 * informado explicitamente e o acesso a metadados JDBC é desligado, de forma que
 * nenhum banco — nem mesmo descartável — participa do processo. Isso elimina o
 * risco de o DDL sair com sintaxe de outro SGBD e depois divergir do PostgreSQL
 * real do Supabase.
 *
 * <p>As estratégias de nomenclatura precisam ser <strong>as mesmas</strong> que o
 * Spring Boot aplica em runtime; qualquer divergência faria o
 * {@code ddl-auto: validate} reprovar o schema gerado por esta própria classe.
 *
 * <p>Execução: {@code ./mvnw -Pschema-gen process-test-classes}
 */
public final class SchemaGenerator {

    private static final String PACOTE_ENTIDADES = "com.smartrentb2b.domain.model";
    private static final String DESTINO_PADRAO = "target/schema-postgres.sql";

    private SchemaGenerator() {
    }

    public static void main(String[] args) throws Exception {
        Path destino = Path.of(args.length > 0 ? args[0] : DESTINO_PADRAO);
        Files.createDirectories(destino.toAbsolutePath().getParent());
        Files.deleteIfExists(destino);

        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName());
        // Sem isto o Hibernate tentaria abrir conexão para descobrir os metadados
        // do banco, o que derrubaria a geração offline.
        settings.put(AvailableSettings.ALLOW_METADATA_ON_BOOT, false);
        settings.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                CamelCaseToUnderscoresNamingStrategy.class.getName());
        settings.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY,
                SpringImplicitNamingStrategy.class.getName());
        // Modo scripts-only: nada é aplicado em banco, só escrito em arquivo.
        settings.put("jakarta.persistence.schema-generation.database.action", "none");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", destino.toString());
        settings.put("hibernate.hbm2ddl.delimiter", ";");
        settings.put(AvailableSettings.FORMAT_SQL, true);

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        try {
            MetadataSources sources = new MetadataSources(registry);
            List<Class<?>> mapeadas = descobrirClassesMapeadas();
            mapeadas.forEach(sources::addAnnotatedClass);

            Metadata metadata = sources.buildMetadata();

            SchemaManagementToolCoordinator.process(
                    metadata, registry, settings, DelayedDropRegistryNotAvailableImpl.INSTANCE);

            System.out.printf("DDL gerado em %s a partir de %d classes mapeadas:%n",
                    destino.toAbsolutePath(), mapeadas.size());
            mapeadas.forEach(c -> System.out.println("  - " + c.getName()));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    /**
     * Varre o pacote de domínio em busca de tudo que o Hibernate precisa mapear.
     * Evita manter uma lista fixa de classes que silenciosamente ficaria
     * desatualizada quando uma entidade nova for criada.
     */
    private static List<Class<?>> descobrirClassesMapeadas() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Embeddable.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(MappedSuperclass.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition definicao : scanner.findCandidateComponents(PACOTE_ENTIDADES)) {
            classes.add(Class.forName(definicao.getBeanClassName()));
        }
        classes.sort(Comparator.comparing(Class::getName));

        if (classes.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhuma classe mapeada encontrada em " + PACOTE_ENTIDADES);
        }
        return classes;
    }
}
