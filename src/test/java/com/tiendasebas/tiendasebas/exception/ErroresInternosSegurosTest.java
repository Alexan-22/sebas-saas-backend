package com.tiendasebas.tiendasebas.exception;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ErroresInternosSegurosTest {
 @Test void runtimeNoPublicaMensajeInterno(){var r=new GlobalExceptionHandler().manejarRuntimeException(new IllegalStateException("DETALLE_INTERNO_FICTICIO"));assertEquals(500,r.getStatusCode().value());assertFalse(r.getBody().toString().contains("DETALLE_INTERNO"));}
 @Test void sqlNoPublicaParametrosNiConsultas(){var r=new GlobalExceptionHandler().manejarBaseNoDisponible(new org.springframework.dao.DataAccessResourceFailureException("SELECT_DATO_PRIVADO_FICTICIO"));assertEquals(503,r.getStatusCode().value());assertFalse(r.getBody().toString().contains("SELECT_"));}
}
