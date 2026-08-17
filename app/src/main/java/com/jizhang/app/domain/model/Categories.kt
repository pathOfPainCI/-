package com.jizhang.app.domain.model

/** 内置分类枚举：规则引擎/AI 输出/客户端校验共用此表 */
object Categories {
    val DEFAULT: List<String> = listOf(
        "餐饮", "交通", "购物", "日用", "娱乐", "医疗",
        "教育", "居住", "通讯", "转账红包", "收入", "其他",
    )

    fun isValid(name: String): Boolean = name in DEFAULT
}
