package net.linlan.dsh.app.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 飞书建表脚本集成测试：H2 {@code MODE=MySQL} 下重复执行幂等、列数正确，并校验 SQL 三方口径一致。
 *
 * <p>对应 tasks 11.4 的 ①（双库幂等）与 ④（一致性校验，与 {@code scripts/check-sql-parity.sh} 同口径）。
 */
class FeishuBotSchemaIntegrationTest {

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z0-9_]+)`?", Pattern.CASE_INSENSITIVE);
    private static final int EXPECTED_TABLES = 31;
    private static final int FEISHU_BOT_COLUMNS = 11;

    private static Path root;
    private static DataSource dataSource;

    @BeforeAll
    static void resolveRoot() {
        Path module = Paths.get("").toAbsolutePath();
        if (Files.exists(module.resolve("README.md"))) {
            root = module;
        } else if (Files.exists(module.resolve("../README.md"))) {
            root = module.getParent();
        } else {
            root = module.getParent();
        }
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:feishu_schema;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static Path path(String relative) {
        return root.resolve(relative).normalize();
    }

    private static Set<String> tables(String sql) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1).toLowerCase());
        }
        return names;
    }

    private static Set<String> migrationTables() throws Exception {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(path("dsh-java-app/src/main/resources/db/migration"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                names.addAll(tables(Files.readString(file)));
            }
        }
        return names;
    }

    private static int declaredCount(Path readme, String regex) throws Exception {
        Matcher matcher = Pattern.compile(regex).matcher(Files.readString(readme));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    @Test
    void schemaIsIdempotentOnH2AndCreatesFeishuBot() throws Exception {
        FileSystemResource script = new FileSystemResource(path("dsh-java-app/src/main/resources/schema.sql").toFile());
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
        populator.setContinueOnError(false);

        populator.execute(dataSource);
        populator.execute(dataSource);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer columns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME) = 'FEISHU_BOT'", Integer.class);
        assertEquals(FEISHU_BOT_COLUMNS, columns, "feishu_bot 应为 11 列");

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            boolean found = false;
            try (ResultSet rs = meta.getTables(null, null, "FEISHU_BOT", new String[]{"TABLE"})) {
                while (rs.next()) {
                    found = true;
                }
            }
            assertTrue(found, "feishu_bot 表应存在");
        }
    }

    @Test
    void schemaAndDumpShareSameTableSet() throws Exception {
        Set<String> schema = tables(Files.readString(path("dsh-java-app/src/main/resources/schema.sql")));
        Set<String> dump = tables(Files.readString(path("docs/dev-ops/mysql/sql/dsh_java.sql")));

        Set<String> onlySchema = new TreeSet<>(schema);
        onlySchema.removeAll(dump);
        Set<String> onlyDump = new TreeSet<>(dump);
        onlyDump.removeAll(schema);

        assertTrue(onlySchema.isEmpty(), "仅 schema 有: " + onlySchema);
        assertTrue(onlyDump.isEmpty(), "仅 dump 有: " + onlyDump);
        assertEquals(EXPECTED_TABLES, schema.size());
        assertTrue(schema.contains("feishu_bot"));
    }

    @Test
    void migrationTablesAreCoveredBySchemaAndDump() throws Exception {
        Set<String> union = new HashSet<>(tables(Files.readString(path("dsh-java-app/src/main/resources/schema.sql"))));
        union.addAll(tables(Files.readString(path("docs/dev-ops/mysql/sql/dsh_java.sql"))));

        Set<String> uncovered = new TreeSet<>(migrationTables());
        uncovered.removeAll(union);

        assertTrue(uncovered.isEmpty(), "迁移脚本未被两脚本覆盖: " + uncovered);
    }

    @Test
    void readmeDeclaresActualTableCount() throws Exception {
        int actual = tables(Files.readString(path("dsh-java-app/src/main/resources/schema.sql"))).size();
        assertEquals(actual, declaredCount(path("README.md"), "共\\s*(\\d+)\\s*张表"), "README.md 声明表数应与事实一致");
        assertEquals(actual, declaredCount(path("README.en.md"), "\\((\\d+)\\s+in total\\)"), "README.en.md 声明表数应与事实一致");
    }
}
