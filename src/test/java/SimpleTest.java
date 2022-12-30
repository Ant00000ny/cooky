import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.Cookie;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTest {
    @Test
    public void test() {
        List<Cookie> cookieList = Optional.of(new ChromeBrowser())
                                          .map(ChromeBrowser::getCookies)
                                          .orElseThrow(RuntimeException::new)
                                          .stream()
                                          .toList();
        for (Cookie c : cookieList) {
            assertTrue(ObjectUtils.allNotNull(c, c.getHost(), c.getPath(), c.getName(), c.getValue()));
        }
    }
    
    @Test
    public void test1() {
        System.out.println(System.getProperty("user.dir"));
    }
}
