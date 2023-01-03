package com.mikaa404.browser;

import com.mikaa404.cookie.Cookie;

import java.util.List;

public interface Browser {
    /**
     * @return name of this browser
     */
    String getBrowserName();
    
    
    /**
     * @return a list of {@link Cookie} stored in path of this browser
     */
    List<Cookie> getAllCookies();
}
