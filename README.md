## Usage

```java
import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.Cookie;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Cookie> cookieList = new ChromeBrowser().getAllCookies();
        for (Cookie c : cookieList) {
            System.out.printf("%s - %s - %s - %s\n", c.getHost(), c.getPath(), c.getName(), c.getValue());
        }
    }
}
```

Only supports Chrome on macOS currently. 
