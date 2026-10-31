package com.workeasy.erp

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

/**
 * 事务探针：确认交易域（JdbcTemplate）写入是否落在同一个物理事务里。
 *
 * 结论决定阶段二的方案（见 DOCS/下一阶段开发计划.md §3.3）：
 * - 回滚成功 → 维持现状，JPA 事务管理器可覆盖 JdbcTemplate/MyBatis；
 * - 回滚失败 → 必须显式声明 dataSourceTransactionManager，并给 JDBC 服务指定事务管理器。
 *
 * 需要本地 PostgreSQL（docker compose up -d workeasy-pg）。
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionProbeTest {

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    @Autowired
    private lateinit var dataSource: DataSource

    @BeforeAll
    fun createProbeTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tx_probe (id BIGSERIAL PRIMARY KEY, note VARCHAR(50))")
    }

    @AfterAll
    fun dropProbeTable() {
        jdbc.execute("DROP TABLE IF EXISTS tx_probe")
    }

    /** 探针一：当前应用默认事务管理器（JPA）内，JdbcTemplate 写入必须一起回滚 */
    @Test
    fun `JPA 事务管理器下 JdbcTemplate 写入回滚`() {
        assertTrue(
            txManager is JpaTransactionManager,
            "预期默认事务管理器为 JpaTransactionManager，实际为 ${txManager.javaClass.name}",
        )
        assertRolledBack(TransactionTemplate(txManager), "jpa-tx")
    }

    /** 探针二（对照）：显式 JDBC 事务管理器内同样必须回滚 */
    @Test
    fun `JdbcTransactionManager 下写入回滚`() {
        assertRolledBack(TransactionTemplate(JdbcTransactionManager(dataSource)), "jdbc-tx")
    }

    /** 探针三（对照）：无事务时写入立即生效，证明探针本身能观察到已提交数据 */
    @Test
    fun `无事务时写入提交`() {
        jdbc.update("DELETE FROM tx_probe WHERE note = ?", "no-tx")
        jdbc.update("INSERT INTO tx_probe(note) VALUES (?)", "no-tx")
        assertEquals(1, countOf("no-tx"), "无事务写入应立即可见")
        jdbc.update("DELETE FROM tx_probe WHERE note = ?", "no-tx")
    }

    private fun assertRolledBack(template: TransactionTemplate, note: String) {
        jdbc.update("DELETE FROM tx_probe WHERE note = ?", note)
        assertThrows(IllegalStateException::class.java) {
            template.executeWithoutResult {
                jdbc.update("INSERT INTO tx_probe(note) VALUES (?)", note)
                throw IllegalStateException("探针主动抛异常，触发回滚")
            }
        }
        assertEquals(0, countOf(note), "事务回滚后不应残留数据（否则说明 JDBC 写入未加入该事务）")
    }

    private fun countOf(note: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM tx_probe WHERE note = ?", Int::class.java, note) ?: 0
}
