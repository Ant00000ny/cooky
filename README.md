## Maven Dependency

```xml
<dependency>
    <groupId>com.mikaa404</groupId>
    <artifactId>cooky</artifactId>
    <version>1.0</version>
</dependency>
```

## Example Usage

```java
import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.Cookie;

import java.util.List;

class Main {
    public static void main(String[] args) {
        List<Cookie> cookieList = ChromeBrowser.getInstance().getAllCookies();
        
        // do something with cookies
        for (Cookie c : cookieList) {
            System.out.printf("%s - %s - %s - %s\n", c.getHost(), c.getPath(), c.getName(), c.getValue());
        }
    }
}
```

## Note

Only supports Chrome on macOS currently. 
