/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale;

import javax.xml.namespace.QName;

import com.querydsl.sql.SQLQuery;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.query.InOidFilter;
import com.evolveum.midpoint.repo.sqale.filtering.InOidFilterProcessor;
import com.evolveum.midpoint.repo.sqale.qmodel.SqaleTableMapping;
import com.evolveum.midpoint.repo.sqlbase.SqlQueryContext;
import com.evolveum.midpoint.repo.sqlbase.filtering.FilterProcessor;
import com.evolveum.midpoint.repo.sqlbase.mapping.QueryTableMapping;
import com.evolveum.midpoint.repo.sqlbase.querydsl.FlexibleRelationalPathBase;

public class SqaleQueryContext<S, Q extends FlexibleRelationalPathBase<R>, R>
        extends SqlQueryContext<S, Q, R> {

    public static <S, Q extends FlexibleRelationalPathBase<R>, R> SqaleQueryContext<S, Q, R> from(
            Class<S> schemaType,
            SqaleRepoContext sqlRepoContext) {

        SqaleTableMapping<S, Q, R> rootMapping = sqlRepoContext.getMappingBySchemaType(schemaType);
        Q rootPath = rootMapping.defaultAlias();
        SQLQuery<?> query = sqlRepoContext.newQuery().from(rootPath);
        // Turns on validations of aliases, does not ignore duplicate JOIN expressions,
        // we must take care of unique alias names for JOINs, which is what we want.
        query.getMetadata().setValidate(true);

        return new SqaleQueryContext<>(
                rootPath, rootMapping, sqlRepoContext, query);
    }

    private SqaleQueryContext(
            Q entityPath,
            SqaleTableMapping<S, Q, R> mapping,
            SqaleRepoContext sqlRepoContext,
            SQLQuery<?> query) {
        super(entityPath, mapping, sqlRepoContext, query);
    }

    @Override
    public SqaleRepoContext repositoryContext() {
        return (SqaleRepoContext) super.repositoryContext();
    }

    @Override
    public FilterProcessor<InOidFilter> createInOidFilter(SqlQueryContext<?, ?, ?> context) {
        return new InOidFilterProcessor(context);
    }

    public @NotNull Integer searchCachedRelationId(QName qName) {
        return repositoryContext().searchCachedRelationId(qName);
    }

    /**
     * Returns derived {@link SqaleQueryContext} for join or subquery.
     */
    @Override
    protected <TS, TQ extends FlexibleRelationalPathBase<TR>, TR> SqlQueryContext<TS, TQ, TR>
    deriveNew(TQ newPath, QueryTableMapping<TS, TQ, TR> newMapping) {
        return new SqaleQueryContext<>(
                newPath,
                (SqaleTableMapping<TS, TQ, TR>) newMapping,
                repositoryContext(),
                sqlQuery);
    }
}