package com.unciv.logic

import com.unciv.json.LastSeenImprovement
import com.unciv.logic.civilization.*
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.TileHistory
import com.unciv.models.Counter
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.KeyboardBindings
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.victoryscreen.RankingType
import java.lang.reflect.Modifier
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.temporal.ChronoUnit

@RunWith(GdxTestRunner::class)
class SerializationTests {
    private val json = com.unciv.json.json()

    // use @RedirectOutput(RedirectPolicy.Show) to see the actual json

    @Test
    fun `test DurationSerializer`() {
        val data = arrayListOf(
            // Java Duration! (even though kotlin.Duration is perfectly fine - all the multiplayer code is outdated in that respect)
            Duration.ZERO,
            Duration.of(666, ChronoUnit.HOURS),
            Duration.parse("P1DT2H3M4.058S"),
        )
        testRoundtrip(data, Duration::class.java)
    }

    @Test
    //@RedirectOutput(RedirectPolicy.Show)
    fun `test LastSeenImprovement serialization roundtrip`() {
        val data = LastSeenImprovement()
        data[HexCoord.Zero] = "Borehole"
        data[HexCoord(1,0)] = "Smokestack"
        data[HexCoord(0,1)] = "Waffle stand"
        testRoundtrip(data)
    }

    @Test
    fun `test KeyboardBindings serialization roundtrip`() {
        val data = KeyboardBindings()
        data[KeyboardBinding.DeveloperConsole] = KeyCharAndCode.TAB
        data[KeyboardBinding.NextTurn] = KeyCharAndCode('X')
        data[KeyboardBinding.NextTurnAlternate] = KeyCharAndCode.ctrl('X')
        data[KeyboardBinding.Menu] = KeyCharAndCode.BACK
        testRoundtrip(data)
    }

    @Test
    fun `test TileHistory serialization roundtrip`() {
        val data = TileHistory()
        data.addTestEntry(0, TileHistory.TileHistoryState("Greece", TileHistory.TileHistoryState.CityCenterType.Capital))
        data.addTestEntry(1, TileHistory.TileHistoryState(null, TileHistory.TileHistoryState.CityCenterType.None))
        testRoundtrip(data) { expected, actual ->
            // Neither TileHistory nor TileHistoryState support equality contract
            Assert.assertTrue(expected.all {
                val expectedState = it.value
                val actualState = actual.getState(it.key)
                expectedState.owningCivName == actualState.owningCivName && expectedState.cityCenterType == actualState.cityCenterType
            })
        }
    }

    @Test
    fun `test CivRankingHistory serialization roundtrip`() {
        val data = CivRankingHistory()
        data[0] = mapOf(RankingType.Force to 0, RankingType.Territory to 7)
        data[20] = mapOf(RankingType.Culture to 42, RankingType.Territory to 666)
        testRoundtrip(data)
    }

    @Test
    fun `test UnitMovementMemory serialization roundtrip`() {
        val data = arrayListOf(
            MapUnit.UnitMovementMemory(HexCoord(-1,42), UnitMovementMemoryType.UnitTeleported),
            MapUnit.UnitMovementMemory(HexCoord(42,-3), UnitMovementMemoryType.UnitAttacked),
        )
        fun MapUnit.UnitMovementMemory.isEqual(other: MapUnit.UnitMovementMemory) =
            position == other.position && type == other.type
        testRoundtrip(data, MapUnit.UnitMovementMemory::class.java) { expected, actual ->
            Assert.assertTrue(expected.withIndex().all { (index, expectedMemory) ->
                expectedMemory.isEqual(actual[index])
            })
        }
    }

    @Test
    fun `test HistoricalAttackMemory serialization roundtrip`() {
        val data = arrayListOf(
            Civilization.HistoricalAttackMemory("Submarine", HexCoord(-1,42), HexCoord(0,42)),
            Civilization.HistoricalAttackMemory("Cruiser", HexCoord(42,-3), HexCoord(42,-2)),
        )
        fun Civilization.HistoricalAttackMemory.isEqual(other: Civilization.HistoricalAttackMemory) =
            attackingUnit == other.attackingUnit && source == other.source && target == other.target
        testRoundtrip(data, Civilization.HistoricalAttackMemory::class.java) { expected, actual ->
            Assert.assertTrue(expected.withIndex().all { (index, expectedMemory) ->
                expectedMemory.isEqual(actual[index])
            })
        }
    }

    @Test
    @RedirectOutput(RedirectPolicy.Show)
    fun `test Notification serialization roundtrip`() {
        val data = arrayListOf(
            Notification("hello", emptyArray(), emptyList(), Notification.NotificationCategory.General),
            Notification("Oh my goddesses", arrayOf("ReligionIcons/Pray"),
                listOf(ReligionAction("Civilizationism"), CivilopediaAction("Tutorial/Religion")),
                Notification.NotificationCategory.Religion),
            Notification("There's Horses", arrayOf("ResourceIcons/Horses"), LocationAction(HexCoord.Zero, HexCoord(1,0)).asIterable(), Notification.NotificationCategory.General),
            Notification("An evil overlord has arisen", arrayOf("PersonalityIcons/Devil"), listOf(DiplomacyAction("Russia")), Notification.NotificationCategory.War),
            Notification("Here's a Wizzard", arrayOf("EmojiIcons/Great Scientist"), listOf(MapUnitAction(HexCoord(0,1), 42)), Notification.NotificationCategory.Units),
            Notification("All roads lead to", arrayOf("ImprovementIcons/City center"), listOf(CityAction(HexCoord(-42,-1))), Notification.NotificationCategory.Diplomacy),
            Notification("All other NotificationActions", arrayOf("TechIcons/Education"),
                listOf(
                    TechAction("Eureka"), EspionageAction(), MayaLongCountAction(),
                    OverviewAction(EmpireOverviewCategories.Notifications, "123"), LinkAction("https://localhost"),
                    PromoteUnitAction("Warrior", HexCoord(99,-1), 42), PolicyAction("Honor")
                ),
                Notification.NotificationCategory.Production),
        )

        // Neither Notification nor NotificationAction support equality contract
        fun Notification.isEqual(other: Notification): Boolean {
            if (text != other.text) return false
            if (category != other.category) return false
            if (icons != other.icons) return false
            val otherIterator = other.actions.iterator()
            for (action in actions) {
                if (!otherIterator.hasNext()) return false
                val otherAction = otherIterator.next()
                if (action.javaClass != otherAction.javaClass) return false
                // The lazy way to compare fields that vary from one NotificationAction subclass to the next
                if (json.toJson(action) != json.toJson(otherAction)) return false
                // .. but that doesn't test proper implementation of Json.Serializable
                if (action::class.qualifiedName != otherAction::class.qualifiedName) return false
                for (field in action::class.java.declaredFields) {
                    field.isAccessible = true
                    if (Modifier.isStatic(field.modifiers)) continue  // not skipping Modifier.isTransient(field.modifiers)
                    if (field.get(action) != field.get(otherAction))
                        return false
                }
            }
            return !otherIterator.hasNext()
        }

        testRoundtrip(data, Notification::class.java) { expected, actual ->
            Assert.assertTrue(expected.withIndex().all { (index, notification) ->
                notification.isEqual(actual[index])
            })
        }
    }

    /** Note that no other Counter<X> will pass this test */
    @Test
    fun `test Counter(String) serialization roundtrip`() {
        val data = Counter(mapOf("Foo" to 1, "Bar" to 3, "Towel" to 42))
        testRoundtrip(data)
    }

    ///////////////////////////////// Helper
    private inline fun <reified T> testRoundtrip(
        data: T,
        elementType: Class<*>? = null,
        testEquality: ((expected: T, actual: T)->Unit) = { expected, actual ->
            Assert.assertEquals(expected, actual)
        }
    ) {
        val serialized = json.toJson(data, null, elementType)
        println("Serialized form: $serialized")
        Assert.assertTrue(serialized.isNotBlank())
        val deserialized = json.fromJson(T::class.java, elementType, serialized)
        testEquality(data, deserialized)
    }
}
