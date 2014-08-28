/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.quality;

import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.quality.PrecisionAtN.Rating;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class PrecisionAtRequest extends ActionRequest<PrecisionAtRequest> {

    /** The request data to use for evaluation. */
    private PrecisionTask task;
    
    @Override
    public ActionRequestValidationException validate() {
        // TODO
        return null;
    }

    /** Returns the specification of this qa run including intents to execute, specifications detailing intent translation and metrics
     * to compute. */
    public PrecisionTask getTask() {
        return task;
    }

    /** Returns the specification of this qa run including intents to execute, specifications detailing intent translation and metrics
     * to compute. */
    public void setTask(PrecisionTask task) {
        this.task = task;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        Collection<Intent<Rating>> intents = new ArrayList<>();
        int intentSize = in.readInt();
        for (int i = 0; i < intentSize; i++) {
            Intent<Rating> intent = new Intent<Rating>();
            intent.setIntentId(in.readInt());
            intent.setIntentParameters((Map<String, String>) in.readGenericValue());
            intent.setRatedDocuments((Map<String, Rating>) in.readGenericValue());
            intents.add(intent);
        }
        
        Collection<Specification> specs = new ArrayList<Specification>();
        int specSize = in.readInt();
        for (int i = 0; i < specSize; i++) {

            Specification spec = new Specification();
            spec.setTargetIndex(in.readString());
            SearchRequest searchRequest = new SearchRequest();
            searchRequest.readFrom(in);
            spec.setTemplatedSearchRequest(searchRequest);
            spec.setFilter(in.readBytesReference());
            spec.setSpecId(in.readInt());
            specs.add(spec);
        }
        
        this.task = new PrecisionTask();
        task.setIntents(intents);
        task.setSpecifications(specs);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Collection<Intent<Rating>> intents = task.getIntents();
        Collection<Specification> specs = task.getSpecifications();
        
        out.writeInt(intents.size());
        for (Intent<Rating> intent : intents) {
            out.writeInt(intent.getIntentId());
            out.writeGenericValue(intent.getIntentParameters());
            out.writeGenericValue(intent.getRatedDocuments());
        }
        
        out.writeInt(specs.size());
        for (Specification spec : specs) {
            out.writeString(spec.getTargetIndex());
            spec.getTemplatedSearchRequest().writeTo(out);
            out.writeBytesReference(spec.getFilter());
            out.writeInt(spec.getSpecId());
        }
    }
}
