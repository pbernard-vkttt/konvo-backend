package com.vulkantechtt.konvo.email;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders Thymeleaf email templates from {@code classpath:/templates/email/}.
 * Each caller passes a template name (without path prefix or .html suffix)
 * and a variable map. The {@code assetsBaseUrl} variable is injected into
 * every context so templates can reference hosted images.
 */
@Component
public class EmailTemplateRenderer {

    private final SpringTemplateEngine templateEngine;
    private final String assetsBaseUrl;

    public EmailTemplateRenderer(
            SpringTemplateEngine templateEngine,
            @Value("${konvo.email.assets-base-url:http://localhost:4200}") String assetsBaseUrl) {
        this.templateEngine = templateEngine;
        this.assetsBaseUrl = assetsBaseUrl;
    }

    public String render(String templateName, Map<String, Object> vars) {
        Map<String, Object> merged = new HashMap<>(vars);
        merged.put("assetsBaseUrl", assetsBaseUrl);
        Context ctx = new Context(java.util.Locale.ENGLISH);
        ctx.setVariables(merged);
        return templateEngine.process("email/" + templateName, ctx);
    }
}
