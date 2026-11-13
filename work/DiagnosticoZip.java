import java.nio.file.*;
import java.util.*;
public class DiagnosticoZip {
 public static void main(String[] args) throws Exception {
  Path p=Path.of(args[0]);
  System.out.println("readable="+Files.isReadable(p)+", writable="+Files.isWritable(p));
  System.out.println("before="+p.toRealPath());
  try(var zip=FileSystems.newFileSystem(p,Map.of())){System.out.println("zip opened");}
  System.out.println("closed normally");
 }
}
