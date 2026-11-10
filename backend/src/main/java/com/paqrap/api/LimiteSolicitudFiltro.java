package com.paqrap.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Capa defensiva adicional al limite de nginx; el cuerpo JSON esperado es diminuto. */
@Component
class LimiteSolicitudFiltro extends OncePerRequestFilter {
    private static final long MAX_BYTES = 16 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // La carga masiva de ventas es la única ruta que recibe un archivo mensual.
        long limite = "/api/datos/ventas".equals(request.getRequestURI())
                ? CargasDePedidos.MAX_CARACTERES + 1024 : MAX_BYTES;
        if (request.getContentLengthLong() > limite) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
