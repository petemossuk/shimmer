/*
 * Copyright 2017 Open mHealth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openmhealth.shim.moves.mapper;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.openmhealth.schema.domain.omh.*;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gdata.util.common.base.Preconditions.checkArgument;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.openmhealth.shim.common.mapper.JsonNodeMappingSupport.*;


/**
 * A mapper from activity nodes in Moves Resource API <code>/user/storyline/daily</code> responses to data points.
 *
 * @author Emerson Farrugia
 * @author Jared Sieling
 * @see <a href="https://dev.moves-app.com/docs/api_storyline">API documentation</a>
 */
public abstract class MovesActivityNodeDataPointMapper<T extends SchemaSupport> extends MovesDataPointMapper<T> {

    @Override
    public List<DataPoint<T>> asDataPoints(List<JsonNode> responseNodes) {

        checkNotNull(responseNodes);
        checkArgument(responseNodes.size() == 1, "A single response node is allowed per call.");

        List<DataPoint<T>> dataPoints = Lists.newArrayList();

        JsonNode segmentNodes = asRequiredNode(responseNodes.get(0), "segments");

        for (JsonNode segmentNode : segmentNodes) {

            Optional<JsonNode> activityNodes = asOptionalNode(segmentNode, "activities");
            if (activityNodes.isPresent()) {

                for (JsonNode activityNode : activityNodes.get()) {
                    asDataPoint(activityNode).ifPresent(dataPoints::add);
                }
            }
        }

        return dataPoints;
    }

    /**
     * Creates a data point.
     *
     * @param node a node containing all the information required to build the data point
     * @return a data point
     */
    protected Optional<DataPoint<T>> asDataPoint(JsonNode node) {

        Optional<T> measure = newMeasure(node);

        if (!measure.isPresent()) {
            return empty();
        }

        DataPointAcquisitionProvenance.Builder acquisitionProvenanceBuilder =
                new DataPointAcquisitionProvenance.Builder(RESOURCE_API_SOURCE_NAME);

        Optional<DataPointModality> modality = getModality(node);
        modality.ifPresent(acquisitionProvenanceBuilder::setModality);

        DataPointAcquisitionProvenance acquisitionProvenance = acquisitionProvenanceBuilder.build();

        acquisitionProvenance.setAdditionalProperty("external_id", newExternalId(node));

        DataPointHeader header = new DataPointHeader.Builder(randomUUID().toString(), measure.get().getSchemaId())
                .setAcquisitionProvenance(acquisitionProvenance)
                .build();

        return Optional.of(new DataPoint<>(header, measure.get()));
    }

    /**
     * @param node a node containing all the information required to build the measure
     * @return a measure
     */
    protected abstract Optional<T> newMeasure(JsonNode node);

    /**
     * @param node a node containing all the information required to construct a unique identifier
     * @return a unique identifier for this node
     */
    private String newExternalId(JsonNode node) {

        String qualifier = asRequiredString(node, "activity");
        TimeFrame timeFrame = getTimeFrame(node);

        return String.format("%s-%d", qualifier, timeFrame.getTimeInterval().getStartDateTime().toEpochSecond());
    }
}