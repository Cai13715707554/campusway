package com.campusway.core.data

import kotlinx.serialization.json.Json

/**
 * 全应用统一的 JSON 配置。
 *
 * 关于容错策略：导入的课表/校园数据来自用户手写或第三方导出，
 * 难免出现多字段、缺字段、类型不一致。这里打开 `ignoreUnknownKeys`
 * 与 `isLenient`，让导入尽量成功，再由各自的 `validate()` 给出具体问题，
 * 而不是直接抛异常让用户无从下手。
 */
object JsonConfig {

    val instance: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** 紧凑输出，用于写入 DataStore 这类对体积敏感的场景。 */
    val compact: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
}
