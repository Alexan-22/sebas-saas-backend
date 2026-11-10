package com.tiendasebas.tiendasebas.transferencias;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
class TransferenciaImagenTest {
 static byte[] image(String format)throws Exception{var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB),format,out);return out.toByteArray();}
 static MockMultipartFile file(String name,String mime,byte[] b){return new MockMultipartFile("archivo",name,mime,b);}
 static void invalid(MockMultipartFile f){assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->TransferenciaManualService.limpiar(f)).getStatusCode());}
 @Test void acceptsPngAndNormalizesPrivateJpeg()throws Exception{var image=TransferenciaManualService.limpiar(file("evidence.png","image/png",image("png")));assertEquals("image/jpeg",image.mime());assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.bytes())));}
 @Test void acceptsJpeg()throws Exception{assertEquals("image/jpeg",TransferenciaManualService.limpiar(file("evidence.JPG","image/jpeg",image("jpeg"))).mime());}
 @Test void acceptsExactlyFiveMiB()throws Exception{assertTrue(TransferenciaManualService.limpiar(file("evidence.png","image/png",java.util.Arrays.copyOf(image("png"),5*1024*1024))).bytes().length>0);}
 @Test void rejectsOverFiveMiB(){invalid(file("evidence.png","image/png",new byte[5*1024*1024+1]));}
 @Test void rejectsWrongExtension()throws Exception{invalid(file("evidence.html","image/png",image("png")));}
 @Test void rejectsWrongMime()throws Exception{invalid(file("evidence.png","application/octet-stream",image("png")));}
 @Test void rejectsDisguisedJpegAsPng()throws Exception{invalid(file("evidence.png","image/png",image("jpeg")));}
 @Test void rejectsInvalidBytes(){invalid(file("evidence.png","image/png",new byte[]{1,2,3}));}
 @Test void rejectsEmptyFile(){invalid(file("evidence.jpg","image/jpeg",new byte[0]));}
}
