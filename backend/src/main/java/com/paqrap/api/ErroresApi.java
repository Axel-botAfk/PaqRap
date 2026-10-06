package com.paqrap.api;

import com.paqrap.api.ApiModels.ErrorVista;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ErroresApi {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorVista> dominio(ApiException error) {
        return ResponseEntity.status(error.status())
                .body(new ErrorVista(error.codigo(), error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorVista> solicitudInvalida(Exception error) {
        return ResponseEntity.badRequest().body(new ErrorVista("SOLICITUD_INVALIDA",
                "Revise los campos, tipos y formato de fechas de la solicitud."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorVista> imprevisto(Exception error) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorVista("ERROR_INTERNO",
                        "La solicitud no pudo procesarse."));
    }
}
