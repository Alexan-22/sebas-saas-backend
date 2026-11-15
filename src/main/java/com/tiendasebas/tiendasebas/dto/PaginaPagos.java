package com.tiendasebas.tiendasebas.dto;
import java.util.List;
import java.util.function.ToIntFunction;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Cursor por id_venta: no omite registros al insertar nuevos pagos durante la lectura. */
public record PaginaPagos<T>(List<T> registros,Integer siguienteCursor,boolean hasMore){
 public static void validar(Integer cursor,int size){if(size<1||size>200||(cursor!=null&&cursor<1))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Paginacion invalida");}
 public static <T> PaginaPagos<T> desde(List<T> rows,int size,ToIntFunction<T> id){boolean more=rows.size()>size;var data=List.copyOf(rows.subList(0,Math.min(size,rows.size())));return new PaginaPagos<>(data,more?id.applyAsInt(data.getLast()):null,more);}
}
