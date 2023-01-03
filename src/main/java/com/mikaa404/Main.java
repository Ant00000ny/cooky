package com.mikaa404;

import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.Cookie;

import java.util.List;

class Main {
    public static void main(String[] args) {
        List<Cookie> cookieList = new ChromeBrowser().getAllCookies();
        
        // do something with cookies
        for (Cookie c : cookieList) {
            System.out.printf("%s - %s - %s - %s\n", c.getHost(), c.getPath(), c.getName(), c.getValue());
        }
    }
}
