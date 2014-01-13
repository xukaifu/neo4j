/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
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
package org.neo4j.kernel.impl.core;

import java.util.Iterator;

import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Resource;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.kernel.api.ReadOperations;
import org.neo4j.kernel.api.Statement;
import org.neo4j.kernel.impl.cleanup.CleanupService;
import org.neo4j.kernel.impl.util.TestLogging;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.neo4j.helpers.collection.IteratorUtil.primitiveLongIterator;

public class NodeProxySingleRelationshipTest
{
    private static final long REL_ID = 1;
    private static final RelationshipType loves = DynamicRelationshipType.withName( "LOVES" );

    /**
     * This behaviour is a workaround until we have proper concurrency support in the kernel.
     * It fixes a problem at the lower levels whereby a source containing a relationship from disk
     * gets merged with a add COW map containing the same relationship in an uncommitted transaction
     * giving unhelpful duplicates at the API level. One day this unit test can be removed,
     * but that day is not today.
     */
    @Test
    public void shouldQuietlyIgnoreSingleDuplicateEntryWhenGetSingleRelationshipCalled() throws Exception
    {
        // given
        NodeProxy nodeImpl = mockNodeWithRels( REL_ID, REL_ID );

        // when
        Relationship singleRelationship = nodeImpl.getSingleRelationship( loves, Direction.OUTGOING );

        // then
        assertNotNull( singleRelationship );
    }

    @Test
    public void shouldThrowExceptionIfMultipleDifferentEntries() throws Exception
    {
        // given
        NodeProxy node = mockNodeWithRels( REL_ID, REL_ID + 1 );

        // when
        try
        {
            node.getSingleRelationship( loves, Direction.OUTGOING );
            fail();
        }
        catch ( NotFoundException expected )
        {
        }
    }

    @Test
    public void shouldThrowExceptionIfMultipleDifferentEntriesWithTwoOfThemBeingIdentical() throws Exception
    {
        // given
        NodeProxy node = mockNodeWithRels( REL_ID, REL_ID, REL_ID + 1, REL_ID + 1);

        // when
        try
        {
            node.getSingleRelationship( loves, Direction.OUTGOING );
            fail();
        }
        catch ( NotFoundException expected )
        {
        }
    }

    private NodeProxy mockNodeWithRels(long ... relIds)
    {
        ThreadToStatementContextBridge stmCtxBridge = mock( ThreadToStatementContextBridge.class );
        NodeProxy.NodeLookup nodeLookup = mock( NodeProxy.NodeLookup.class );
        GraphDatabaseService gds = mock( GraphDatabaseService.class );

        when(gds.getRelationshipById( REL_ID )).thenReturn( mock( Relationship.class ) );
        when(gds.getRelationshipById( REL_ID + 1)).thenReturn( mock(Relationship.class) );
        when( nodeLookup.getGraphDatabase() ).thenReturn( gds );

        NodeProxy nodeImpl = new NodeProxy( 1, nodeLookup, stmCtxBridge,
                new CleanupService(new TestLogging())
                {
                    @Override
                    public <T> ResourceIterator<T> resourceIterator( Iterator<T> iterator, Resource resource )
                    {
                        return IteratorUtil.asResourceIterator( iterator );
                    }
                } );

        Statement stmt = mock( Statement.class );
        ReadOperations readOps = mock( ReadOperations.class );

        when(stmt.readOperations()).thenReturn( readOps );
        when( stmCtxBridge.instance() ).thenReturn( stmt );
        when( readOps.relationshipTypeGetForName( loves.name() ) ).thenReturn( 2 );

        when(readOps.relationshipsGetFromNode( 1, Direction.OUTGOING, 2 )).thenReturn( primitiveLongIterator(relIds) );
        return nodeImpl;
    }
}