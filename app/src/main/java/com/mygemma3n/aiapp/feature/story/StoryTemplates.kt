package com.mygemma3n.aiapp.feature.story

import androidx.room.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Story template types
enum class TemplateType {
    HEROS_JOURNEY,      // Classic hero's journey structure
    MYSTERY,            // Mystery/detective story structure
    FRIENDSHIP,         // Stories about friendship and relationships
    ADVENTURE,          // Action-packed adventure structure
    SLICE_OF_LIFE,      // Everyday life stories
    FAIRY_TALE,         // Traditional fairy tale structure
    SCIENCE_FICTION,    // Sci-fi story frameworks
    COMING_OF_AGE,      // Growing up and learning stories
    ANIMAL_ADVENTURE,   // Stories with animal protagonists
    FAMILY_DRAMA,       // Family-centered stories
    FOLKLORE,           // Traditional folklore and wisdom tales
    SPACE_ADVENTURE     // Space exploration and alien encounters
}

// Story structure components
data class StoryBeat(
    val name: String,
    val description: String,
    val promptTemplate: String,
    val characterRoles: List<CharacterRole> = emptyList(),
    val suggestedLength: Int = 1 // Pages for this beat
)

@Entity(tableName = "story_templates")
data class StoryTemplate(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: TemplateType,
    val description: String,
    val targetAudience: StoryTarget,
    val genre: StoryGenre,
    val estimatedPages: Int,
    val beatsJson: String, // JSON array of StoryBeat
    val requiredCharacters: String, // JSON array of CharacterRole
    val optionalCharacters: String, // JSON array of CharacterRole
    val settingSuggestions: String, // Comma-separated settings
    val themeSuggestions: String, // Comma-separated themes
    val isBuiltIn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
    val isActive: Boolean = true
)

// Domain model for easier use
data class Template(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: TemplateType,
    val description: String,
    val targetAudience: StoryTarget,
    val genre: StoryGenre,
    val estimatedPages: Int,
    val beats: List<StoryBeat>,
    val requiredCharacters: List<CharacterRole>,
    val optionalCharacters: List<CharacterRole>,
    val settingSuggestions: List<String>,
    val themeSuggestions: List<String>,
    val isBuiltIn: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val useCount: Int = 0,
    val isActive: Boolean = true
) {
    fun generatePrompt(
        customCharacters: List<Character> = emptyList(),
        selectedSetting: String = "",
        selectedTheme: String = "",
        customizations: Map<String, String> = emptyMap()
    ): String {
        val characterDescriptions = if (customCharacters.isNotEmpty()) {
            "\n\nCharacters to include:\n" + customCharacters.joinToString("\n") { character ->
                "- ${character.name}: ${character.getFullDescription()}"
            }
        } else ""
        
        val settingInfo = if (selectedSetting.isNotBlank()) {
            "\nSetting: $selectedSetting"
        } else ""
        
        val themeInfo = if (selectedTheme.isNotBlank()) {
            "\nTheme: $selectedTheme"
        } else ""
        
        val structureInfo = "\n\nStory Structure (${beats.size} parts):\n" + 
            beats.mapIndexed { index, beat ->
                val customization = customizations[beat.name] ?: ""
                val customInfo = if (customization.isNotBlank()) " - $customization" else ""
                "${index + 1}. ${beat.name}: ${beat.description}$customInfo"
            }.joinToString("\n")
        
        return buildString {
            append("Create a ${type.name.lowercase().replace('_', ' ')} story suitable for ${targetAudience.name.lowercase().replace('_', ' ')} readers.")
            append(characterDescriptions)
            append(settingInfo)
            append(themeInfo)
            append(structureInfo)
            append("\n\nEnsure the story follows this structure while being engaging and age-appropriate.")
        }
    }
}

@Dao
interface StoryTemplateDao {
    @Query("SELECT * FROM story_templates WHERE isActive = 1 ORDER BY useCount DESC, name ASC")
    fun getAllActiveTemplates(): Flow<List<StoryTemplate>>
    
    @Query("SELECT * FROM story_templates WHERE type = :type AND isActive = 1")
    suspend fun getTemplatesByType(type: TemplateType): List<StoryTemplate>
    
    @Query("SELECT * FROM story_templates WHERE targetAudience = :audience AND isActive = 1")
    suspend fun getTemplatesByAudience(audience: StoryTarget): List<StoryTemplate>
    
    @Query("SELECT * FROM story_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: String): StoryTemplate?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: StoryTemplate)
    
    @Update
    suspend fun updateTemplate(template: StoryTemplate)
    
    @Query("UPDATE story_templates SET useCount = useCount + 1 WHERE id = :templateId")
    suspend fun incrementUseCount(templateId: String)
    
    @Query("DELETE FROM story_templates WHERE id = :templateId")
    suspend fun deleteTemplate(templateId: String)
    
    @Query("DELETE FROM story_templates WHERE isBuiltIn = 0")
    suspend fun deleteCustomTemplates()
}

@Singleton
class StoryTemplateRepository @Inject constructor(
    private val templateDao: StoryTemplateDao,
    private val gson: Gson
) {
    
    fun getAllActiveTemplates(): Flow<List<Template>> =
        templateDao.getAllActiveTemplates().map { entities ->
            entities.map { it.toDomain(gson) }
        }
    
    suspend fun getTemplatesByType(type: TemplateType): List<Template> =
        templateDao.getTemplatesByType(type).map { it.toDomain(gson) }
    
    suspend fun getTemplatesByAudience(audience: StoryTarget): List<Template> =
        templateDao.getTemplatesByAudience(audience).map { it.toDomain(gson) }
    
    suspend fun getTemplateById(templateId: String): Template? =
        templateDao.getTemplateById(templateId)?.toDomain(gson)
    
    suspend fun saveTemplate(template: Template) {
        val entity = template.toEntity(gson)
        templateDao.insertTemplate(entity)
    }
    
    suspend fun incrementUseCount(templateId: String) {
        templateDao.incrementUseCount(templateId)
    }
    
    suspend fun initializeBuiltInTemplates() {
        try {
            // First remove any duplicate templates by name
            removeDuplicateTemplates()
            
            // Check if built-in templates already exist to avoid duplicates
            val existingTemplates = templateDao.getAllActiveTemplates().first()
            val existingNames = existingTemplates.map { it.name }.toSet()
            
            val templates = createBuiltInTemplates()
            val newTemplates = templates.filter { it.name !in existingNames }
            
            if (newTemplates.isNotEmpty()) {
                newTemplates.forEach { template ->
                    saveTemplate(template)
                }
                Timber.d("Created ${newTemplates.size} new built-in templates")
            } else {
                Timber.d("Built-in templates already exist, skipping creation")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize built-in templates")
        }
    }
    
    suspend fun removeDuplicateTemplates() {
        try {
            val allTemplates = templateDao.getAllActiveTemplates().first()
            val duplicates = allTemplates.groupBy { it.name }
                .filter { it.value.size > 1 }
            
            duplicates.forEach { (name, templates) ->
                // Keep the first one (usually the oldest), delete the rest
                val toDelete = templates.drop(1)
                toDelete.forEach { template ->
                    templateDao.deleteTemplate(template.id)
                    Timber.d("Removed duplicate template: $name (${template.id})")
                }
            }
            
            if (duplicates.isNotEmpty()) {
                Timber.d("Removed ${duplicates.values.sumOf { it.size - 1 }} duplicate templates")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove duplicate templates")
        }
    }
    
    private fun createBuiltInTemplates(): List<Template> = listOf(
        // Hero's Journey Template
        Template(
            name = "The Hero's Adventure",
            type = TemplateType.HEROS_JOURNEY,
            description = "A classic hero's journey where the protagonist goes on an epic adventure",
            targetAudience = StoryTarget.ELEMENTARY,
            genre = StoryGenre.ADVENTURE,
            estimatedPages = 12,
            beats = listOf(
                StoryBeat(
                    "Ordinary World",
                    "Show the hero in their normal, everyday life",
                    "Introduce the main character in their regular environment. What makes them special or different?",
                    listOf(CharacterRole.PROTAGONIST),
                    2
                ),
                StoryBeat(
                    "Call to Adventure",
                    "Something happens that calls the hero to leave their comfort zone",
                    "What event or challenge forces the hero to leave their normal life behind?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.CHALLENGER),
                    2
                ),
                StoryBeat(
                    "Meeting the Mentor",
                    "The hero meets someone who provides guidance and tools",
                    "Who helps prepare the hero for their journey? What wisdom or tools do they provide?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.MENTOR),
                    2
                ),
                StoryBeat(
                    "Crossing the Threshold",
                    "The hero enters the special world of the adventure",
                    "The hero commits to the adventure and enters a new, challenging world",
                    listOf(CharacterRole.PROTAGONIST),
                    2
                ),
                StoryBeat(
                    "Tests and Trials",
                    "The hero faces challenges and makes allies",
                    "What obstacles must the hero overcome? Who do they meet along the way?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK, CharacterRole.ANTAGONIST),
                    2
                ),
                StoryBeat(
                    "The Ordeal",
                    "The hero faces their greatest fear or challenge",
                    "The climactic moment where everything is at stake. How does the hero triumph?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.ANTAGONIST),
                    1
                ),
                StoryBeat(
                    "Return Home",
                    "The hero returns transformed by their journey",
                    "How has the hero changed? What do they bring back to help others?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST, CharacterRole.MENTOR),
            optionalCharacters = listOf(CharacterRole.SIDEKICK, CharacterRole.ANTAGONIST, CharacterRole.HELPER),
            settingSuggestions = listOf("magical kingdom", "space station", "enchanted forest", "underwater city", "mountain village"),
            themeSuggestions = listOf("courage", "friendship", "believing in yourself", "helping others", "overcoming fear")
        ),
        
        // Mystery Template
        Template(
            name = "Mystery Detective",
            type = TemplateType.MYSTERY,
            description = "A puzzle-solving mystery story with clues and red herrings",
            targetAudience = StoryTarget.MIDDLE_SCHOOL,
            genre = StoryGenre.MYSTERY,
            estimatedPages = 10,
            beats = listOf(
                StoryBeat(
                    "The Crime",
                    "Something mysterious happens that needs solving",
                    "What has gone missing, been stolen, or happened mysteriously?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                ),
                StoryBeat(
                    "Initial Investigation",
                    "The detective begins gathering clues",
                    "What are the first clues? Who are the potential suspects?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    2
                ),
                StoryBeat(
                    "Red Herrings",
                    "False clues lead the detective astray",
                    "What misleading evidence throws the detective off track?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.MYSTERIOUS_STRANGER),
                    2
                ),
                StoryBeat(
                    "New Evidence",
                    "A breakthrough clue points toward the truth",
                    "What crucial evidence helps solve the case?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.HELPER),
                    2
                ),
                StoryBeat(
                    "The Chase",
                    "The detective pursues the culprit",
                    "How does the detective catch up with the person responsible?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.ANTAGONIST),
                    2
                ),
                StoryBeat(
                    "The Reveal",
                    "The mystery is solved and explained",
                    "How does the detective explain how they solved the mystery?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST),
            optionalCharacters = listOf(CharacterRole.SIDEKICK, CharacterRole.ANTAGONIST, CharacterRole.HELPER, CharacterRole.MYSTERIOUS_STRANGER),
            settingSuggestions = listOf("school", "neighborhood", "library", "museum", "amusement park"),
            themeSuggestions = listOf("logic and deduction", "persistence", "truth", "justice", "teamwork")
        ),
        
        // Friendship Template
        Template(
            name = "Friendship Adventure",
            type = TemplateType.FRIENDSHIP,
            description = "A heartwarming story about the power of friendship",
            targetAudience = StoryTarget.ELEMENTARY,
            genre = StoryGenre.FRIENDSHIP,
            estimatedPages = 8,
            beats = listOf(
                StoryBeat(
                    "Meeting",
                    "Two characters meet for the first time",
                    "How do the main characters first encounter each other? What are their first impressions?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    1
                ),
                StoryBeat(
                    "Getting to Know Each Other",
                    "The characters discover they have things in common",
                    "What do they learn about each other? What do they both enjoy?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    2
                ),
                StoryBeat(
                    "Fun Times",
                    "The friends have enjoyable experiences together",
                    "What adventures or activities do they share? How do they have fun?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    2
                ),
                StoryBeat(
                    "Conflict",
                    "Something threatens to break up the friendship",
                    "What causes tension between the friends? What challenges their bond?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK, CharacterRole.CHALLENGER),
                    2
                ),
                StoryBeat(
                    "Resolution",
                    "The friends work through their problems and grow closer",
                    "How do they resolve their differences? What do they learn about friendship?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
            optionalCharacters = listOf(CharacterRole.HELPER, CharacterRole.CHALLENGER),
            settingSuggestions = listOf("playground", "school", "summer camp", "neighborhood", "park"),
            themeSuggestions = listOf("friendship", "loyalty", "forgiveness", "understanding", "kindness")
        ),
        
        // Fairy Tale Template
        Template(
            name = "Modern Fairy Tale",
            type = TemplateType.FAIRY_TALE,
            description = "A classic fairy tale structure with modern twists",
            targetAudience = StoryTarget.KINDERGARTEN,
            genre = StoryGenre.FAIRYTALE,
            estimatedPages = 6,
            beats = listOf(
                StoryBeat(
                    "Once Upon a Time",
                    "Introduce the main character and their situation",
                    "Who is the main character and what is their life like?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                ),
                StoryBeat(
                    "The Problem",
                    "Something goes wrong or creates a challenge",
                    "What problem or challenge does the main character face?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.ANTAGONIST),
                    1
                ),
                StoryBeat(
                    "The Helper",
                    "A magical helper or wise character offers assistance",
                    "Who helps the main character? What special power or wisdom do they offer?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.WISE_GUIDE),
                    1
                ),
                StoryBeat(
                    "The Journey",
                    "The character sets off to solve their problem",
                    "What does the character need to do? Where do they go?",
                    listOf(CharacterRole.PROTAGONIST),
                    2
                ),
                StoryBeat(
                    "Happy Ending",
                    "The problem is solved and everyone lives happily",
                    "How is the problem resolved? What happy ending awaits?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST),
            optionalCharacters = listOf(CharacterRole.WISE_GUIDE, CharacterRole.ANTAGONIST, CharacterRole.HELPER),
            settingSuggestions = listOf("enchanted forest", "magical castle", "fairy garden", "rainbow bridge", "cloud kingdom"),
            themeSuggestions = listOf("good vs evil", "kindness", "magic", "believing in yourself", "helping others")
        ),
        
        // Animal Adventure Template
        Template(
            name = "Animal Friends",
            type = TemplateType.ANIMAL_ADVENTURE,
            description = "An adventure story featuring animal characters",
            targetAudience = StoryTarget.ELEMENTARY,
            genre = StoryGenre.ADVENTURE,
            estimatedPages = 8,
            beats = listOf(
                StoryBeat(
                    "Life in the Wild",
                    "Show the animals in their natural habitat",
                    "What is life like for the animal characters in their home?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                ),
                StoryBeat(
                    "The Threat",
                    "Something threatens their home or way of life",
                    "What danger faces the animals and their environment?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.ANTAGONIST),
                    1
                ),
                StoryBeat(
                    "Gathering Allies",
                    "The animals work together to face the challenge",
                    "How do different animals contribute their unique abilities?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.HELPER),
                    2
                ),
                StoryBeat(
                    "The Plan",
                    "The animals devise a clever solution",
                    "What creative plan do the animals come up with?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.HELPER),
                    2
                ),
                StoryBeat(
                    "Working Together",
                    "The animals execute their plan and save the day",
                    "How does teamwork and each animal's special skills save the day?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.HELPER),
                    2
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST),
            optionalCharacters = listOf(CharacterRole.HELPER, CharacterRole.ANTAGONIST, CharacterRole.WISE_GUIDE),
            settingSuggestions = listOf("forest", "jungle", "ocean", "farm", "zoo", "safari"),
            themeSuggestions = listOf("teamwork", "protecting nature", "friendship", "courage", "using your talents")
        ),
        
        // Folklore Wisdom Template
        Template(
            name = "Wisdom Tales",
            type = TemplateType.FOLKLORE,
            description = "Traditional folklore stories that teach important lessons through clever characters like Ananse",
            targetAudience = StoryTarget.ELEMENTARY,
            genre = StoryGenre.FAIRYTALE,
            estimatedPages = 8,
            beats = listOf(
                StoryBeat(
                    "The Lesson Needed",
                    "Someone in the community needs to learn an important lesson",
                    "What problem or behavior needs to be addressed? Who needs to learn something important?",
                    listOf(CharacterRole.CHALLENGER),
                    1
                ),
                StoryBeat(
                    "The Clever Plan",
                    "A wise trickster character devises a clever scheme",
                    "How does the trickster plan to teach the lesson? What clever trick will they use?",
                    listOf(CharacterRole.TRICKSTER),
                    2
                ),
                StoryBeat(
                    "Setting the Trap",
                    "The trickster puts their plan into motion",
                    "How does the trickster set up their clever scheme? What bait do they use?",
                    listOf(CharacterRole.TRICKSTER, CharacterRole.CHALLENGER),
                    2
                ),
                StoryBeat(
                    "The Revelation",
                    "The truth is revealed and the lesson is learned",
                    "How does the trick reveal the truth? What does everyone learn?",
                    listOf(CharacterRole.TRICKSTER, CharacterRole.CHALLENGER),
                    2
                ),
                StoryBeat(
                    "Wisdom Shared",
                    "The community benefits from the lesson learned",
                    "How does the lesson help everyone? What wisdom is passed on?",
                    listOf(CharacterRole.TRICKSTER),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.TRICKSTER),
            optionalCharacters = listOf(CharacterRole.CHALLENGER, CharacterRole.WISE_GUIDE, CharacterRole.HELPER),
            settingSuggestions = listOf("African village", "forest clearing", "marketplace", "river crossing", "ancient tree"),
            themeSuggestions = listOf("wisdom", "humility", "sharing", "honesty", "respecting others", "learning from mistakes")
        ),
        
        // Space Adventure Template
        Template(
            name = "Cosmic Explorer",
            type = TemplateType.SPACE_ADVENTURE,
            description = "An exciting space adventure with alien worlds and futuristic technology",
            targetAudience = StoryTarget.MIDDLE_SCHOOL,
            genre = StoryGenre.SCIENCE_FICTION,
            estimatedPages = 10,
            beats = listOf(
                StoryBeat(
                    "Mission Briefing",
                    "The space explorer receives an important mission",
                    "What important mission must be completed? What worlds need exploring?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.MENTOR),
                    1
                ),
                StoryBeat(
                    "Launch into Space",
                    "The journey begins with departure from Earth",
                    "What spaceship and technology do they use? How do they feel leaving home?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    1
                ),
                StoryBeat(
                    "First Discovery",
                    "The explorer encounters their first alien world",
                    "What strange and wonderful things do they discover? What challenges arise?",
                    listOf(CharacterRole.PROTAGONIST),
                    2
                ),
                StoryBeat(
                    "Alien Encounter",
                    "The explorer meets alien life forms",
                    "What kind of aliens do they meet? Are they friendly or hostile?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.MYSTERIOUS_STRANGER),
                    2
                ),
                StoryBeat(
                    "Crisis in Space",
                    "A dangerous situation threatens the mission",
                    "What goes wrong? How does technology fail or alien danger emerge?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.ANTAGONIST),
                    2
                ),
                StoryBeat(
                    "Ingenious Solution",
                    "Clever thinking and technology save the day",
                    "How does the explorer use science and creativity to solve the problem?",
                    listOf(CharacterRole.PROTAGONIST, CharacterRole.SIDEKICK),
                    1
                ),
                StoryBeat(
                    "Return to Earth",
                    "The explorer returns home with new knowledge",
                    "What amazing discoveries do they bring back? How has the journey changed them?",
                    listOf(CharacterRole.PROTAGONIST),
                    1
                )
            ),
            requiredCharacters = listOf(CharacterRole.PROTAGONIST),
            optionalCharacters = listOf(CharacterRole.SIDEKICK, CharacterRole.MENTOR, CharacterRole.ANTAGONIST, CharacterRole.MYSTERIOUS_STRANGER),
            settingSuggestions = listOf("space station", "alien planet", "asteroid field", "spaceship", "distant galaxy", "alien city"),
            themeSuggestions = listOf("exploration", "scientific discovery", "courage", "problem-solving", "friendship across differences", "protecting the environment")
        )
    )
    
    private fun Template.toEntity(gson: Gson) = StoryTemplate(
        id = id,
        name = name,
        type = type,
        description = description,
        targetAudience = targetAudience,
        genre = genre,
        estimatedPages = estimatedPages,
        beatsJson = gson.toJson(beats),
        requiredCharacters = gson.toJson(requiredCharacters),
        optionalCharacters = gson.toJson(optionalCharacters),
        settingSuggestions = settingSuggestions.joinToString(","),
        themeSuggestions = themeSuggestions.joinToString(","),
        isBuiltIn = isBuiltIn,
        createdAt = createdAt,
        useCount = useCount,
        isActive = isActive
    )
    
    private fun StoryTemplate.toDomain(gson: Gson) = Template(
        id = id,
        name = name,
        type = type,
        description = description,
        targetAudience = targetAudience,
        genre = genre,
        estimatedPages = estimatedPages,
        beats = gson.fromJson(beatsJson, Array<StoryBeat>::class.java).toList(),
        requiredCharacters = gson.fromJson(requiredCharacters, Array<CharacterRole>::class.java).toList(),
        optionalCharacters = gson.fromJson(optionalCharacters, Array<CharacterRole>::class.java).toList(),
        settingSuggestions = settingSuggestions.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        themeSuggestions = themeSuggestions.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        isBuiltIn = isBuiltIn,
        createdAt = createdAt,
        useCount = useCount,
        isActive = isActive
    )
}