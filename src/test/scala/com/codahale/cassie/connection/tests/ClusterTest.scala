package com.codahale.cassie.connection.tests

import org.scalatest.matchers.MustMatchers
import org.mockito.Mockito.{when, verify, times}
import org.scalatest.{BeforeAndAfterAll, Spec}
import com.codahale.cassie.tests.util.MockCassandraServer
import com.codahale.cassie.connection.Cluster
import java.net.InetSocketAddress
import org.apache.cassandra.thrift.{ColumnPath, TimedOutException, ConsistencyLevel, ColumnOrSuperColumn}

class ClusterTest extends Spec with MustMatchers with BeforeAndAfterAll {
  val cp = new ColumnPath("cf")
  val cosc = new ColumnOrSuperColumn

  val server1 = new MockCassandraServer(MockCassandraServer.choosePort())
  when(server1.cassandra.describe_version).thenReturn("node1")
  when(server1.cassandra.get("ks", "key", cp, ConsistencyLevel.ALL)).thenReturn(cosc)

  val server2 = new MockCassandraServer(MockCassandraServer.choosePort())
  when(server2.cassandra.describe_version).thenReturn("node2")
  when(server2.cassandra.get("ks", "key", cp, ConsistencyLevel.ALL)).thenReturn(cosc)

  val server3 = new MockCassandraServer(MockCassandraServer.choosePort())
  when(server3.cassandra.describe_version).thenReturn("node3")
  when(server3.cassandra.get("ks", "key", cp, ConsistencyLevel.ALL)).thenThrow(new TimedOutException())

  val hosts = Set(
    new InetSocketAddress("127.0.0.1", server1.port),
    new InetSocketAddress("127.0.0.1", server2.port),
    new InetSocketAddress("127.0.0.1", server3.port)
  )

  override protected def beforeAll() {
    server1.start()
    server2.start()
    server3.start()
  }

  override protected def afterAll() {
    server1.stop()
    server2.stop()
    server3.stop()
  }

  describe("a cluster") {
    val cluster = new Cluster(hosts, 3, 2, 1000, 2, 5, 10*60*1000)

    it("balances requests between nodes") {
      cluster.map { c => c.describe_version } must equal("node1")
      cluster.map { c => c.describe_version } must equal("node2")
      cluster.map { c => c.describe_version } must equal("node3")
      cluster.map { c => c.describe_version } must equal("node1")
      cluster.map { c => c.describe_version } must equal("node2")
      cluster.map { c => c.describe_version } must equal("node3")

    }

    it("handles nodes which are down gracefully") {
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)
      cluster.map { c => c.get("ks", "key", cp, ConsistencyLevel.ALL) } must equal(cosc)

      verify(server1.cassandra, times(5)).get("ks", "key", cp, ConsistencyLevel.ALL)
      verify(server2.cassandra, times(5)).get("ks", "key", cp, ConsistencyLevel.ALL)
      verify(server3.cassandra, times(2)).get("ks", "key", cp, ConsistencyLevel.ALL)
    }
  }
}