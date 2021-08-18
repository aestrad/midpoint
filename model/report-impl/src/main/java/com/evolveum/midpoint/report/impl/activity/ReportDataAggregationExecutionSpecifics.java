/*
 * Copyright (C) 2010-2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.report.impl.activity;

import java.util.List;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.repo.common.task.*;
import com.evolveum.midpoint.report.impl.ReportUtils;
import com.evolveum.midpoint.report.impl.controller.ExportedReportDataRow;
import com.evolveum.midpoint.report.impl.controller.ExportedReportHeaderRow;
import com.evolveum.midpoint.report.impl.controller.ReportDataWriter;
import com.evolveum.midpoint.task.api.RunningTask;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

import com.evolveum.midpoint.xml.ns._public.common.common_3.FileFormatTypeType;

import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.repo.common.activity.ActivityExecutionException;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.CommonException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ReportDataType;

class ReportDataAggregationExecutionSpecifics
        extends BaseSearchBasedExecutionSpecificsImpl
        <ReportDataType,
                DistributedReportExportWorkDefinition,
                DistributedReportExportActivityHandler> {

    private static final Trace LOGGER = TraceManager.getTrace(ReportDataAggregationExecutionSpecifics.class);

    /** Helper functionality. */
    @NotNull private final DistributedReportExportActivitySupport support;

    /**
     * Data from all the partial reports.
     *
     * TODO eliminate gathering in memory: write to a file immediately after getting the data.
     */
    private final StringBuilder aggregatedData = new StringBuilder();

    /**
     * Data writer which completize context of report.
     */
    private ReportDataWriter<ExportedReportDataRow, ExportedReportHeaderRow> dataWriter;

    ReportDataAggregationExecutionSpecifics(@NotNull SearchBasedActivityExecution<ReportDataType,
            DistributedReportExportWorkDefinition, DistributedReportExportActivityHandler, ?> activityExecution) {
        super(activityExecution);
        support = new DistributedReportExportActivitySupport(activityExecution);
    }

    @Override
    public void beforeExecution(OperationResult result) throws CommonException, ActivityExecutionException {
        support.beforeExecution(result);

        dataWriter = ReportUtils.createDataWriter(
                support.getReport(), FileFormatTypeType.CSV, getActivityHandler().reportService, support.getCompiledCollectionView(result));
    }

    @Override
    @NotNull
    public SearchSpecification<ReportDataType> createSearchSpecification(OperationResult result) {
        // FIXME When parent OID is indexed, the query can be improved
        // FIXME Also when sequenceNumber is indexed, we'll sort on it
        String prefix = String.format("Partial report data for [%s]", support.getGlobalReportDataRef().getOid());
        return new SearchSpecification<>(
                ReportDataType.class,
                PrismContext.get().queryFor(ReportDataType.class)
                        .item(ReportDataType.F_NAME).startsWithPoly(prefix)
                        .asc(ReportDataType.F_NAME)
                        .build(),
                List.of(),
                true);
    }

    @Override
    public boolean processObject(@NotNull PrismObject<ReportDataType> reportData,
            @NotNull ItemProcessingRequest<PrismObject<ReportDataType>> request, RunningTask workerTask, OperationResult result)
            throws CommonException, ActivityExecutionException {
        LOGGER.info("Appending data from {} (and deleting the object)", reportData);
        aggregatedData.append(reportData.asObjectable().getData());
        getActivityHandler().commonTaskBeans.repositoryService.deleteObject(ReportDataType.class, reportData.getOid(), result);
        return true;
    }

    @Override
    public void afterExecution(OperationResult result) throws CommonException, ActivityExecutionException {
        support.saveReportFile(aggregatedData.toString(), dataWriter, result);
    }
}