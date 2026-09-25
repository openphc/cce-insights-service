package org.openphc.cce.insights.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    /**
     * Patient ids are taken verbatim from the event subject, and some sources send a FHIR reference
     * there (e.g. {@code Group/856237}), so a patient path segment can carry an encoded slash
     * ({@code /patients/Group%2F856237/...}). Tomcat rejects {@code %2F} in a path with a 400 by
     * default. Passing it through keeps it inside the one segment: Spring matches the encoded path
     * and decodes {@code {patientId}} to {@code Group/856237}. Decoding it instead would split the id
     * into two segments and no mapping would match.
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> encodedSlashInPathCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector ->
                connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue()));
    }
}
