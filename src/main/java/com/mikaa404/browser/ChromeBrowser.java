package com.mikaa404.browser;

import com.mikaa404.cookie.ChromeCookie;
import com.mikaa404.cookie.Cookie;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class ChromeBrowser implements Browser {
    private static final Path tmpDir = Paths.get(System.getProperty("user.dir"), "tmp");
    @Override
    public String getBrowserName() {
        return "Chrome";
    }
    
    @Override
    public List<Cookie> getCookies() {
        return getCookieFilePaths().stream()
                       .map(this::readFromCookieFile)
                       .flatMap(Collection::stream)
                       .toList();
    }
    
    private List<Path> getCookieFilePaths() {
        Path cookieStorePath = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "Google", "Chrome");
        try (Stream<Path> pathStream = Files.walk(cookieStorePath)) {
            return pathStream.filter(p -> StringUtils.equals(p.toFile().getName(), "Cookies"))
                           .toList();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed while accessing cookie file: %s", e.getMessage()));
        }
    }
    
    private List<Cookie> readFromCookieFile(Path cookieFile) {
        final String datasourceUrl = String.format("jdbc:sqlite:%s", copyFileToTemp(cookieFile));
        
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load sqlite driver class. ");
        }
        
        try (Connection connection = DriverManager.getConnection(datasourceUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM cookies;")) {
            List<Cookie> cookieList = new ArrayList<>();
            while (resultSet.next()) {
                ChromeCookie chromeCookie = new ChromeCookie(
                        resultSet.getString("host_key"),
                        resultSet.getString("name"),
                        resultSet.getBytes("encrypted_value"),
                        resultSet.getString("path")
                );
                
                
                cookieList.add(chromeCookie);
            }
            return cookieList;
        } catch (SQLException e) {
            throw new RuntimeException("Failed while execute SQL operations. ");
        }
    }
    
    /**
     * In order to avoid locked sqlite database. Temp file will be automatically deleted on app exit.
     */
    private Path copyFileToTemp(Path source) {
        try {
            Path target = Files.copy(source, tmpDir, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        } catch (IOException e) {
            throw new RuntimeException("Failed copying cookies store file. ");
        }
    }
}
