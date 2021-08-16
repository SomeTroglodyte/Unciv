package com.unciv.testing

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.newgamescreen.GameSetupInfo
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class SerializationTests {

    //@Test
    /** This is a _guaranteed_ stack overflow - the try doesn't catch */
    fun tryFixJson() {
        class TestWithLazy {
            val test: Int by lazy { 0 }
        }
        val json = com.badlogic.gdx.utils.Json().apply {
            setIgnoreDeprecated(true)
            // val clazz = TestWithLazy::class.java.declaredFields[0].type
            // same result as val clazz = kotlin.Lazy::class.java
            // but we need val clazz = kotlin.SynchronizedLazyImpl::class.java
            // setDeprecated(clazz, "initializer", true)
            // setDeprecated(clazz, "_value", true)
            // setDeprecated(clazz, "lock", true)
        }
        val data = TestWithLazy()
        try {
            val out = json.toJson(data, TestWithLazy::class.java)
            println(out)
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    @Before
    fun prepareGame() {
        RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun canSerializeGame() {
        val ruleset = RulesetCache.getBaseRuleset()

        val param = GameParameters().apply {
            numberOfCityStates = 0
            players.clear()
            players.add(Player("Rome").apply { playerType = PlayerType.Human })
            religionEnabled = true
        }
        val mapParameters = MapParameters().apply {
            mapSize = MapSizeNew(MapSize.Tiny)
        }
        val setup = GameSetupInfo(param, mapParameters)
        UncivGame.Current = UncivGame("")
        UncivGame.Current.settings = GameSettings()
        val game = GameStarter.startNewGame(setup)
        UncivGame.Current.gameInfo = game

        val civ = game.getCurrentPlayerCivilization()
        val unit = civ.getCivUnits().first { it.hasUnique(Constants.settlerUnique) }
        val tile = unit.getTile()
        unit.civInfo.addCity(tile.position)
        if (tile.ruleset.tileImprovements.containsKey("City center"))
            tile.improvement = "City center"
        unit.destroy()
        val city = civ.cities.first()
        val name = ruleset.religions.first()
        val founder = ruleset.beliefs.values.first { it.type == BeliefType.Founder }
        val follower = ruleset.beliefs.values.first { it.type == BeliefType.Follower }
        civ.religionManager.foundingCityId = city.id
        civ.religionManager.foundReligion(name, name, listOf(founder.name), listOf(follower.name))
        
        val json = try {
            GameSaver.json().toJson(game)    
        } catch (ex: Exception) {
            ex.printStackTrace()
            ""
        }
        
        Assert.assertTrue("This test will only pass when a game can be serialized", json.isNotEmpty())
    }
}
