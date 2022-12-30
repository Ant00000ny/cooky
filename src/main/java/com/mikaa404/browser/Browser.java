package com.mikaa404.browser;

import com.mikaa404.cookie.Cookie;

import java.util.List;

public interface Browser {
    String getBrowserName();
    
    List<Cookie> getAllCookies();
}
