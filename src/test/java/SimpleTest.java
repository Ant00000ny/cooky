import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.Cookie;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleTest {
    @Test
    public void test() {
        List<Cookie> cookieList = Optional.of(ChromeBrowser.getInstance())
                                          .map(ChromeBrowser::getAllCookies)
                                          .orElseThrow(RuntimeException::new);
        
        for (Cookie c : cookieList) {
            System.out.printf("%s\t%s\t%s\t%s\n", c.getHost(), c.getPath(), c.getName(), c.getValue());
            assertTrue(ObjectUtils.allNotNull(c, c.getHost(), c.getPath(), c.getName(), c.getValue()));
        }
    }
    
    @Test
    public void test1() {
        System.out.println(SystemUtils.USER_DIR);
    }
}
