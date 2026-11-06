package com.traiding.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos que aparecen en la documentación Swagger UI ({@code /swagger-ui.html}). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI botOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Bot de trading spot BTCUSDT")
                .version("1.0.0")
                .description("""
                        Bot de grillas spot para BTC/USDT (paper trading).

                        Crea conjuntos de niveles (piso, techo, n y % por nivel que suman 100 %), \
                        el bot compra al tocar el borde inferior de cada banda y vende al tocar el \
                        superior, registrando la ganancia en BTC y USDT.

                        Cuando el precio supera el techo todo el BTC queda en USDT (estado EXHAUSTED) \
                        y se decide entre CONTINUE (mismos niveles) o NEW_SET (nuevo rango)."""));
    }
}
