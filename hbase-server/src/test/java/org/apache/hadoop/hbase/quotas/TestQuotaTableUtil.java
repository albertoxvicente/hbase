/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.quotas;

import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.protobuf.ProtobufUtil;
import org.apache.hadoop.hbase.protobuf.generated.QuotaProtos.Quotas;
import org.apache.hadoop.hbase.protobuf.generated.QuotaProtos.Throttle;
import org.apache.hadoop.hbase.MediumTests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * Test the quota table helpers (e.g. CRUD operations)
 */
@Category(MediumTests.class)
public class TestQuotaTableUtil {
  final Log LOG = LogFactory.getLog(getClass());

  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TEST_UTIL.getConfiguration().setBoolean(QuotaUtil.QUOTA_CONF_KEY, true);
    TEST_UTIL.getConfiguration().setInt(QuotaCache.REFRESH_CONF_KEY, 2000);
    TEST_UTIL.getConfiguration().setInt("hbase.hstore.compactionThreshold", 10);
    TEST_UTIL.getConfiguration().setInt("hbase.regionserver.msginterval", 100);
    TEST_UTIL.getConfiguration().setInt("hbase.client.pause", 250);
    TEST_UTIL.getConfiguration().setInt(HConstants.HBASE_CLIENT_RETRIES_NUMBER, 6);
    TEST_UTIL.getConfiguration().setBoolean("hbase.master.enabletable.roundrobin", true);
    TEST_UTIL.startMiniCluster(1);
    TEST_UTIL.waitTableAvailable(QuotaTableUtil.QUOTA_TABLE_NAME);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Test
  public void testTableQuotaUtil() throws Exception {
    final TableName table = TableName.valueOf("testTableQuotaUtilTable");

    Quotas quota = Quotas.newBuilder()
              .setThrottle(Throttle.newBuilder()
                .setReqNum(ProtobufUtil.toTimedQuota(1000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setWriteNum(ProtobufUtil.toTimedQuota(600, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setReadSize(ProtobufUtil.toTimedQuota(8192, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .build())
              .build();

    // Add user quota and verify it
    QuotaUtil.addTableQuota(TEST_UTIL.getConfiguration(), table, quota);
    Quotas resQuota = QuotaUtil.getTableQuota(TEST_UTIL.getConfiguration(), table);
    assertEquals(quota, resQuota);

    // Remove user quota and verify it
    QuotaUtil.deleteTableQuota(TEST_UTIL.getConfiguration(), table);
    resQuota = QuotaUtil.getTableQuota(TEST_UTIL.getConfiguration(), table);
    assertEquals(null, resQuota);
  }

  @Test
  public void testNamespaceQuotaUtil() throws Exception {
    final String namespace = "testNamespaceQuotaUtilNS";

    Quotas quota = Quotas.newBuilder()
              .setThrottle(Throttle.newBuilder()
                .setReqNum(ProtobufUtil.toTimedQuota(1000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setWriteNum(ProtobufUtil.toTimedQuota(600, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setReadSize(ProtobufUtil.toTimedQuota(8192, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .build())
              .build();

    // Add user quota and verify it
    QuotaUtil.addNamespaceQuota(TEST_UTIL.getConfiguration(), namespace, quota);
    Quotas resQuota = QuotaUtil.getNamespaceQuota(TEST_UTIL.getConfiguration(), namespace);
    assertEquals(quota, resQuota);

    // Remove user quota and verify it
    QuotaUtil.deleteNamespaceQuota(TEST_UTIL.getConfiguration(), namespace);
    resQuota = QuotaUtil.getNamespaceQuota(TEST_UTIL.getConfiguration(), namespace);
    assertEquals(null, resQuota);
  }

  @Test
  public void testUserQuotaUtil() throws Exception {
    final TableName table = TableName.valueOf("testUserQuotaUtilTable");
    final String namespace = "testNS";
    final String user = "testUser";

    Quotas quotaNamespace = Quotas.newBuilder()
              .setThrottle(Throttle.newBuilder()
                .setReqNum(ProtobufUtil.toTimedQuota(50000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .build())
              .build();
    Quotas quotaTable = Quotas.newBuilder()
              .setThrottle(Throttle.newBuilder()
                .setReqNum(ProtobufUtil.toTimedQuota(1000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setWriteNum(ProtobufUtil.toTimedQuota(600, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setReadSize(ProtobufUtil.toTimedQuota(10000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .build())
              .build();
    Quotas quota = Quotas.newBuilder()
              .setThrottle(Throttle.newBuilder()
                .setReqSize(ProtobufUtil.toTimedQuota(8192, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setWriteSize(ProtobufUtil.toTimedQuota(4096, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .setReadNum(ProtobufUtil.toTimedQuota(1000, TimeUnit.SECONDS, QuotaScope.MACHINE))
                .build())
              .build();

    // Add user global quota
    QuotaUtil.addUserQuota(TEST_UTIL.getConfiguration(), user, quota);
    Quotas resQuota = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user);
    assertEquals(quota, resQuota);

    // Add user quota for table
    QuotaUtil.addUserQuota(TEST_UTIL.getConfiguration(), user, table, quotaTable);
    Quotas resQuotaTable = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user, table);
    assertEquals(quotaTable, resQuotaTable);

    // Add user quota for namespace
    QuotaUtil.addUserQuota(TEST_UTIL.getConfiguration(), user, namespace, quotaNamespace);
    Quotas resQuotaNS = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user, namespace);
    assertEquals(quotaNamespace, resQuotaNS);

    // Delete user global quota
    QuotaUtil.deleteUserQuota(TEST_UTIL.getConfiguration(), user);
    resQuota = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user);
    assertEquals(null, resQuota);

    // Delete user quota for table
    QuotaUtil.deleteUserQuota(TEST_UTIL.getConfiguration(), user, table);
    resQuotaTable = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user, table);
    assertEquals(null, resQuotaTable);

    // Delete user quota for namespace
    QuotaUtil.deleteUserQuota(TEST_UTIL.getConfiguration(), user, namespace);
    resQuotaNS = QuotaUtil.getUserQuota(TEST_UTIL.getConfiguration(), user, namespace);
    assertEquals(null, resQuotaNS);
  }
}