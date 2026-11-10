package com.tiendasebas.tiendasebas.pagos;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
class PaymentAdminErrorsTest {
    @Test void denialKeeps403WithoutInternalMessage() {
        var response=new PaymentAdminErrors().denied(new AccessDeniedException("internal-detail"));
        assertEquals(403,response.getStatusCode().value()); assertFalse(response.getBody().toString().contains("internal-detail"));
    }
    @Test void databaseFailureKeeps500WithoutSqlDetails() {
        var response=new PaymentAdminErrors().database(new DataAccessResourceFailureException("internal-sql"));
        assertEquals(500,response.getStatusCode().value()); assertFalse(response.getBody().toString().contains("internal-sql"));
    }
}
