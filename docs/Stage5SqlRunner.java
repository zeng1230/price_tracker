import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLSyntaxErrorException;

public class Stage5SqlRunner {
    private static final String[] SQL_FILES = {
            "src/main/resources/sql/00_init_database.sql",
            "src/main/resources/sql/tb_user.sql",
            "src/main/resources/sql/tb_product.sql",
            "src/main/resources/sql/tb_price_history.sql",
            "src/main/resources/sql/tb_watchlist.sql",
            "src/main/resources/sql/tb_notification.sql"
    };

    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowMultiQueries=true";
        String username = args.length > 0 ? args[0] : "root";
        String password = args.length > 1 ? args[1] : "123456";

        try (var conn = DriverManager.getConnection(url, username, password)) {
            for (String file : SQL_FILES) {
                String sql = Files.readString(Path.of(file), StandardCharsets.UTF_8);
                if (!file.endsWith("00_init_database.sql")) {
                    sql = "USE price_tracker;\n" + sql;
                }
                try (var statement = conn.createStatement()) {
                    statement.execute(sql);
                } catch (SQLSyntaxErrorException e) {
                    if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                        System.out.println("skipped existing object from " + file + ": " + e.getMessage());
                    } else {
                        throw e;
                    }
                }
                System.out.println("executed " + file);
            }

            try (var statement = conn.createStatement();
                 var rs = statement.executeQuery(
                         "SELECT table_name FROM information_schema.tables " +
                                 "WHERE table_schema='price_tracker' ORDER BY table_name")) {
                while (rs.next()) {
                    System.out.println("table " + rs.getString(1));
                }
            }

            try (var statement = conn.createStatement();
                 var rs = statement.executeQuery(
                         "SELECT index_name, column_name FROM information_schema.statistics " +
                                 "WHERE table_schema='price_tracker' AND table_name='tb_watchlist' " +
                                 "AND index_name='uk_user_product' ORDER BY seq_in_index")) {
                while (rs.next()) {
                    System.out.println("watchlist_index " + rs.getString(1) + "." + rs.getString(2));
                }
            }
        }
    }
}
