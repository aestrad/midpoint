/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.repo.sqale.operations;

import java.util.Objects;
import java.util.UUID;

import com.querydsl.core.QueryException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.api.RepoAddOptions;
import com.evolveum.midpoint.repo.sqale.ContainerValueIdGenerator;
import com.evolveum.midpoint.repo.sqale.SqaleRepoContext;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.MObjectType;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObject;
import com.evolveum.midpoint.repo.sqale.qmodel.object.QObjectMapping;
import com.evolveum.midpoint.repo.sqlbase.JdbcSession;
import com.evolveum.midpoint.repo.sqlbase.SqlRepoContext;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectType;

/*
TODO: implementation note:
 Typically I'd use "technical" dependencies in a constructor and then the object/options/result
 would be parameters of execute(). Unfortunately I don't know how to do that AND capture
 the parametric types in the operations object. I could hide it behind this object and then capture
 it in another "actual operation" object, but that does not make any sense.
 That's why the creation is with actual parameters and execute() takes technical ones (possibly
 some richer "context" object later to provide more dependencies if necessary).
 The "context" could go to construction too, but than it would be all mixed too much. Sorry.
*/
public class AddObjectOperation<S extends ObjectType, Q extends QObject<R>, R extends MObject> {

    private final PrismObject<S> object;
    private final RepoAddOptions options;
    private final OperationResult result;

    private SqlRepoContext repositoryContext;
    private Q root;
    private QObjectMapping<S, Q, R> rootMapping;
    private MObjectType objectType;

    public AddObjectOperation(@NotNull PrismObject<S> object,
            @NotNull RepoAddOptions options, @NotNull OperationResult result) {
        this.object = object;
        this.options = options;
        this.result = result;
    }

    /**
     * Inserts the object provided to the constructor and returns its OID.
     */
    public String execute(SqaleRepoContext repositoryContext)
            throws SchemaException, ObjectAlreadyExistsException {
        try {
            // TODO utilize options and result
            this.repositoryContext = repositoryContext;
            Class<S> schemaObjectClass = object.getCompileTimeClass();
            objectType = MObjectType.fromSchemaType(schemaObjectClass);
            rootMapping = repositoryContext.getMappingBySchemaType(schemaObjectClass);
            root = rootMapping.defaultAlias();

            // we don't want CID generation here, because overwrite works different then normal add

            if (object.getOid() == null) {
                return addObjectWithoutOid();
            } else if (options.isOverwrite()) {
                return overwriteObject();
            } else {
                // OID is not null, but it's not overwrite either
                return addObjectWithOid();
            }
        } catch (QueryException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PSQLException) {
                handlePostgresException((PSQLException) cause);
            }
            throw e;
        }
    }

    private String overwriteObject() {
        throw new UnsupportedOperationException(); // TODO
    }

    private String addObjectWithOid() throws SchemaException {
        long lastCid = new ContainerValueIdGenerator().generateForNewObject(object);
        try (JdbcSession jdbcSession = repositoryContext.newJdbcSession().startTransaction()) {
            S schemaObject = object.asObjectable();
            R row = rootMapping.toRowObjectWithoutFullObject(schemaObject, jdbcSession);
            row.containerIdSeq = lastCid + 1;
            rootMapping.setFullObject(row, schemaObject);

            UUID oid = jdbcSession.newInsert(root)
                    // default populate mapper ignores null, that's good, especially for objectType
                    .populate(row)
                    .executeWithKey(root.oid);

            row.objectType = objectType; // sub-entities can use it, now it's safe to set it
            rootMapping.storeRelatedEntities(row, schemaObject, jdbcSession);

            jdbcSession.commit();
            return Objects.requireNonNull(oid, "OID of inserted object can't be null")
                    .toString();
        }
    }

    private String addObjectWithoutOid() throws SchemaException {
        try (JdbcSession jdbcSession = repositoryContext.newJdbcSession().startTransaction()) {
            S schemaObject = object.asObjectable();
            R row = rootMapping.toRowObjectWithoutFullObject(schemaObject, jdbcSession);

            // first insert without full object, because we don't know the OID yet
            UUID oid = jdbcSession.newInsert(root)
                    // default populate mapper ignores null, that's good, especially for objectType
                    .populate(row)
                    .executeWithKey(root.oid);
            String oidString =
                    Objects.requireNonNull(oid, "OID of inserted object can't be null")
                            .toString();
            object.setOid(oidString);

            long lastCid = new ContainerValueIdGenerator().generateForNewObject(object);

            // now to update full object with known OID
            rootMapping.setFullObject(row, schemaObject);
            jdbcSession.newUpdate(root)
                    .set(root.fullObject, row.fullObject)
                    .set(root.containerIdSeq, lastCid + 1)
                    .where(root.oid.eq(oid))
                    .execute();

            row.oid = oid;
            row.objectType = objectType; // sub-entities can use it, now it's safe to set it
            rootMapping.storeRelatedEntities(row, schemaObject, jdbcSession);

            jdbcSession.commit();
            return oidString;
        }
    }

    /** Throws more specific exception or returns and then original exception should be rethrown. */
    private void handlePostgresException(PSQLException psqlException)
            throws ObjectAlreadyExistsException {
        String state = psqlException.getSQLState();
        String message = psqlException.getMessage();
        if (PSQLState.UNIQUE_VIOLATION.getState().equals(state)) {
            if (message.contains("m_object_oid_pkey")) {
                String oid = StringUtils.substringBetween(message, "(oid)=(", ")");
                throw new ObjectAlreadyExistsException(
                        oid != null ? "Provided OID " + oid + " already exists" : message,
                        psqlException);
            }
        }
    }
}