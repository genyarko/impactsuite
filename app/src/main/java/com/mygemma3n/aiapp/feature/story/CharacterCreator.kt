package com.mygemma3n.aiapp.feature.story

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Character appearance options
enum class Gender { MALE, FEMALE, NON_BINARY, UNSPECIFIED }
enum class AgeGroup { CHILD, TEEN, YOUNG_ADULT, ADULT, ELDERLY }
enum class HairColor { BLACK, BROWN, BLONDE, RED, GRAY, WHITE, BLUE, GREEN, PURPLE, RAINBOW }
enum class EyeColor { BROWN, BLUE, GREEN, HAZEL, GRAY, AMBER, VIOLET }
enum class SkinTone { LIGHT, MEDIUM_LIGHT, MEDIUM, MEDIUM_DARK, DARK }
enum class BodyType { SLIM, AVERAGE, ATHLETIC, CURVY, MUSCULAR }

// Character personality traits
enum class PersonalityTrait {
    BRAVE, SHY, CURIOUS, CREATIVE, FUNNY, KIND, SMART, ADVENTUROUS,
    LOYAL, DETERMINED, CHEERFUL, MYSTERIOUS, CALM, ENERGETIC, WISE, PLAYFUL,
    CLEVER, MISCHIEVOUS, GENTLE, EMPATHETIC, STRONG, PROTECTIVE
}

// Character roles in stories
enum class CharacterRole {
    PROTAGONIST, SIDEKICK, MENTOR, ANTAGONIST, COMIC_RELIEF, 
    WISE_GUIDE, HELPER, CHALLENGER, LOVE_INTEREST, MYSTERIOUS_STRANGER,
    TRICKSTER, HERO, GUARDIAN, PROTECTOR
}

// Character special abilities/skills
enum class SpecialAbility {
    MAGIC, SUPER_STRENGTH, FLIGHT, INVISIBILITY, TELEPATHY, HEALING,
    TIME_TRAVEL, SHAPE_SHIFTING, ELEMENTAL_CONTROL, TECH_GENIUS,
    ANIMAL_COMMUNICATION, ENHANCED_SENSES, NONE,
    WATER_CONTROL, DREAM_WALKING
}

@Entity(tableName = "custom_characters")
data class CustomCharacter(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val gender: Gender,
    val ageGroup: AgeGroup,
    val hairColor: HairColor,
    val eyeColor: EyeColor,
    val skinTone: SkinTone,
    val bodyType: BodyType,
    val personalityTraitsJson: String, // JSON array of PersonalityTrait
    val specialAbilitiesJson: String, // JSON array of SpecialAbility
    val characterRole: CharacterRole,
    val backstory: String,
    val favoriteThings: String, // Comma-separated list
    val fears: String, // Comma-separated list
    val goals: String, // Character's main goals/motivations
    val catchphrase: String = "", // Optional memorable phrase
    val occupation: String = "", // What they do for work/role in society
    val homeland: String = "", // Where they come from
    val relationships: String = "", // Important relationships
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L,
    val useCount: Int = 0,
    val isActive: Boolean = true
)

// Domain model for easier use in UI
data class Character(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val gender: Gender,
    val ageGroup: AgeGroup,
    val hairColor: HairColor,
    val eyeColor: EyeColor,
    val skinTone: SkinTone,
    val bodyType: BodyType,
    val personalityTraits: List<PersonalityTrait>,
    val specialAbilities: List<SpecialAbility>,
    val characterRole: CharacterRole,
    val backstory: String,
    val favoriteThings: List<String>,
    val fears: List<String>,
    val goals: String,
    val catchphrase: String = "",
    val occupation: String = "",
    val homeland: String = "",
    val relationships: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L,
    val useCount: Int = 0,
    val isActive: Boolean = true
) {
    fun getPhysicalDescription(): String {
        val genderStr = when (gender) {
            Gender.MALE -> "male"
            Gender.FEMALE -> "female"
            Gender.NON_BINARY -> "person"
            Gender.UNSPECIFIED -> "person"
        }
        
        val ageStr = when (ageGroup) {
            AgeGroup.CHILD -> "young child"
            AgeGroup.TEEN -> "teenager"
            AgeGroup.YOUNG_ADULT -> "young adult"
            AgeGroup.ADULT -> "adult"
            AgeGroup.ELDERLY -> "elderly person"
        }
        
        val bodyStr = when (bodyType) {
            BodyType.SLIM -> "slim"
            BodyType.AVERAGE -> "average build"
            BodyType.ATHLETIC -> "athletic"
            BodyType.CURVY -> "curvy"
            BodyType.MUSCULAR -> "muscular"
        }
        
        return "$ageStr $genderStr with ${hairColor.name.lowercase().replace('_', ' ')} hair, " +
               "${eyeColor.name.lowercase()} eyes, ${skinTone.name.lowercase().replace('_', ' ')} skin, " +
               "and a $bodyStr physique"
    }
    
    fun getPersonalityDescription(): String {
        val traits = personalityTraits.take(3).joinToString(", ") { 
            it.name.lowercase().replace('_', ' ') 
        }
        return "Known for being $traits"
    }
    
    fun getAbilitiesDescription(): String {
        if (specialAbilities.isEmpty() || specialAbilities.contains(SpecialAbility.NONE)) {
            return "No special abilities"
        }
        val abilities = specialAbilities.filter { it != SpecialAbility.NONE }
            .joinToString(", ") { it.name.lowercase().replace('_', ' ') }
        return "Special abilities: $abilities"
    }
    
    fun getFullDescription(): String {
        return buildString {
            append("$name is a ${getPhysicalDescription()}. ")
            append("${getPersonalityDescription()}. ")
            
            if (backstory.isNotBlank()) {
                append("Background: $backstory ")
            }
            
            if (occupation.isNotBlank()) {
                append("Occupation: $occupation. ")
            }
            
            if (homeland.isNotBlank()) {
                append("From: $homeland. ")
            }
            
            if (goals.isNotBlank()) {
                append("Goals: $goals ")
            }
            
            if (favoriteThings.isNotEmpty()) {
                append("Loves: ${favoriteThings.joinToString(", ")}. ")
            }
            
            if (fears.isNotEmpty()) {
                append("Fears: ${fears.joinToString(", ")}. ")
            }
            
            if (catchphrase.isNotBlank()) {
                append("Catchphrase: \"$catchphrase\" ")
            }
            
            append(getAbilitiesDescription())
        }.trim()
    }
}

@Dao
interface CustomCharacterDao {
    @Query("SELECT * FROM custom_characters WHERE isActive = 1 ORDER BY lastUsed DESC, createdAt DESC")
    fun getAllActiveCharacters(): Flow<List<CustomCharacter>>
    
    @Query("SELECT * FROM custom_characters WHERE id = :characterId")
    suspend fun getCharacterById(characterId: String): CustomCharacter?
    
    @Query("SELECT * FROM custom_characters WHERE characterRole = :role AND isActive = 1")
    suspend fun getCharactersByRole(role: CharacterRole): List<CustomCharacter>
    
    @Query("SELECT * FROM custom_characters WHERE isActive = 1 ORDER BY useCount DESC LIMIT :limit")
    suspend fun getMostUsedCharacters(limit: Int = 5): List<CustomCharacter>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CustomCharacter)
    
    @Update
    suspend fun updateCharacter(character: CustomCharacter)
    
    @Query("UPDATE custom_characters SET lastUsed = :timestamp, useCount = useCount + 1 WHERE id = :characterId")
    suspend fun updateCharacterUsage(characterId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE custom_characters SET isActive = 0 WHERE id = :characterId")
    suspend fun deactivateCharacter(characterId: String)
    
    @Query("DELETE FROM custom_characters WHERE id = :characterId")
    suspend fun deleteCharacter(characterId: String)
    
    @Query("DELETE FROM custom_characters")
    suspend fun deleteAllCharacters()
}

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CustomCharacterDao,
    private val gson: Gson
) {
    
    fun getAllActiveCharacters(): Flow<List<Character>> =
        characterDao.getAllActiveCharacters().map { entities ->
            entities.map { it.toDomain(gson) }
        }
    
    suspend fun getCharacterById(characterId: String): Character? =
        characterDao.getCharacterById(characterId)?.toDomain(gson)
    
    suspend fun getCharactersByRole(role: CharacterRole): List<Character> =
        characterDao.getCharactersByRole(role).map { it.toDomain(gson) }
    
    suspend fun getMostUsedCharacters(limit: Int = 5): List<Character> =
        characterDao.getMostUsedCharacters(limit).map { it.toDomain(gson) }
    
    suspend fun saveCharacter(character: Character) {
        val entity = character.toEntity(gson)
        characterDao.insertCharacter(entity)
    }
    
    suspend fun updateCharacter(character: Character) {
        val entity = character.toEntity(gson)
        characterDao.updateCharacter(entity)
    }
    
    suspend fun updateCharacterUsage(characterId: String) {
        characterDao.updateCharacterUsage(characterId)
    }
    
    suspend fun deactivateCharacter(characterId: String) {
        characterDao.deactivateCharacter(characterId)
    }
    
    suspend fun deleteCharacter(characterId: String) {
        characterDao.deleteCharacter(characterId)
    }
    
    suspend fun createPresetCharacters() {
        try {
            // First remove any duplicate characters by name
            removeDuplicateCharacters()
            
            // Check if preset characters already exist to avoid duplicates
            val existingCharacters = characterDao.getAllActiveCharacters().first()
            val existingNames = existingCharacters.map { it.name }.toSet()
            
            val presets = getPresetCharacters()
            val newPresets = presets.filter { it.name !in existingNames }
            
            if (newPresets.isNotEmpty()) {
                newPresets.forEach { character ->
                    saveCharacter(character)
                }
                Timber.d("Created ${newPresets.size} new preset characters")
            } else {
                Timber.d("Preset characters already exist, skipping creation")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create preset characters")
        }
    }
    
    suspend fun removeDuplicateCharacters() {
        try {
            val allCharacters = characterDao.getAllActiveCharacters().first()
            val duplicates = allCharacters.groupBy { it.name }
                .filter { it.value.size > 1 }
            
            duplicates.forEach { (name, characters) ->
                // Keep the first one (usually the oldest), delete the rest
                val toDelete = characters.drop(1)
                toDelete.forEach { character ->
                    characterDao.deleteCharacter(character.id)
                    Timber.d("Removed duplicate character: $name (${character.id})")
                }
            }
            
            if (duplicates.isNotEmpty()) {
                Timber.d("Removed ${duplicates.values.sumOf { it.size - 1 }} duplicate characters")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove duplicate characters")
        }
    }
    
    private fun getPresetCharacters(): List<Character> = listOf(
        Character(
            name = "Luna the Explorer",
            gender = Gender.FEMALE,
            ageGroup = AgeGroup.CHILD,
            hairColor = HairColor.BROWN,
            eyeColor = EyeColor.GREEN,
            skinTone = SkinTone.MEDIUM,
            bodyType = BodyType.ATHLETIC,
            personalityTraits = listOf(PersonalityTrait.CURIOUS, PersonalityTrait.BRAVE, PersonalityTrait.ADVENTUROUS),
            specialAbilities = listOf(SpecialAbility.ENHANCED_SENSES),
            characterRole = CharacterRole.PROTAGONIST,
            backstory = "A young adventurer who loves discovering new places and solving mysteries.",
            favoriteThings = listOf("maps", "hiking", "puzzles", "animals"),
            fears = listOf("getting lost", "dark caves"),
            goals = "To explore every corner of the world and help others along the way",
            catchphrase = "Let's see what's around the next corner!",
            occupation = "Explorer",
            homeland = "Mountain Village"
        ),
        
        Character(
            name = "Professor Wise Owl",
            gender = Gender.MALE,
            ageGroup = AgeGroup.ELDERLY,
            hairColor = HairColor.GRAY,
            eyeColor = EyeColor.AMBER,
            skinTone = SkinTone.LIGHT,
            bodyType = BodyType.SLIM,
            personalityTraits = listOf(PersonalityTrait.WISE, PersonalityTrait.KIND, PersonalityTrait.CALM),
            specialAbilities = listOf(SpecialAbility.MAGIC),
            characterRole = CharacterRole.MENTOR,
            backstory = "An ancient wizard who has spent centuries studying magic and helping young heroes.",
            favoriteThings = listOf("books", "tea", "stargazing", "teaching"),
            fears = listOf("forgetting important knowledge"),
            goals = "To pass on wisdom to the next generation",
            catchphrase = "Knowledge is the greatest magic of all",
            occupation = "Wizard Teacher",
            homeland = "Enchanted Library"
        ),
        
        Character(
            name = "Spark the Inventor",
            gender = Gender.NON_BINARY,
            ageGroup = AgeGroup.TEEN,
            hairColor = HairColor.BLUE,
            eyeColor = EyeColor.VIOLET,
            skinTone = SkinTone.MEDIUM_LIGHT,
            bodyType = BodyType.AVERAGE,
            personalityTraits = listOf(PersonalityTrait.CREATIVE, PersonalityTrait.SMART, PersonalityTrait.ENERGETIC),
            specialAbilities = listOf(SpecialAbility.TECH_GENIUS),
            characterRole = CharacterRole.SIDEKICK,
            backstory = "A brilliant young inventor who creates amazing gadgets to help friends.",
            favoriteThings = listOf("building things", "robots", "electricity", "problem-solving"),
            fears = listOf("technology breaking", "being misunderstood"),
            goals = "To invent something that will change the world for the better",
            catchphrase = "There's always a solution, we just need to build it!",
            occupation = "Inventor",
            homeland = "Tech City"
        ),
        
        Character(
            name = "Kwaku Ananse",
            gender = Gender.MALE,
            ageGroup = AgeGroup.ADULT,
            hairColor = HairColor.BLACK,
            eyeColor = EyeColor.BROWN,
            skinTone = SkinTone.DARK,
            bodyType = BodyType.SLIM,
            personalityTraits = listOf(PersonalityTrait.CLEVER, PersonalityTrait.MISCHIEVOUS, PersonalityTrait.WISE),
            specialAbilities = listOf(SpecialAbility.SHAPE_SHIFTING),
            characterRole = CharacterRole.TRICKSTER,
            backstory = "The legendary spider trickster from Akan folklore who weaves stories and teaches lessons through clever schemes.",
            favoriteThings = listOf("storytelling", "riddles", "weaving webs", "outsmarting others"),
            fears = listOf("being trapped", "losing his cleverness"),
            goals = "To share wisdom through stories and teach important life lessons",
            catchphrase = "Every story has a thread of truth woven within it",
            occupation = "Storyteller & Trickster",
            homeland = "West African Forest"
        ),
        
        Character(
            name = "Captain Marina Stormwind",
            gender = Gender.FEMALE,
            ageGroup = AgeGroup.ADULT,
            hairColor = HairColor.RED,
            eyeColor = EyeColor.BLUE,
            skinTone = SkinTone.MEDIUM_LIGHT,
            bodyType = BodyType.ATHLETIC,
            personalityTraits = listOf(PersonalityTrait.BRAVE, PersonalityTrait.LOYAL, PersonalityTrait.DETERMINED),
            specialAbilities = listOf(SpecialAbility.WATER_CONTROL),
            characterRole = CharacterRole.HERO,
            backstory = "A fearless pirate captain who sails the seven seas protecting ocean creatures and fighting sea monsters.",
            favoriteThings = listOf("sailing", "treasure maps", "sea shanties", "protecting the innocent"),
            fears = listOf("deep sea trenches", "losing her crew"),
            goals = "To keep the oceans safe for all who sail them",
            catchphrase = "Fair winds and following seas!",
            occupation = "Pirate Captain",
            homeland = "Floating Island City"
        ),
        
        Character(
            name = "Zara the Dreamkeeper",
            gender = Gender.FEMALE,
            ageGroup = AgeGroup.YOUNG_ADULT,
            hairColor = HairColor.PURPLE,
            eyeColor = EyeColor.VIOLET,
            skinTone = SkinTone.MEDIUM,
            bodyType = BodyType.AVERAGE,
            personalityTraits = listOf(PersonalityTrait.GENTLE, PersonalityTrait.MYSTERIOUS, PersonalityTrait.EMPATHETIC),
            specialAbilities = listOf(SpecialAbility.DREAM_WALKING),
            characterRole = CharacterRole.GUARDIAN,
            backstory = "A mystical guardian who protects children's dreams and helps them overcome nightmares.",
            favoriteThings = listOf("peaceful sleep", "beautiful dreams", "starlight", "helping others"),
            fears = listOf("nightmares spreading", "children losing hope"),
            goals = "To ensure every child has sweet dreams and restful sleep",
            catchphrase = "In dreams, anything is possible",
            occupation = "Dream Guardian",
            homeland = "The Realm of Dreams"
        ),
        
        Character(
            name = "Rocky the Gentle Giant",
            gender = Gender.MALE,
            ageGroup = AgeGroup.ADULT,
            hairColor = HairColor.BROWN,
            eyeColor = EyeColor.HAZEL,
            skinTone = SkinTone.MEDIUM_DARK,
            bodyType = BodyType.MUSCULAR,
            personalityTraits = listOf(PersonalityTrait.KIND, PersonalityTrait.STRONG, PersonalityTrait.PROTECTIVE),
            specialAbilities = listOf(SpecialAbility.SUPER_STRENGTH),
            characterRole = CharacterRole.PROTECTOR,
            backstory = "A mountain giant with a heart of gold who uses his strength to help those in need.",
            favoriteThings = listOf("gardening", "helping others", "peaceful mountains", "making friends"),
            fears = listOf("accidentally hurting someone", "being misunderstood"),
            goals = "To show that size doesn't determine kindness",
            catchphrase = "Big hands, bigger heart",
            occupation = "Mountain Guardian",
            homeland = "Crystal Peaks"
        )
    )
    
    private fun Character.toEntity(gson: Gson) = CustomCharacter(
        id = id,
        name = name,
        gender = gender,
        ageGroup = ageGroup,
        hairColor = hairColor,
        eyeColor = eyeColor,
        skinTone = skinTone,
        bodyType = bodyType,
        personalityTraitsJson = gson.toJson(personalityTraits),
        specialAbilitiesJson = gson.toJson(specialAbilities),
        characterRole = characterRole,
        backstory = backstory,
        favoriteThings = favoriteThings.joinToString(","),
        fears = fears.joinToString(","),
        goals = goals,
        catchphrase = catchphrase,
        occupation = occupation,
        homeland = homeland,
        relationships = relationships,
        createdAt = createdAt,
        lastUsed = lastUsed,
        useCount = useCount,
        isActive = isActive
    )
    
    private fun CustomCharacter.toDomain(gson: Gson) = Character(
        id = id,
        name = name,
        gender = gender,
        ageGroup = ageGroup,
        hairColor = hairColor,
        eyeColor = eyeColor,
        skinTone = skinTone,
        bodyType = bodyType,
        personalityTraits = gson.fromJson(personalityTraitsJson, Array<PersonalityTrait>::class.java).toList(),
        specialAbilities = gson.fromJson(specialAbilitiesJson, Array<SpecialAbility>::class.java).toList(),
        characterRole = characterRole,
        backstory = backstory,
        favoriteThings = favoriteThings.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        fears = fears.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        goals = goals,
        catchphrase = catchphrase,
        occupation = occupation,
        homeland = homeland,
        relationships = relationships,
        createdAt = createdAt,
        lastUsed = lastUsed,
        useCount = useCount,
        isActive = isActive
    )
}