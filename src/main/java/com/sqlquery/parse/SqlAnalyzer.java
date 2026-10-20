package com.sqlquery.parse;

import com.sqlquery.model.ColumnLiteral;
import com.sqlquery.model.ColumnRef;
import com.sqlquery.model.LiteralKind;
import com.sqlquery.model.SqlFeatures;
import com.sqlquery.model.TableRef;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// 只处理单条 SELECT。子查询、UNION 暂时不做，遇到就抛 UnsupportedSqlException。
public final class SqlAnalyzer {

    public SqlFeatures analyze(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof PlainSelect select)) {
                throw new UnsupportedSqlException(
                        "Only a single SELECT (PlainSelect) is supported: " + statement.getClass().getSimpleName());
            }
            return analyze(select);
        } catch (UnsupportedSqlException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedSqlException("Cannot parse SQL: " + e.getMessage(), e);
        }
    }

    private SqlFeatures analyze(PlainSelect select) {
        SqlFeatures features = new SqlFeatures(select.toString());

        addTables(select, features);
        addSelectColumns(select, features);
        addWhere(select, features);
        addJoins(select, features);
        addOrderAndGroup(select, features);
        addLimit(select, features);

        return features;
    }

    private void addTables(PlainSelect select, SqlFeatures features) {
        addFromItem(select.getFromItem(), features);
        if (select.getJoins() != null) {
            for (Join join : select.getJoins()) {
                addFromItem(join.getRightItem(), features);
            }
        }
    }

    private void addFromItem(FromItem item, SqlFeatures features) {
        if (item instanceof Table table) {
            Alias alias = item.getAlias();
            features.addTable(new TableRef(table.getName(), alias == null ? null : alias.getName()));
        }
    }

    private void addSelectColumns(PlainSelect select, SqlFeatures features) {
        for (SelectItem<?> item : select.getSelectItems()) {
            Expression expression = item.getExpression();
            if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
                features.setSelectStar(true);
            }
            collectColumns(expression, features, features::addSelectColumn);
        }
    }

    private void addWhere(PlainSelect select, SqlFeatures features) {
        Expression where = select.getWhere();
        if (where == null) {
            return;
        }
        List<Expression> conjuncts = new ArrayList<>();
        flattenAnd(where, conjuncts);
        for (Expression conjunct : conjuncts) {
            detectFunctions(conjunct, features);
            classify(conjunct, features);
        }
    }

    private void flattenAnd(Expression expression, List<Expression> out) {
        if (expression instanceof AndExpression and) {
            flattenAnd(and.getLeftExpression(), out);
            flattenAnd(and.getRightExpression(), out);
        } else if (expression instanceof Parenthesis parenthesis) {
            flattenAnd(parenthesis.getExpression(), out);
        } else if (expression instanceof InExpression in
                && in.getRightExpression() instanceof AndExpression and) {
            // JSqlParser 5.3 的一个解析怪癖：
            // "a IN (1,2) AND b > 3" 会把 AND 右侧塞进 IN 的 rightExpression。
            // 这里把 AST 修回 "a IN (1,2)" + "b > 3" 两个合取项。
            in.setRightExpression(and.getLeftExpression());
            out.add(in);
            flattenAnd(and.getRightExpression(), out);
        } else {
            out.add(expression);
        }
    }

    private void classify(Expression expression, SqlFeatures features) {
        if (expression instanceof OrExpression) {
            features.setHasOr(true);
            return;
        }
        if (expression instanceof EqualsTo equalsTo) {
            handleComparison(equalsTo, features);
            return;
        }
        if (expression instanceof InExpression in) {
            handleIn(in, features);
            return;
        }
        if (expression instanceof Between between) {
            ColumnRef column = column(between.getLeftExpression(), features);
            if (column != null) {
                features.addRangeColumn(column);
                features.addComparison(column, literalKind(between.getBetweenExpressionStart()));
            }
            return;
        }
        if (expression instanceof GreaterThan || expression instanceof GreaterThanEquals
                || expression instanceof MinorThan || expression instanceof MinorThanEquals) {
            BinaryExpression binary = (BinaryExpression) expression;
            ColumnRef column = column(binary.getLeftExpression(), features);
            if (column != null) {
                features.addRangeColumn(column);
                features.addComparison(column, literalKind(binary.getRightExpression()));
            }
            return;
        }
        if (expression instanceof LikeExpression like) {
            ColumnRef column = column(like.getLeftExpression(), features);
            if (column != null) {
                Expression right = like.getRightExpression();
                if (right instanceof StringValue value && value.getValue().startsWith("%")) {
                    features.setLeadingWildcard(true);
                } else {
                    features.addRangeColumn(column);
                }
            }
            return;
        }
        if (expression instanceof IsNullExpression isNull) {
            ColumnRef column = column(isNull.getLeftExpression(), features);
            if (column != null) {
                features.addEqColumn(column);
            }
        }
    }

    private void handleComparison(BinaryExpression expression, SqlFeatures features) {
        ColumnRef left = column(expression.getLeftExpression(), features);
        ColumnRef right = column(expression.getRightExpression(), features);

        if (left != null && right == null) {
            features.addEqColumn(left);
            features.addComparison(left, literalKind(expression.getRightExpression()));
        } else if (right != null && left == null) {
            features.addEqColumn(right);
            features.addComparison(right, literalKind(expression.getLeftExpression()));
        }
    }

    private void handleIn(InExpression in, SqlFeatures features) {
        ColumnRef column = column(in.getLeftExpression(), features);
        if (column == null) {
            return;
        }
        features.addEqColumn(column);

        if (in.getRightExpression() instanceof ExpressionList<?> list && !list.isEmpty()) {
            features.addComparison(column, literalKind(list.get(0)));
        } else {
            features.addComparison(column, LiteralKind.OTHER);
        }
    }

    private void addJoins(PlainSelect select, SqlFeatures features) {
        if (select.getJoins() == null) {
            return;
        }
        for (Join join : select.getJoins()) {
            Collection<Expression> onExpressions = join.getOnExpressions();
            if (onExpressions == null || onExpressions.isEmpty()) {
                features.setCartesianJoin(true);
                continue;
            }

            for (Expression on : onExpressions) {                for (EqualsTo equalsTo : equalities(on)) {
                    ColumnRef left = column(equalsTo.getLeftExpression(), features);
                    ColumnRef right = column(equalsTo.getRightExpression(), features);

                    // JOIN 两侧的关联列都记录下来：
                    // 哪张表是被驱动表由执行计划决定，索引建议阶段再取舍。
                    if (left != null) {
                        features.addJoinColumn(left);
                    }
                    if (right != null) {
                        features.addJoinColumn(right);
                    }
                }
            }
        }
    }

    private Set<String> tableNames(FromItem item, SqlFeatures features) {
        if (item instanceof Table table) {
            String real = features.resolveTable(table.getName());
            return real == null ? Collections.emptySet() : Set.of(real);
        }
        return Collections.emptySet();
    }

    private List<EqualsTo> equalities(Expression expression) {
        List<Expression> conjuncts = new ArrayList<>();
        flattenAnd(expression, conjuncts);
        List<EqualsTo> result = new ArrayList<>();
        for (Expression conjunct : conjuncts) {
            if (conjunct instanceof EqualsTo equalsTo) {
                result.add(equalsTo);
            }
        }
        return result;
    }

    private void addOrderAndGroup(PlainSelect select, SqlFeatures features) {
        if (select.getOrderByElements() != null) {
            for (OrderByElement orderBy : select.getOrderByElements()) {
                collectColumns(orderBy.getExpression(), features, features::addOrderColumn);
            }
        }
        if (select.getGroupBy() != null) {
            collectColumns(select.getGroupBy().getGroupByExpressionList(), features, features::addGroupColumn);
        }
    }

    private void addLimit(PlainSelect select, SqlFeatures features) {
        if (select.getLimit() != null) {
            features.setLimitRows(asLong(select.getLimit().getRowCount()));
            features.setLimitOffset(asLong(select.getLimit().getOffset()));
        }
        if (features.getLimitOffset() == null && select.getOffset() != null) {
            features.setLimitOffset(asLong(select.getOffset().getOffset()));
        }
    }

    private void collectColumns(Expression expression, SqlFeatures features, Consumer<ColumnRef> sink) {
        if (expression == null) {
            return;
        }
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Column column, S context) {
                ColumnRef ref = resolve(column, features);
                if (ref != null) {
                    sink.accept(ref);
                }
                return null;
            }
        }, null);
    }

    private void detectFunctions(Expression expression, SqlFeatures features) {
        expression.accept(new ExpressionVisitorAdapter<Void>() {
            @Override
            public <S> Void visit(Function function, S context) {
                ExpressionList<?> parameters = function.getParameters();
                if (parameters != null) {
                    for (Expression parameter : parameters) {
                        if (parameter instanceof Column column) {
                            ColumnRef ref = resolve(column, features);
                            if (ref != null) {
                                features.addFunctionColumn(ref);
                            }
                        }
                    }
                }
                return super.visit(function, context);
            }
        }, null);
    }

    private ColumnRef column(Expression expression, SqlFeatures features) {
        if (expression instanceof Column column) {
            return resolve(column, features);
        }
        if (expression instanceof Parenthesis parenthesis) {
            return column(parenthesis.getExpression(), features);
        }
        return null;
    }

    private ColumnRef resolve(Column column, SqlFeatures features) {
        String qualifier = column.getTable() == null ? null : column.getTable().getName();
        if (qualifier != null) {
            String real = features.resolveTable(qualifier);
            return new ColumnRef(real == null ? qualifier : real, column.getColumnName());
        }
        if (features.getTables().size() == 1) {
            return new ColumnRef(features.getTables().get(0).tableName(), column.getColumnName());
        }
        return new ColumnRef(null, column.getColumnName());
    }

    private LiteralKind literalKind(Expression expression) {
        if (expression instanceof LongValue || expression instanceof net.sf.jsqlparser.expression.DoubleValue) {
            return LiteralKind.NUMBER;
        }
        if (expression instanceof StringValue) {
            return LiteralKind.STRING;
        }
        return LiteralKind.OTHER;
    }

    private Long asLong(Expression expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof LongValue value) {
            return value.getValue();
        }
        try {
            return Long.parseLong(expression.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
