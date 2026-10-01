package com.pokernight.web.api;

import java.net.URI;

public record ApiProblem(
    URI type,
    String title,
    int status,
    String detail,
    String code,
    URI instance
) { }
