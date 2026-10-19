package com.sqlquery.model;

/**
 * 一次"列 vs 字面量"的比较，用于隐式转换判断。
 */
public record ColumnLiteral(ColumnRef column, LiteralKind literalKind) {
}
