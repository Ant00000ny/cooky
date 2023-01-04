package com.mikaa404.browser;

import com.mikaa404.cookie.ChromeCookie;
import com.mikaa404.cookie.ICookie;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChromeBrowser implements IBrowser {
    private static ChromeBrowser instance;
    private static Path TEMP_FILE_FOLDER;
    
    public static ChromeBrowser getInstance() {
        if (instance != null) {
            return instance;
        }
        
        synchronized (ChromeBrowser.class) {
            if (instance != null) {
                return instance;
            }
            
            instance = new ChromeBrowser();
            return instance;
        }
    }
    
    private ChromeBrowser() {
        if (SystemUtils.IS_OS_MAC) {
            TEMP_FILE_FOLDER = Paths.get("/", "tmp", "cookyTmpStore");
            return;
        } else {
            throw new RuntimeException(String.format("OS %s is not supported. ", SystemUtils.OS_NAME));
        }
    }
    
    @Override
    public String getBrowserName() {
        return "Chrome";
    }
    
    @Override
    public List<ICookie> getAllCookies() {
        prepareTempFolder();
        
        List<ICookie> CookieList = getCookieFilePaths().stream()
                                           // use cookies in first profile by default
                                           .findFirst()
                                           .map(this::readFromCookieFile)
                                           .orElseGet(ArrayList::new);
        
        deleteTempFolder();
        return CookieList;
    }
    
    /**
     * Get all cookies from a specific profile.
     *
     * @param profileName specified profile name
     * @return a list of cookies stored in that profile name, or empty list if no profile of that name or cookies is
     * found.
     */
    public List<ICookie> getAllCookies(String profileName) {
        return getCookieFilePaths().stream()
                       .filter(p -> StringUtils.equals(profileName, p.getParent().getFileName().toString()))
                       .findFirst()
                       .map(this::readFromCookieFile)
                       .orElseGet(ArrayList::new);
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
            throw new RuntimeException("Failed while accessing cookie file: ", e);
        }
    }
    
    /**
     * The `Cookies` file in Chrome profile is stored in sqlite database format. This method uses sqlite library to
     * extract cookies information.
     *
     * @param cookieFile path of `Cookies` file.
     * @return a list of {@link ICookie} stored in this file.
     */
    private List<ICookie> readFromCookieFile(Path cookieFile) {
        Path targetPath = copyFileToTemp(cookieFile);
        final String datasourceUrl = String.format("jdbc:sqlite:%s", targetPath);
        //language=SQL
        final String queryAllSql = "SELECT * FROM cookies;";
        
        // manually load sqlite class is needed, otherwise connection to sqlite will fail.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load sqlite driver class: ", e);
        }
        
        try (Connection connection = DriverManager.getConnection(datasourceUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(queryAllSql)) {
            List<ICookie> cookieList = new ArrayList<>();
            while (resultSet.next()) {
                ChromeCookie chromeCookie = new ChromeCookie(
                        resultSet.getString("host_key"),
                        resultSet.getString("name"),
                        resultSet.getBytes("encrypted_value"),
                        resultSet.getString("path"),
                        resultSet.getLong("creation_utc"),
                        resultSet.getString("top_frame_site_key"),
                        resultSet.getLong("expires_utc"),
                        resultSet.getBoolean("is_secure"),
                        resultSet.getBoolean("is_httponly"),
                        resultSet.getLong("last_access_utc"),
                        resultSet.getBoolean("has_expires"),
                        resultSet.getBoolean("is_persistent"),
                        resultSet.getInt("priority"),
                        resultSet.getInt("samesite"),
                        resultSet.getInt("source_scheme"),
                        resultSet.getInt("source_port"),
                        resultSet.getBoolean("is_same_party"),
                        resultSet.getLong("last_update_utc")
                );
                cookieList.add(chromeCookie);
            }
            
            // manually delete temp cookies file
            deleteTempFile(targetPath);
            return cookieList;
        } catch (SQLException e) {
            throw new RuntimeException("Failed while execute SQL operations: ", e);
        }
    }
    
    /**
     * Copy the `Cookies` file in Chrome profile to tmp folder, in order to avoid sqlite database lock. Store path of
     * copied file depends on the running OS, for example, on macOS, file located in
     * {@code /Users/my_username/Library/Application Support/Google/Chrome/Profile 1/Cookies} will be copied to
     * {@code /tmp/cookyTmpStore/Chrome_Profile 1_Cookies}
     * <p>
     * Note: Temp file is supposed to be deleted after cookies processing.
     *
     * @return path of copy of the `Cookies` file.
     */
    private Path copyFileToTemp(Path source) {
        final String tmpFileName = String.join("_",
                                               source.getParent().getParent().getFileName().toString(),
                                               source.getParent().getFileName().toString(),
                                               source.getFileName().toString());
        final Path tmpFilePath = TEMP_FILE_FOLDER.resolve(tmpFileName);
        
        try {
            return Files.copy(source, tmpFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed copying cookies store file: ", e);
        }
    }
    
    private void deleteTempFile(Path targetPath) {
        if (!targetPath.toFile().delete()) {
            throw new RuntimeException(String.format("Failed to delete copy of Cookie file: %s", targetPath));
        }
    }
    
    private void prepareTempFolder() {
        TEMP_FILE_FOLDER.toFile().mkdirs();
        try (Stream<Path> pathStream = Files.walk(TEMP_FILE_FOLDER)) {
            List<Path> pathList = pathStream.filter(p -> !p.equals(TEMP_FILE_FOLDER))
                                          .collect(Collectors.toList());
            for (Path path : pathList) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare temp store folder: ", e);
        }
    }
    
    private void deleteTempFolder() {
        try {
            Files.deleteIfExists(TEMP_FILE_FOLDER);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete temp store folder: ", e);
        }
    }
}
