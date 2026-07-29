package com.unciv.logic.map.astar

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import java.io.File
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class PathingMapTest {

    private lateinit var originTile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(8)
        originTile = testGame.tileMap[0, 0]
        civInfo = testGame.addCiv()
        for (i in 0 until 100)
            testGame.tileMap.tileList[i].setExplored(civInfo, true)
    }

    @Test // This only exists to reduce how often we accidentally push with verbose logging enabled
    fun verbose_pathing_logs_disabled() {
        Assert.assertEquals(PathingMap.VERBOSE_PATHFINDING_LOGS, PathingMap.NEVER_LOG)
    }

    @Test
    fun bitsInRouteNodeRoundTrip() {
        testGame.makeHexagonalMap(209) //209 is smallest radius that uses all bits in tile index
        val zeroBasedIndex = (1 shl 17) or 1 // 18 bits. value is 131073
        val tile = testGame.tileMap.tileList[zeroBasedIndex]
        val relationship = RelationshipLevel.Unforgivable // 3 bits, value is 8
        val pbmMoveThisTurn = FixedPointMovement.fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val moveThisTurn = FixedPointMovement.fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val turns = (1 shl 5) or 1 // 6 bits. value is 33
        val parentTile = testGame.tileMap.getClockPositionNeighborTile(tile, 12)!!
        val attackRange = (1 shl 4) or 1 // 5 bits. value is 17
        val damagingTiles = 3
        val underestimatedTotal = FixedPointMovement.fpmFromFixedPointBits((1 shl 13) or 1) //15 bits. value is 8193 aka 409.65move
        val canMoveTo = true

        val node = RouteNode(
            tile,
            relationship,
            pbmMoveThisTurn,
            moveThisTurn,
            turns,
            parentTile,
            canMoveTo,
            damagingTiles
        )

        Assert.assertEquals(tile.zeroBasedIndex, node.tileIdx)
        Assert.assertEquals(tile, node.tile(testGame.tileMap))
        Assert.assertEquals(12, node.parentClockDir)
        Assert.assertEquals(parentTile, node.parentTile(testGame.tileMap))
        Assert.assertEquals(true, node.canMoveTo)
        Assert.assertEquals(moveThisTurn, node.moveUsedThisTurn)
        Assert.assertEquals(pbmMoveThisTurn, node.pbmMoveThisTurn)
        Assert.assertEquals(turns, node.turns)
        Assert.assertEquals(damagingTiles, node.damagingTiles)
        Assert.assertEquals(relationship, node.relationshipLevel)
        Assert.assertEquals(true, node.initialized)

        val prioritized = PrioritizedNode(node, underestimatedTotal)
        Assert.assertEquals(tile.zeroBasedIndex, prioritized.tileIdx)
        Assert.assertEquals(underestimatedTotal, prioritized.underestimatedTotal)
        
        val reRouteNode = RouteNode(prioritized.bits)
        Assert.assertEquals(tile.zeroBasedIndex, reRouteNode.tileIdx)
        Assert.assertEquals(tile, reRouteNode.tile(testGame.tileMap))
        // PrioritizedNode drops parentTile
        Assert.assertEquals(moveThisTurn, reRouteNode.moveUsedThisTurn)
        Assert.assertEquals(pbmMoveThisTurn, reRouteNode.pbmMoveThisTurn)
        Assert.assertEquals(turns, reRouteNode.turns)
        Assert.assertEquals(damagingTiles, reRouteNode.damagingTiles)
        Assert.assertEquals(relationship, reRouteNode.relationshipLevel)
        Assert.assertEquals(true, reRouteNode.initialized)
    }


    @Test
    fun shortestPathEvenWhenItsWayMoreTiles() {
        // A straight road from 0,0 up the x axis
        testGame.getTile(HexCoord(0, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(1, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(2, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(3, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(4, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // then straight down the y axis for 4 tiles
        testGame.getTile(HexCoord(5, 1)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 2)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 3)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // then straight down the x axis for 4 tiles
        testGame.getTile(HexCoord(4, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(3, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(2, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(1, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(0, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // The total roads are be 14 tiles, but only 1.4 movement. the direct route is 3 tiles, but
        // 3 movement.  So the road route should be chosen, despite gong way out of the way.
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        val target = testGame.getTile(HexCoord(0, 4))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)!!

        // expect movement along the railroad, even though it's 13 tiles
        Assert.assertEquals(
            listOf(
                HexCoord(3, 4),
                HexCoord(0, 4)
            ),
            path.map { it.position },
        )
        Assert.assertEquals(1, pathing.getCachedNode(target).turns)
        Assert.assertEquals(FixedPointMovement.fpmFromMovement(0.3f), pathing.getCachedNode(target).moveUsedThisTurn)
        Assert.assertEquals(
            """
        -1     +0     +1     +2     +3     +4     +5     +6    
  +5     /      /     1/1.0  1/1.0  1/1.0  0/1.0  0/1.0  0/1.0 
  +4     /     1/0.3D 1/0.2  1/0.1  0/1.0  0/0.9  0/0.8  0/1.0 
  +3     /     1/1.0  1/1.0  1/1.0  0/1.0  0/1.0  0/0.7  0/1.0 
  +2     /      /      /      /      /     0/1.0  0/0.6  0/1.0 
  +1     /     0/1.0  0/1.0  0/1.0  0/1.0  0/1.0  0/0.5  0/1.0 
  +0    0/1.0  0/0.0S 0/0.1  0/0.2  0/0.3  0/0.4  0/0.5   /    
  -1    0/1.0  0/1.0  0/1.0  0/1.0  0/1.0  0/1.0   /      /    
""", pathing.toDebugString(target)
        )
        // And affirm cache
        Assert.assertEquals(path, pathing.getShortestPath(target)!!)
    }

    @Test
    fun canPauseBeforeMountainsToCrossWithoutDamage() {
        // Everything is mountains
        for (tile in testGame.tileMap.tileList) {
            testGame.setTileTerrain(tile.position, "Mountain")
        }
        testGame.setTileTerrain(HexCoord(0, 0), "Plains")
        testGame.setTileTerrain(HexCoord(0, 1), "Plains")
        // unit has two cross two mountains here, so MUST stop on 0,1 to safely cross
        testGame.setTileTerrain(HexCoord(0, 4), "Plains")
        testGame.setTileTerrain(HexCoord(0, 5), "Plains")
        // unit has two cross two mountains here, so MUST stop on 0,5 to safely cross
        testGame.setTileTerrain(HexCoord(0, 8), "Plains")
        
        // Enable Carthage LandUnitsCrossTerrainAfterUnitGained
        civInfo.passThroughImpassableUnlocked = true
        civInfo.passableImpassables.add("Mountain")
        
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        val target = testGame.getTile(HexCoord(0, 8))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        Assert.assertEquals(
            listOf(
                HexCoord(0, 1),
                HexCoord(0, 4),
                HexCoord(0, 5),
                HexCoord(0, 8),
            ), path?.map { it.position })
        Assert.assertEquals(
            """
        -4     -3     -2     -1     +0     +1     +2     +3     +4    
  +8                                3/3.0D 4/3.0*  /      /      /    
  +7                         4/3.0* 3/2.0* 3/2.0* 3/2.0* 3/3.0*  /    
  +6                   /     3/2.0* 2/2.0* 2/2.0* 2/2.0* 3/3.0*  /    
  +5            /     3/2.0* 2/2.0* 2/1.0  2/1.0* 2/2.0* 3/3.0* 3/1.0*
  +4     /     3/3.0* 2/2.0* 2/1.0* 1/3.0  2/3.0* 2/3.0* 2/3.0* 3/1.0*
  +3     /     3/3.0* 2/2.0* 2/3.0* 1/2.0* 1/2.0* 1/2.0* 1/3.0* 2/1.0*
  +2     /     3/3.0* 2/3.0* 1/2.0* 0/2.0* 0/2.0* 0/2.0* 1/3.0* 2/1.0*
  +1    3/1.0* 2/3.0* 1/2.0* 0/2.0* 0/1.0  0/1.0* 0/2.0* 1/3.0* 2/1.0*
  +0    3/1.0* 1/3.0* 0/2.0* 0/1.0* 0/0.0S 0/1.0* 0/2.0* 1/3.0* 2/1.0*
  -1    2/1.0* 1/3.0* 0/2.0* 0/1.0* 0/1.0* 0/2.0* 1/3.0* 2/1.0*  /    
  -2    2/1.0* 1/3.0* 0/2.0* 0/2.0* 0/2.0* 1/3.0* 2/1.0*  /      /    
  -3    2/1.0* 1/3.0* 1/3.0* 1/3.0* 1/3.0* 2/1.0*  /      /      /    
  -4    2/1.0* 2/1.0* 2/1.0* 2/1.0* 2/1.0*  /      /      /      /    
""", pathing.toDebugString(target)
        )
        // And affirm cache
        Assert.assertEquals(path, pathing.getShortestPath(target)!!)
    }
    
    @Test
    fun getMovementToTilesAtPosition_returnsRightTiles() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 2f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getMovementToTilesAtPosition()

        Assert.assertEquals(path.toString(), 18, path.size)
//        assertNotEquals(path.toString(), path.firstEntry(), path.lastEntry())
        Assert.assertEquals(
            """
        -3     -2     -1     +0     +1     +2     +3    
  +3     /      /      /      /     1/1.0  1/1.0  1/1.0 
  +2     /      /     1/1.0  1/1.0  0/2.0  0/2.0  1/1.0 
  +1     /     1/1.0  0/2.0  0/2.0  0/1.0  0/2.0  1/1.0 
  +0    1/1.0  0/2.0  0/1.0  0/0.0S 0/1.0  0/2.0  1/1.0 
  -1    1/1.0  0/2.0  0/1.0  0/1.0  0/2.0  1/1.0   /    
  -2    1/1.0  0/2.0  0/2.0  0/2.0  1/1.0   /      /    
  -3    1/1.0  1/1.0  1/1.0  1/1.0   /      /      /    
""", pathing.toDebugString()
        )
        // And affirm cache
        Assert.assertEquals(path, pathing.getMovementToTilesAtPosition())
    }

    @Test
    fun getShortestPath_takesOverMaxTurns_returnsNull() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.getTile(HexCoord(-8, -8))
        val targetTile = testGame.getTile(HexCoord(8, 8))
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(targetTile, 5)

        Assert.assertNull(path)
        // And affirm cache
        Assert.assertEquals(path, pathing.getShortestPath(targetTile, 5))
    }

    @Test
    fun getShortestPath_takesOver63Turns_returnsNull() {
        testGame.makeHexagonalMap(100)
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.getTile(HexCoord(-50, 0))
        val targetTile = testGame.getTile(HexCoord(50, 0))
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(targetTile)

        Assert.assertNull(path)
        // And affirm cache
        Assert.assertEquals(path, pathing.getShortestPath(targetTile))
    }

    @Test
    fun getShortestPath_toEveryTile_usesCacheCorrectly() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 4
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 4f

        val pathing = PathingMap.createUnitPathingMap(unit)
        for (targetTile in testGame.tileMap.tileList) {
            val aerialDistance = originTile.aerialDistanceTo(targetTile)
            
            val path = pathing.getShortestPath(targetTile)
            
            if (aerialDistance <= 4) {
                Assert.assertEquals(listOf(targetTile), path)
            } else {
                Assert.assertEquals(2, path!!.size)
                Assert.assertEquals(targetTile, path[1])                
            }
        }
    }
    
    @Test
    fun findMatchingTilesInAttackRange_considersAttackRange() {
        val evemyCiv = testGame.addBarbarianCiv()
        // Right side all hills, left side all roads.  A line of enemy warriors just north.
        for (tile in testGame.tileMap.tileList) {
            if (tile.position.x > 0) tile.addTerrainFeature("Hill")
            if (tile.position.x < 0) tile.setRoadStatus(RoadStatus.Road, civInfo)
            if (tile.position.y == 2) testGame.addUnit("Warrior", evemyCiv, tile)
        }
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        baseUnit.range = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 3f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val attackableTiles = pathing.bfsAllMatchingTiles(1) { tile, _ -> tile.militaryUnit?.civ == evemyCiv}
        
        // cant hit enemy at -5,2 because unit would have no movement left.
        val expected = listOf(
            HexCoord(-4, 2),
            HexCoord(-3, 2),
            HexCoord(-2, 2),
            HexCoord(-1, 2),
            HexCoord(0, 2),
            HexCoord(1, 2),
            HexCoord(2, 2),
            HexCoord(3, 2),
        )
        val actual = attackableTiles.map {it.position}.sortedWith { l, r -> if (l.x != r.x) l.x.compareTo(r.x) else l.y.compareTo(r.y) }
        Assert.assertEquals(expected, actual)
        Assert.assertEquals(
            """
        -6     -5     -4     -3     -2     -1     +0     +1     +2     +3    
  +2     /      /     0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0*
  +1     /     1/0.5  0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/2.0  0/3.0  1/2.0 
  +0    1/0.5  0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/0.0S 0/2.0  0/3.0  1/2.0 
  -1    1/0.5  0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/1.0  0/3.0  1/2.0   /    
  -2    1/0.5  0/3.0  0/2.5  0/2.0  0/1.5  0/1.5  0/2.0  0/3.0  1/2.0   /    
  -3    1/0.5  0/3.0  0/2.5  0/2.0  0/2.0  0/2.0  0/3.0  1/2.0   /      /    
  -4    1/0.5  0/3.0  0/2.5  0/2.5  0/2.5  0/2.5  0/3.0  1/2.0   /      /    
  -5    1/0.5  0/3.0  0/3.0  0/3.0  0/3.0  0/3.0  1/1.0   /      /      /    
  -6    1/0.5  1/0.5  1/0.5  1/0.5  1/0.5  1/0.5   /      /      /           
""", pathing.toDebugString()
        )
        // And affirm full recalculation using cached tiles has same result.
        val actual2 = attackableTiles.map {it.position}.sortedWith { l, r -> if (l.x != r.x) l.x.compareTo(r.x) else l.y.compareTo(r.y) }
        Assert.assertEquals(expected, actual2)
    }

    @Test
    fun findMatchingTilesInAttackRange_considersSelf() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val attackableTiles = pathing.bfsAllMatchingTilesThisTurn { tile, _ -> tile.civilianUnit?.civ == civInfo}

        val expected = listOf(HexCoord(0, 0))
        Assert.assertEquals(expected, attackableTiles.map { it.position })
        // And affirm full recalculation using cached tiles has same result.
        Assert.assertEquals(expected, pathing.bfsAllMatchingTilesThisTurn { tile, _ -> tile.civilianUnit?.civ == civInfo }.map { it.position })
    }

    private companion object {
        const val TestModName = "Pathing-Test"
        const val TestSaveResource = "SaveFiles/Pathing-Test"
        const val TestUnitID = 134
        const val TestUnitName = "Redcoat"
        const val TestUnitCiv = "England"
        val ExpectedStartPos = HexCoord(6, 0)
        val TargetPos = HexCoord(8, 4)
    }

    @Test
    fun `issue #15165 - Crash with Can't reach tile`() {
        runAutomateUnitMovesOnSave(true)
    }

    @Test
    fun `issue #15165 - Was fine with classic pathfinding`() {
        runAutomateUnitMovesOnSave(false)
    }

    fun runAutomateUnitMovesOnSave(astar: Boolean) {
        val modUrl = javaClass.classLoader.getResource("mods/$TestModName")
            ?: error("Test mod not found")
        val modFile = FileHandle(File(modUrl.toURI()))
        val errorLines = mutableListOf<String>()
        val mod = RulesetCache.loadSingleRuleset(modFile, errorLines)
            ?: error("Could not load Test mod")
        RulesetCache[TestModName] = mod

        val saveData = javaClass.classLoader.getResourceAsStream(TestSaveResource)
            ?.bufferedReader()?.readText()
            ?: error("Could not load Test save")
        val game = UncivFiles.gameInfoFromString(saveData)

        val unit = game.getCivilization(TestUnitCiv).units.getUnitById(TestUnitID)
            ?: error("Test unit not found in save")
        Assert.assertEquals(TestUnitName, unit.name)
        Assert.assertEquals(ExpectedStartPos, unit.currentTile.position)

        UncivGame.Current.settings.useAStarPathfinding = astar

        // VERBOSE_PATHFINDING_LOGS = ExpectedStartPos // Activate for debug info
        // For intricate debugging, one could call `UnitAutomation.automateUnitMoves(unit)` here - too dependent on AI redesign

        // The original issue code path had one PathingMap.getMovementToTilesAtPosition eval,
        // incomplete due to early exit, then tryHeadTowardsEnemyCity using getShortestPath would
        // fill in the actual PathingMap but keep the cached PathingMap.tilesSameTurn, then
        // headTowardsEnemyCity would ask getMovementToTilesAtPosition again and find the destination
        // returned by getShortestPath missing. Emulate only the first part here:
        val reachableMap = unit.movement.getDistanceToTiles()
        val targetTile = reachableMap.keys.firstOrNull { it.position == TargetPos }
        Assert.assertTrue("$unit should be able to reach $TargetPos in one turn", targetTile != null)
    }
}
