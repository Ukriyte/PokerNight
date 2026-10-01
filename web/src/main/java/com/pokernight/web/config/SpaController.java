package com.pokernight.web.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves React entry HTML only for known client routes; API 404s remain JSON/HTTP 404s. */
@Controller
public final class SpaController {
    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @GetMapping(value = {"/", "/room/{code}", "/room/{code}/play", "/room/{code}/tv"},
        produces = "text/html")
    public ResponseEntity<ClassPathResource> index() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(INDEX);
    }
}
