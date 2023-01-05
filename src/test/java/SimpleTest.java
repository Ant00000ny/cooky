import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void test() throws IOException {
        final Path cookieStorePath = Paths.get(SystemUtils.USER_HOME, "AppData", "Local", "Google", "Chrome");
        try (Stream<Path> pathStream = Files.walk(cookieStorePath)) {
            boolean cookieInfoExist = pathStream.anyMatch(p -> StringUtils.equals("Cookies", p.getFileName().toString()));
            assertTrue(cookieInfoExist);
        }
    }
    
    @Test
    @EnabledOnOs(OS.MAC)
    public void test1() throws IOException {
        final Path cookieStorePath = Paths.get(SystemUtils.USER_HOME, "Library", "Application Support", "Google", "Chrome");
        
        try (Stream<Path> pathStream = Files.walk(cookieStorePath)) {
            boolean cookieInfoExist = pathStream.anyMatch(p -> StringUtils.equals(p.toFile().getName(), "Cookies"));
            assertTrue(cookieInfoExist);
        }
    }
}
