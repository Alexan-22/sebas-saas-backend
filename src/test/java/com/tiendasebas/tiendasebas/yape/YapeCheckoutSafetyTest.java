package com.tiendasebas.tiendasebas.yape;
import com.tiendasebas.tiendasebas.config.TenantContext;
import com.tiendasebas.tiendasebas.dto.*;
import com.tiendasebas.tiendasebas.entity.Usuario;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.tiendasebas.tiendasebas.repository.*;
import com.tiendasebas.tiendasebas.service.VentaService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class YapeCheckoutSafetyTest {
 @Test void rejectsOnlineYapeBeforeStockOrSalesWrites() {
  var users=mock(UsuarioRepository.class);var sales=mock(VentaRepository.class);var details=mock(DetalleVentaRepository.class);var stock=mock(ProductoTallaRepository.class);
  var user=new Usuario();user.setEstado(true);
  when(users.findByCorreoAndTiendaId("fixture@example.test",1L)).thenReturn(Optional.of(user));
  var pagos=mock(PagoYapeManualService.class);
  doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Yape no esta habilitado para esta tienda")).when(pagos).validarHabilitado(1L);
  var service=new VentaService(sales,details,stock,users,null,null,null,null,pagos,null);
  var request=new CompraRequest();request.setMedioPago("YAPE");request.setItems(List.of(new CompraItemDto()));
  TenantContext.setCurrentTenant(1L);
  try {var ex=assertThrows(ResponseStatusException.class,()->service.realizarCompra(request,"fixture@example.test"));assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());assertEquals("Yape no esta habilitado para esta tienda",ex.getReason());verifyNoInteractions(sales,details,stock);}
  finally {TenantContext.clear();}
 }
}
