import com.youlong.hd.CacheCleaner;
import java.io.*;
import java.nio.file.*;
public class CacheCleanerCheck {
 public static void main(String[] args)throws Exception{
  Path temp=Files.createTempDirectory("clearguard-cache-test");
  File cache=Files.createDirectory(temp.resolve("cache")).toFile();Path nested=Files.createDirectories(cache.toPath().resolve("nested"));Files.write(nested.resolve("temporary"),new byte[]{1,2,3});
  Path model=Files.write(temp.resolve("model.gguf"),new byte[]{4,5});
  CacheCleaner.Result preview=CacheCleaner.inspect(new File[]{cache,null},false);if(preview.files!=1||preview.bytes!=3||!Files.exists(nested.resolve("temporary")))throw new AssertionError("preview modified files");
  try{Files.createSymbolicLink(cache.toPath().resolve("model-link"),model);CacheCleaner.Result link=CacheCleaner.inspect(new File[]{cache},false);if(link.skipped!=1)throw new AssertionError("followed symbolic link");}catch(UnsupportedOperationException|FileSystemException unavailable){}
  CacheCleaner.Result result=CacheCleaner.inspect(new File[]{cache},true);if(result.files!=1||result.bytes!=3||!cache.isDirectory()||!Files.exists(model)||Files.exists(nested))throw new AssertionError("cleanup scope");
  CacheCleaner.Result repeat=CacheCleaner.inspect(new File[]{cache},true);if(repeat.files!=0||repeat.bytes!=0)throw new AssertionError("repeat cleanup");
  Files.deleteIfExists(cache.toPath().resolve("model-link"));Files.delete(cache.toPath());Files.delete(model);Files.delete(temp);
  System.out.println("PASS preview, cleanup, retained roots, model exclusion, repeated cleanup and link handling");
 }
}
