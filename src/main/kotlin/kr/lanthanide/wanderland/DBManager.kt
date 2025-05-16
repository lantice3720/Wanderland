package kr.lanthanide.wanderland

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

object DBManager {
    private val dataSource: HikariDataSource

    init {
        val config = HikariConfig().apply {
            jdbcUrl = CONFIG.jdbcUrl
            username = CONFIG.dbUsername
            password = CONFIG.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            connectionInitSql = "SET search_path TO ${CONFIG.dbMainScheme}, public;"
        }

        dataSource = HikariDataSource(config)
    }

    fun getConnection(): Connection = dataSource.connection
}