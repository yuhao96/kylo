package com.thinkbiganalytics.nifi.provenance;

import com.thinkbiganalytics.common.constants.KyloProcessorFlowType;
import com.thinkbiganalytics.common.constants.KyloProcessorFlowTypeRelationship;
import com.thinkbiganalytics.metadata.rest.model.nifi.NiFiFlowCacheSync;
import com.thinkbiganalytics.metadata.rest.model.nifi.NifiFlowCacheSnapshot;
import com.thinkbiganalytics.nifi.provenance.model.ActiveFlowFile;
import com.thinkbiganalytics.nifi.provenance.model.ProvenanceEventRecordDTO;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Created by sr186054 on 12/20/16.
 */
@Component
public class ProvenanceFeedLookup {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceFeedLookup.class);
    NiFiFlowCacheSync flowCache;
    private String syncId;


    private DateTime lastUpdated;

    public void updateFlowCache(NiFiFlowCacheSync updates) {
        if (!updates.isEmpty()) {
            if (flowCache == null) {
                flowCache = updates;
            } else {
                flowCache.getSnapshot().update(updates.getSnapshot());
            }
            lastUpdated = updates.getLastSync();
        }
    }

    public Integer getProcessorIdMapSize() {
        return getFlowCache().getAddProcessorIdToFeedNameMap().size();
    }

    public Map<String, Set<KyloProcessorFlowTypeRelationship>> getFeedProcessorFlowTypes(String feedName) {
        return getFlowCache().getFeedToProcessorIdToFlowTypeMap().get(feedName);
    }


    public static DateTime convertToUTC(DateTime date) {
        DateTimeZone dtZone = DateTimeZone.forID("UTC");
        DateTime utc = date.withZone(dtZone);
        return new DateTime(utc);
    }

    private String getSyncId() {
        if (!flowCache.isEmpty()) {
            return flowCache.getSyncId();
        } else {
            return null;
        }
    }


    private NifiFlowCacheSnapshot getFlowCache() {
        if (flowCache == null || flowCache.getSnapshot() == null) {
            return NifiFlowCacheSnapshot.EMPTY;
        } else {
            return flowCache.getSnapshot();
        }
    }

    private String getFeedName(String processorId) {
        return getFlowCache().getAddProcessorIdToFeedNameMap().get(processorId);
    }

    private String getFeedProcessGroupId(String processorId) {
        return getFlowCache().getAddProcessorIdToFeedProcessGroupId().get(processorId);
    }

    public String getProcessorName(String processorId) {
        return getFlowCache().getAddProcessorIdToProcessorName().get(processorId);
    }

    public KyloProcessorFlowType setProcessorFlowType(ProvenanceEventRecordDTO event) {

        Map<String, KyloProcessorFlowType> flowTypes = getFlowCache().getProcessorFlowTypesAsMap(event.getFeedName(), event.getComponentId());

        KyloProcessorFlowType allType = flowTypes.get(KyloProcessorFlowTypeRelationship.ALL_RELATIONSHIP);
        KyloProcessorFlowType failureType = flowTypes.get(KyloProcessorFlowTypeRelationship.FAILURE_RELATIONSHIP);
        KyloProcessorFlowType successType = flowTypes.get(KyloProcessorFlowTypeRelationship.SUCCESS_RELATIONSHIP);
        KyloProcessorFlowType type = KyloProcessorFlowType.NORMAL_FLOW;

        //if the event is a failure, check to see if this processor was registered as
        if (event.isTerminatedByFailureRelationship()) {
            if (failureType != null) {
                type = failureType;
            } else if (allType != null) {
                type = allType;
            } else if (!getFlowCache().hasProcessorFlowTypesMapped(event.getFeedName())) {
                //If no processors are mapped for this feed and we got here via a failure event, then mark it as a failure
                type = failureType;
            }

        } else {
            if (successType != null) {
                type = successType;
            } else if (allType != null) {
                type = allType;
            }
        }
        event.setProcessorType(type);
        log.debug("Setting the Flow Type as {} for Processor {} ({}) on Feed {}.  Flow Types: {} ", type, event.getComponentName(), event.getComponentId(), event.getFeedName(), flowTypes);
        return type;
    }

    public boolean isFailureEvent(ProvenanceEventRecordDTO eventRecordDTO) {
        KyloProcessorFlowType processorFlowType = eventRecordDTO.getProcessorType();
        if (processorFlowType == null) {
            processorFlowType = setProcessorFlowType(eventRecordDTO);
        }
        ;
        if (processorFlowType != null) {
            return KyloProcessorFlowType.FAILURE.equals(processorFlowType);
        } else {
            return false;
        }
    }

    public boolean assignFeedInformationToFlowFile(ActiveFlowFile flowFile) {
        boolean assigned = false;
        if (!flowFile.hasFeedInformationAssigned() && flowFile.getRootFlowFile() != null) {
            if (flowFile.getRootFlowFile().hasFeedInformationAssigned()) {
                flowFile.assignFeedInformation(flowFile.getRootFlowFile().getFeedName(), flowFile.getRootFlowFile().getFeedProcessGroupId());
                assigned = true;
            }
            if (!flowFile.hasFeedInformationAssigned()) {
                //todo check for nulls
                String feedName = getFeedName(flowFile.getFirstEvent().getComponentId());
                String processGroupId = getFeedProcessGroupId(flowFile.getFirstEvent().getComponentId());
                flowFile.assignFeedInformation(feedName, processGroupId);
                flowFile.getRootFlowFile().assignFeedInformation(feedName, processGroupId);
                assigned = true;
            }
        }
        return assigned;
    }

    public boolean isStream(ProvenanceEventRecordDTO eventRecordDTO) {
        return getFlowCache().getAddStreamingFeeds().contains(eventRecordDTO.getFeedName());
    }

}
