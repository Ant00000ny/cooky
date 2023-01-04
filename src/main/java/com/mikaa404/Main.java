package com.mikaa404;

import com.mikaa404.browser.ChromeBrowser;
import com.mikaa404.cookie.ICookie;

import java.util.List;

class Main {
    public static void main(String[] args) {
        List<ICookie> cookieList = ChromeBrowser.getInstance().getAllCookies();
        
        // do something with cookies
        for (ICookie c : cookieList) {
            System.out.printf("%s - %s - %s - %s\n",
                              c.getHostKey(),
                              c.getPath(),
                              c.getName(),
                              c.getValue()
            );
        }
    }
}
