/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.hdbscan;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.api.properties.nodes.DoubleArrayNodePropertyValues;
import org.neo4j.gds.collections.ha.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class KdTreeNodeBuilderTaskTest {

    @Test
    void shouldFixMedianCorrectly(){
        HugeLongArray ids = HugeLongArray.of(0,1,2,3,4,5,6,7,8,9);
        Random rnd=new Random();
        for (int i=1;i<10;++i){ //get a random perm each time
            int pos = rnd.nextInt(i);
            ids.set(i,ids.get(pos));
            ids.set(pos,i);
        }
        DoubleArrayNodePropertyValues nodePropertyValues=new DoubleArrayNodePropertyValues() {
            @Override
            public double[] doubleArrayValue(long nodeId) {
                return new double[]{nodeId};
            }
            @Override
            public long nodeCount() {
                return ids.size();
            }
        };

        var aabbCreator = new DoubleKDNodeSupport(nodePropertyValues,ids,1);

        var nodeBuilder = new KdTreeNodeBuilderTask(
            aabbCreator,
            nodePropertyValues,
            ids,
            0,
            10,
            1,
            new AtomicInteger(0),
            null,
            false,
            ProgressTracker.NULL_TRACKER
        );

        var median = nodeBuilder.findMedianAndSplit(0);
        assertThat(median).isEqualTo(5L);
        assertThat(ids.toArray()).containsExactlyInAnyOrder(0,1,2,3,4,5,6,7,8,9);
        for (int i=0;i<5;++i){
            assertThat(ids.get(i)).isLessThan(5L);
        }

    }

    @Test
    void shouldNotTouchOutsideWorkingBounds(){
        HugeLongArray ids  =HugeLongArray.of(-1,-1,-1,-1,2,3,0,1,-1,-1,-1);
        DoubleArrayNodePropertyValues nodePropertyValues=new DoubleArrayNodePropertyValues() {
            @Override
            public double[] doubleArrayValue(long nodeId) {
                if (nodeId==-1) throw new RuntimeException("You shall not touch!");
                return new double[]{nodeId};
            }
            @Override
            public long nodeCount() {
                return ids.size();
            }
        };

        var aabbCreator = new DoubleKDNodeSupport(nodePropertyValues,ids,1);

        var nodeBuilder =new KdTreeNodeBuilderTask(
            aabbCreator,
            nodePropertyValues,
            ids,
            4,
            8,
            1,
            new AtomicInteger(),
            null,
            false,
            ProgressTracker.NULL_TRACKER
        );

        var median = nodeBuilder.findMedianAndSplit(0);
        assertThat(median).isEqualTo(6L);
        for (int i=4;i<6;++i){
            assertThat(ids.get(i)).isLessThan(2L);
        }
    }

    @Test
    void shouldReturnALeafNode(){
        HugeLongArray ids  = HugeLongArray.of(0,1,2);
        DoubleArrayNodePropertyValues nodePropertyValues=new DoubleArrayNodePropertyValues() {
            @Override
            public double[] doubleArrayValue(long nodeId) {
                return new double[]{nodeId};
            }
            @Override
            public long nodeCount() {
                return ids.size();
            }
        };

        var aabbCreator = new DoubleKDNodeSupport(nodePropertyValues,ids,1);

        var nodeBuilder =new KdTreeNodeBuilderTask(
            aabbCreator,
            nodePropertyValues,
            ids,
            0,
            3,
            3,
            new AtomicInteger(),
            null,
            false,
            ProgressTracker.NULL_TRACKER
        );

        nodeBuilder.run();

        var node = nodeBuilder.kdNode();
        assertThat(node.isLeaf()).isTrue();
        assertThat(node.start()).isEqualTo(0);
        assertThat(node.end()).isEqualTo(3);
        assertThat(node.leftChild()).isNull();
        assertThat(node.rightChild()).isNull();
        assertThat(node.id()).isEqualTo(0L);

    }

    @Test
    void shouldPerformSplit(){
        HugeLongArray ids  = HugeLongArray.of(0,1,2);
        DoubleArrayNodePropertyValues nodePropertyValues=new DoubleArrayNodePropertyValues() {
            @Override
            public double[] doubleArrayValue(long nodeId) {
                return new double[]{10.0+nodeId};
            }
            @Override
            public long nodeCount() {
                return ids.size();
            }
        };

        var aabbCreator = new DoubleKDNodeSupport(nodePropertyValues,ids,1);

        var nodeBuilder = new KdTreeNodeBuilderTask(
            aabbCreator,
            nodePropertyValues,
            ids,
            0,
            3,
            2,
            new AtomicInteger(0),
            null,
            false,
            ProgressTracker.NULL_TRACKER
        );

        nodeBuilder.run();

        var node = nodeBuilder.kdNode();
        assertThat(node.isLeaf()).isFalse();
        assertThat(node.start()).isEqualTo(0);
        assertThat(node.end()).isEqualTo(3);
        assertThat(node.leftChild()).isNotNull();
        assertThat(node.rightChild()).isNotNull();

        var leftChild = node.leftChild();
        var rightChild = node.rightChild();

        assertThat(leftChild.isLeaf()).isTrue();
        assertThat(leftChild.start()).isEqualTo(0);
        assertThat(leftChild.leftChild()).isNull();
        assertThat(leftChild.rightChild()).isNull();

        assertThat(rightChild.isLeaf()).isTrue();
        assertThat(rightChild.end()).isEqualTo(3);
        assertThat(rightChild.leftChild()).isNull();
        assertThat(rightChild.rightChild()).isNull();

        assertThat(leftChild.end()).isEqualTo(rightChild.start());

        assertThat(node.id()).isEqualTo(0L);
        assertThat(leftChild.id()).isIn(1L,2L);
        assertThat(rightChild.id()).isNotEqualTo(leftChild.id()).isIn(1L,2L);

    }

}
