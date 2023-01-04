package com.mikaa404.browser;

import com.mikaa404.cookie.ICookie;

import java.util.List;

public interface IBrowser {
    /**
     * @return name of this browser
     */
    String getBrowserName();
    
    
    /**
     * @return a list of {@link ICookie} stored in path of this browser
     */
    List<ICookie> getAllCookies();
}
