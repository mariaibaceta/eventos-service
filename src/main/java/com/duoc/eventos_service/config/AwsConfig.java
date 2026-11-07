package com.duoc.eventos_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class AwsConfig {

    /**
     * Cliente S3 sin credenciales en el código. La cadena por defecto del SDK busca, en orden, las variables
     * AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_SESSION_TOKEN (ejecución local) y, si no existen,
     * el rol de la instancia EC2 (LabInstanceProfile), que AWS renueva automáticamente.
     */
    @Bean(destroyMethod = "close")
    S3Client s3Client(@Value("${app.aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    /** Hora oficial de Chile para fechas de compra, emisión y modificación. */
    @Bean
    Clock reloj(@Value("${app.zona-horaria:America/Santiago}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
