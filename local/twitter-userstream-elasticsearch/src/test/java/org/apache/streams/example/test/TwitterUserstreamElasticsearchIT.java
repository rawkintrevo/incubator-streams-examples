/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.streams.example.test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import org.apache.streams.config.ComponentConfigurator;
import org.apache.streams.elasticsearch.ElasticsearchClientManager;
import org.apache.streams.example.TwitterUserstreamElasticsearch;
import org.apache.streams.example.TwitterUserstreamElasticsearchConfiguration;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/**
 * Test copying documents between two indexes on same cluster
 */
public class TwitterUserstreamElasticsearchIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(TwitterUserstreamElasticsearchIT.class);

    protected TwitterUserstreamElasticsearchConfiguration testConfiguration;
    protected Client testClient;

    private int count = 0;

    @Before
    public void prepareTest() throws Exception {

        Config reference  = ConfigFactory.load();
        File conf_file = new File("target/test-classes/TwitterUserstreamElasticsearchIT.conf");
        assert(conf_file.exists());
        Config testResourceConfig  = ConfigFactory.parseFileAnySyntax(conf_file, ConfigParseOptions.defaults().setAllowMissing(false));
        Config typesafe  = testResourceConfig.withFallback(reference).resolve();
        testConfiguration = new ComponentConfigurator<>(TwitterUserstreamElasticsearchConfiguration.class).detectConfiguration(typesafe);
        testClient = new ElasticsearchClientManager(testConfiguration.getElasticsearch()).getClient();

        ClusterHealthRequest clusterHealthRequest = Requests.clusterHealthRequest();
        ClusterHealthResponse clusterHealthResponse = testClient.admin().cluster().health(clusterHealthRequest).actionGet();
        assertNotEquals(clusterHealthResponse.getStatus(), ClusterHealthStatus.RED);

        IndicesExistsRequest indicesExistsRequest = Requests.indicesExistsRequest(testConfiguration.getElasticsearch().getIndex());
        IndicesExistsResponse indicesExistsResponse = testClient.admin().indices().exists(indicesExistsRequest).actionGet();
        assertFalse(indicesExistsResponse.isExists());

    }

    @Test
    public void testUserstreamElasticsearch() throws Exception {

        TwitterUserstreamElasticsearch stream = new TwitterUserstreamElasticsearch(testConfiguration);

        Thread thread = new Thread(stream);
        thread.start();
        thread.join(30000);

        // assert lines in file
        SearchRequestBuilder countRequest = testClient
                .prepareSearch(testConfiguration.getElasticsearch().getIndex())
                .setTypes(testConfiguration.getElasticsearch().getType());
        SearchResponse countResponse = countRequest.execute().actionGet();

        count = (int)countResponse.getHits().getTotalHits();

        assertNotEquals(count, 0);
    }
}
