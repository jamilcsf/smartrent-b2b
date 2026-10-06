package br.com.unisenai.smartrent.config;

import br.com.unisenai.smartrent.service.MidiaStorage;
import br.com.unisenai.smartrent.service.MidiaStorageDisco;
import br.com.unisenai.smartrent.service.MidiaStorageR2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Liga o Cloudflare R2 so quando R2_BUCKET e R2_ENDPOINT existem; sem eles o disco local segue como unico
 * armazenamento (desenvolvimento e testes). Credenciais apenas por variavel de ambiente.
 */
@Configuration
@ConditionalOnExpression("!'${smartrent.midia.r2.bucket:}'.isEmpty() && !'${smartrent.midia.r2.endpoint:}'.isEmpty()")
public class MidiaR2Config {

    @Bean(destroyMethod = "close")
    public S3Client r2Client(@Value("${smartrent.midia.r2.endpoint}") String endpoint,
                             @Value("${smartrent.midia.r2.access-key:}") String chaveAcesso,
                             @Value("${smartrent.midia.r2.secret-key:}") String chaveSecreta) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(chaveAcesso, chaveSecreta)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    @Bean
    @Primary
    public MidiaStorage midiaStorageR2(S3Client r2Client,
                                       @Value("${smartrent.midia.r2.bucket}") String bucket,
                                       @Value("${smartrent.midia.r2.url-publica:}") String urlPublica,
                                       MidiaStorageDisco disco) {
        return new MidiaStorageR2(r2Client, bucket, urlPublica, disco);
    }
}
