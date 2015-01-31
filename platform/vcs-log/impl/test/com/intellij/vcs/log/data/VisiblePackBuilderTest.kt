/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.data

import org.junit.Test
import com.intellij.mock.MockVirtualFile
import com.intellij.vcs.log.impl.TestVcsLogProvider
import com.intellij.vcs.log.impl.TestVcsLogProvider.*
import com.intellij.vcs.log.VcsLogHashMap
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.Disposable
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import java.util.HashMap
import com.intellij.vcs.log.graph.GraphCommit
import java.util.ArrayList
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.VcsRef
import java.util.HashSet
import com.intellij.vcs.log.impl.VcsRefImpl
import com.intellij.vcs.log.VcsLogFilterCollection
import kotlin.test.assertEquals
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcs.log.ui.filter.VcsLogUserFilterImpl
import kotlin.test.assertTrue
import com.intellij.vcs.log.graph.VisibleGraph

class VisiblePackBuilderTest {

  Test fun `no filters`() {
    val graph = graph {
      1(2) *"master"
      2(3)
      3(4)
      4()
    }
    val visiblePack = graph.build(filters())
    assertEquals(4, visiblePack.getVisibleGraph().getVisibleCommitCount())
  }

  Test fun `branch filter`() {
    val graph = graph {
      1(3) *"master"
      2(3) *"feature"
      3(4)
      4()
    }
    val visiblePack = graph.build(filters(branch = listOf("master")))
    assertEquals(3, visiblePack.getVisibleGraph().getVisibleCommitCount())
  }

  Test fun `filter by user in memory`() {
    val graph = graph {
      1(2) *"master"
      2(3)
      3(4)           +"bob.doe"
      4(5)
      5(6)
      6(7)
      7()
    }
    val visiblePack = graph.build(filters(user = DEFAULT_USER))
    val visibleGraph = visiblePack.getVisibleGraph()
    assertEquals(6, visibleGraph.getVisibleCommitCount())
    assertDoesNotContain(visibleGraph, 3)
  }

  fun assertDoesNotContain(graph : VisibleGraph<Int>, id : Int) {
    assertTrue(null == (1..graph.getVisibleCommitCount()).firstOrNull{ graph.getRowInfo(it - 1).getCommit() == id })
  }

  data class Ref(val name : String, val commit : Int)
  data class Data(val user: VcsUser = DEFAULT_USER, val subject: String = "default commit message")

  class Graph(val commits: List<GraphCommit<Int>>,
              val refs: Set<VisiblePackBuilderTest.Ref>,
              val data: HashMap<GraphCommit<Int>, Data>) {
    val root = MockVirtualFile("root") : VirtualFile
    val providers: Map<VirtualFile, TestVcsLogProvider> = mapOf(root to TestVcsLogProvider(root))

    fun build(filters : VcsLogFilterCollection) : VisiblePack {
      val hashMap = generateHashMap(commits.maxBy { it.getId() }!!.getId())

      val refs = refs.mapTo(HashSet<VcsRef>(), {
        VcsRefImpl(hashMap.getHash(it.commit), it.name, BRANCH_TYPE, root)
      })

      val dataPack = DataPack.build(commits, mapOf(root to refs), providers, hashMap, true)
      val detailsCache = data.entrySet().map {
        val hash = hashMap.getHash(it.key.getId())
        val metadata = VcsCommitMetadataImpl(hash, hashMap.getHashes(it.key.getParents()), 1L, root, it.value.subject,
                                             it.value.user, it.value.subject, it.value.user, 1L)
        Pair(it.key.getId(), metadata)
      }.toMap()
      val builder = VisiblePackBuilder(providers, hashMap, detailsCache, CommitDetailsGetter(hashMap, providers, TRIVIAL_DISPOSABLE))

      return builder.build(dataPack, PermanentGraph.SortType.Normal, filters, CommitCountStage.INITIAL).first
    }

    fun VcsLogHashMap.getHashes(ids: List<Int>) = ids.map { getHash(it) }

    fun generateHashMap(num: Int): VcsLogHashMap {
      val map = HashMap<Hash, Int>()
      for (i in 1..num) {
        map.put(HashImpl.build(i.toString()), i)
      }
      return ConstantVcsLogHashMap(map)
    }
  }

  fun filters(branch: List<String>? = null, user: VcsUser? = null)
      = VcsLogFilterCollectionImpl(branchFilter(branch), userFilter(user), null, null, null, null, null)

  fun branchFilter(branch: List<String>?): VcsLogBranchFilterImpl? {
    return if (branch != null) VcsLogBranchFilterImpl(branch) else null
  }

  fun userFilter(user: VcsUser?): VcsLogUserFilter? {
    return if (user != null) VcsLogUserFilterImpl(listOf(user.getName()), mapOf()) else null
  }

  fun graph(f: GraphBuilder.() -> Unit) : Graph {
    val builder = GraphBuilder()
    builder.f()
    return builder.done()
  }

  class GraphBuilder {
    val commits = ArrayList<GraphCommit<Int>>()
    val refs = HashSet<Ref>()
    val data = HashMap<GraphCommit<Int>, Data>()

    fun Int.invoke(vararg id: Int) : GraphCommit<Int> {
      val commit = GraphCommitImpl(this, id.toList(), this.toLong())
      commits.add(commit)
      data[commit] = Data()
      return commit
    }

    fun GraphCommit<Int>.times(name: String): GraphCommit<Int> {
      refs.add(Ref(name, this.getId()))
      return this
    }

    fun GraphCommit<Int>.plus(name: String): GraphCommit<Int> {
      data[this] = Data(VcsUserImpl(name, name + "@example.com"))
      return this;
    }

    fun GraphCommit<Int>.plus(user : VcsUser) : GraphCommit<Int> {
      data[this] = Data(user)
      return this;
    }

    fun done() = Graph(commits, refs, data)
  }

  class ConstantVcsLogHashMap(val map: Map<Hash, Int>) : VcsLogHashMap {
    val reverseMap = map.entrySet().map { Pair(it.value, it.key) }.toMap()

    override fun getCommitIndex(hash: Hash) = map.get(hash)!!

    override fun getHash(commitIndex: Int) = reverseMap.get(commitIndex)!!

    override fun findHashByString(string: String) = throw UnsupportedOperationException()
  }

  object TRIVIAL_DISPOSABLE : Disposable {
    override fun dispose() {
      throw UnsupportedOperationException()
    }
  }
}

