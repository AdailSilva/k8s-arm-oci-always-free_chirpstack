package br.com.adailsilva.chirpstackmanager.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.adailsilva.chirpstackmanager.api.web.AuditoriaInterceptor;

@Configuration
@EnableScheduling
public class WebConfig implements WebMvcConfigurer {

    private final AuditoriaInterceptor auditoria;

    public WebConfig(AuditoriaInterceptor auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoria).addPathPatterns("/api/v1/chirpstack/**", "/api/v1/dispositivos/**",
                "/api/v1/regras-alerta/**", "/api/v1/alertas/**", "/api/v1/usuarios/**", "/api/v1/conta-email/**",
                "/api/v1/comandos/**", "/api/v1/cenas/**", "/api/v1/agendamentos/**", "/api/v1/automacoes/**");
    }
}
