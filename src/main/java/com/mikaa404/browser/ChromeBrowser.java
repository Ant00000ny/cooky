package com.mikaa404.browser;

import com.mikaa404.cookie.ChromeCookie;
import com.mikaa404.cookie.Cookie;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChromeBrowser implements Browser {
    public ChromeBrowser() {
        if (SystemUtils.IS_OS_MAC) {
            return;
        } else {
            throw new RuntimeException(String.format("OS %s %s is not supported. ", SystemUtils.OS_NAME, SystemUtils.OS_VERSION));
        }
    }
    
    private static final Path TEMP_FILE_PATH = Paths.get(SystemUtils.USER_DIR, "Cookies.sqlite.tmp");
    
    @Override
    public String getBrowserName() {
        return "Chrome";
    }
    
    @Override
    public List<Cookie> getAllCookies() {
        // TODO: provide option to get cookies by Chrome user profile, for convenience of multi-profile users
        // TODO: multi-thread may be better?
        return getCookieFilePaths().stream()
                       .map(this::readFromCookieFile)
                       .flatMap(Collection::stream)
                       .collect(Collectors.toList());
    }
    
    /**
     * This method will search all `Cookies` file in Chrome profile path.
     * <p>
     * Note: There will be multiple `Cookies` file if created multiple profile while using Chrome.
     *
     * @return a list of `Cookies` file paths.
     */
    private List<Path> getCookieFilePaths() {
        final Path cookieStorePath = Paths.get(SystemUtils.USER_HOME, "Library", "Application Support", "Google", "Chrome");
        
        try (Stream<Path> pathStream = Files.walk(cookieStorePath)) {
            return pathStream.filter(p -> StringUtils.equals(p.toFile().getName(), "Cookies"))
                           .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed while accessing cookie file: %s", e.getMessage()));
        }
    }
    
    /**
     * The `Cookies` file in Chrome profile is stored in sqlite database format. This method uses sqlite library to
     * extract cookies information.
     *
     * @param cookieFile path of `Cookies` file.
     * @return a list of {@link Cookie} stored in this file.
     */
    private List<Cookie> readFromCookieFile(Path cookieFile) {
        Path targetPath = copyFileToTemp(cookieFile);
        final String datasourceUrl = String.format("jdbc:sqlite:%s", targetPath);
        //language=SQL
        final String queryAllSql = "SELECT * FROM cookies;";
        
        // manually load sqlite class is needed, otherwise connection to sqlite will fail.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(String.format("Failed to load sqlite driver class: %s", e.getMessage()));
        }
        
        try (Connection connection = DriverManager.getConnection(datasourceUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(queryAllSql)) {
            List<Cookie> cookieList = new ArrayList<>();
            while (resultSet.next()) {
                // I think these column is sufficient for now. TODO: extract all cookie info.
                ChromeCookie chromeCookie = new ChromeCookie(resultSet.getString("host_key"),
                                                             resultSet.getString("name"),
                                                             resultSet.getBytes("encrypted_value"),
                                                             resultSet.getString("path"));
                cookieList.add(chromeCookie);
            }
            
            // manually delete temp cookies file
            deleteTempFile(targetPath);
            return cookieList;
        } catch (SQLException e) {
            throw new RuntimeException(String.format("Failed while execute SQL operations: %s", e.getMessage()));
        }
    }
    
    /**
     * In order to avoid sqlite database lock.
     * <p>
     * Note: Temp file is supposed to be deleted after cookies processing.
     *
     * @return path of copy of `Cookies` file.
     */
    private Path copyFileToTemp(Path source) {
        try {
            return Files.copy(source, TEMP_FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed copying cookies store file: %s", e.getMessage()));
        }
    }
    
    private void deleteTempFile(Path targetPath) {
        if (!targetPath.toFile().delete()) {
            throw new RuntimeException(String.format("Failed to delete copy of Cookie file: %s", targetPath));
        }
    }
}
