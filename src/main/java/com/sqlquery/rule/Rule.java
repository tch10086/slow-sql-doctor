package com.sqlquery.rule;

import java.util.List;

public interface Rule {

    String id();

    String title();

    Severity severity();

    List<Issue> check(QueryContext context);
}
