package com.example.mygemma3n.data.local

import android.content.Context
import com.example.mygemma3n.data.ExplanationDepth
import com.example.mygemma3n.data.LearningStyle
import com.example.mygemma3n.data.TextEmbeddingService
import com.example.mygemma3n.feature.quiz.Difficulty
import com.example.mygemma3n.shared_utilities.OfflineRAG
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TutorOfflineRAGExtension @Inject constructor(
    private val offlineRAG: OfflineRAG,
    private val vectorDB: OptimizedVectorDatabase,
    private val embeddingService: TextEmbeddingService,
    @ApplicationContext private val context: Context
) {

    suspend fun setupEducationalContent() = withContext(Dispatchers.IO) {
        val documents = loadEducationalDocuments()
        offlineRAG.setup(documents)
    }

    private suspend fun loadEducationalDocuments(): List<OfflineRAG.Document> {
        val documents = mutableListOf<OfflineRAG.Document>()

        // Load grade-specific content
        for (grade in 1..12) {
            documents.addAll(loadGradeContent(grade))
        }

        // Load subject-specific resources
        OfflineRAG.Subject.entries.forEach { subject ->
            documents.addAll(loadSubjectContent(subject))
        }

        return documents
    }

    private fun loadGradeContent(grade: Int): List<OfflineRAG.Document> {
        val documents = mutableListOf<OfflineRAG.Document>()

        // Math content by grade
        if (grade <= 6) {
            documents.add(createMathDocument(
                id = "math_grade_${grade}_arithmetic",
                gradeLevel = grade,
                topic = "Basic Arithmetic",
                content = getArithmeticContent(grade)
            ))
        } else {
            documents.add(createMathDocument(
                id = "math_grade_${grade}_algebra",
                gradeLevel = grade,
                topic = "Algebra",
                content = getAlgebraContent(grade)
            ))
        }

        // Science content by grade
        documents.add(createScienceDocument(
            id = "science_grade_${grade}_general",
            gradeLevel = grade,
            topic = getScienceTopicForGrade(grade),
            content = getScienceContent(grade)
        ))

        return documents
    }

    private fun loadSubjectContent(subject: OfflineRAG.Subject): List<OfflineRAG.Document> {
        return when (subject) {
            OfflineRAG.Subject.MATHEMATICS -> loadMathematicsContent()
            OfflineRAG.Subject.SCIENCE -> loadScienceContent()
            OfflineRAG.Subject.ENGLISH -> loadEnglishContent()
            else -> emptyList()
        }
    }

    private fun loadMathematicsContent(): List<OfflineRAG.Document> {
        return listOf(
            // Algebra
            OfflineRAG.Document(
                id = "math_algebra_basics",
                content = """
                    Algebra Fundamentals:
                    
                    1. Variables and Expressions
                    - A variable is a letter that represents an unknown number
                    - Example: In 2x + 3, 'x' is the variable
                    - An expression combines numbers, variables, and operations
                    
                    2. Solving Linear Equations
                    - Goal: Find the value of the variable
                    - Use inverse operations to isolate the variable
                    - Example: 2x + 3 = 7
                      Step 1: Subtract 3 from both sides: 2x = 4
                      Step 2: Divide both sides by 2: x = 2
                    
                    3. Common Mistakes
                    - Forgetting to apply operations to both sides
                    - Sign errors when moving terms
                    - Not checking your answer
                """.trimIndent(),
                metadata = OfflineRAG.DocumentMetadata(
                    subject = OfflineRAG.Subject.MATHEMATICS,
                    title = "Algebra Basics",
                    source = "Educational Content Pack",
                    difficulty = "beginner",
                    tags = listOf("algebra", "equations", "variables")
                )
            ),

            // Geometry
            OfflineRAG.Document(
                id = "math_geometry_basics",
                content = """
                    Geometry Fundamentals:
                    
                    1. Basic Shapes
                    - Triangle: 3 sides, angles sum to 180°
                    - Square: 4 equal sides, 4 right angles
                    - Circle: All points equidistant from center
                    
                    2. Perimeter and Area
                    - Perimeter: Distance around the shape
                    - Area: Space inside the shape
                    - Rectangle: P = 2(l+w), A = l×w
                    - Circle: C = 2πr, A = πr²
                    
                    3. Pythagorean Theorem
                    - For right triangles: a² + b² = c²
                    - 'c' is the hypotenuse (longest side)
                    - Example: 3² + 4² = 9 + 16 = 25 = 5²
                """.trimIndent(),
                metadata = OfflineRAG.DocumentMetadata(
                    subject = OfflineRAG.Subject.MATHEMATICS,
                    title = "Geometry Basics",
                    source = "Educational Content Pack",
                    difficulty = "beginner",
                    tags = listOf("geometry", "shapes", "area", "perimeter")
                )
            )
        )
    }

    private fun loadScienceContent(): List<OfflineRAG.Document> {
        return listOf(
            // Biology
            OfflineRAG.Document(
                id = "science_biology_cells",
                content = """
                    Cell Biology:
                    
                    1. Cell Structure
                    - All living things are made of cells
                    - Plant cells have cell walls and chloroplasts
                    - Animal cells have centrioles
                    - Both have: nucleus, mitochondria, cell membrane
                    
                    2. Cell Functions
                    - Nucleus: Controls cell activities, contains DNA
                    - Mitochondria: Produces energy (ATP)
                    - Chloroplasts: Photosynthesis in plants
                    - Cell membrane: Controls what enters/exits
                    
                    3. Cell Division
                    - Mitosis: Creates identical cells for growth
                    - Meiosis: Creates sex cells with half the chromosomes
                """.trimIndent(),
                metadata = OfflineRAG.DocumentMetadata(
                    subject = OfflineRAG.Subject.SCIENCE,
                    title = "Cell Biology",
                    source = "Educational Content Pack",
                    difficulty = "intermediate",
                    tags = listOf("biology", "cells", "life-science")
                )
            ),

            // Physics
            OfflineRAG.Document(
                id = "science_physics_motion",
                content = """
                    Physics - Motion and Forces:
                    
                    1. Newton's Laws of Motion
                    - First Law: Objects at rest stay at rest unless acted upon
                    - Second Law: F = ma (Force = mass × acceleration)
                    - Third Law: Every action has equal and opposite reaction
                    
                    2. Types of Motion
                    - Linear: Motion in a straight line
                    - Circular: Motion in a circle
                    - Projectile: Motion under gravity
                    
                    3. Key Formulas
                    - Speed = Distance/Time
                    - Acceleration = Change in velocity/Time
                    - Momentum = Mass × Velocity
                """.trimIndent(),
                metadata = OfflineRAG.DocumentMetadata(
                    subject = OfflineRAG.Subject.SCIENCE,
                    title = "Physics - Motion",
                    source = "Educational Content Pack",
                    difficulty = "intermediate",
                    tags = listOf("physics", "motion", "forces", "newton")
                )
            )
        )
    }

    private fun loadEnglishContent(): List<OfflineRAG.Document> {
        return listOf(
            OfflineRAG.Document(
                id = "english_grammar_basics",
                content = """
                    English Grammar Fundamentals:
                    
                    1. Parts of Speech
                    - Noun: Person, place, thing, or idea
                    - Verb: Action or state of being
                    - Adjective: Describes a noun
                    - Adverb: Describes a verb, adjective, or another adverb
                    
                    2. Sentence Structure
                    - Subject + Verb + Object
                    - Example: "The cat (S) chased (V) the mouse (O)"
                    - Every sentence needs at least a subject and verb
                    
                    3. Common Errors
                    - Subject-verb agreement: "He runs" not "He run"
                    - Tense consistency: Don't switch tenses mid-sentence
                    - Run-on sentences: Use periods or conjunctions
                """.trimIndent(),
                metadata = OfflineRAG.DocumentMetadata(
                    subject = OfflineRAG.Subject.ENGLISH,
                    title = "Grammar Basics",
                    source = "Educational Content Pack",
                    difficulty = "beginner",
                    tags = listOf("grammar", "parts-of-speech", "sentences")
                )
            )
        )
    }

    // Helper methods
    private fun createMathDocument(
        id: String,
        gradeLevel: Int,
        topic: String,
        content: String
    ): OfflineRAG.Document {
        return OfflineRAG.Document(
            id = id,
            content = content,
            metadata = OfflineRAG.DocumentMetadata(
                subject = OfflineRAG.Subject.MATHEMATICS,
                title = "$topic - Grade $gradeLevel",
                source = "Grade $gradeLevel Curriculum",
                difficulty = when (gradeLevel) {
                    in 1..4 -> "easy"
                    in 5..8 -> "intermediate"
                    else -> "advanced"
                },
                tags = listOf("grade-$gradeLevel", topic.lowercase())
            )
        )
    }

    private fun createScienceDocument(
        id: String,
        gradeLevel: Int,
        topic: String,
        content: String
    ): OfflineRAG.Document {
        return OfflineRAG.Document(
            id = id,
            content = content,
            metadata = OfflineRAG.DocumentMetadata(
                subject = OfflineRAG.Subject.SCIENCE,
                title = "$topic - Grade $gradeLevel",
                source = "Grade $gradeLevel Curriculum",
                difficulty = when (gradeLevel) {
                    in 1..4 -> "easy"
                    in 5..8 -> "intermediate"
                    else -> "advanced"
                },
                tags = listOf("grade-$gradeLevel", topic.lowercase())
            )
        )
    }

    private fun getArithmeticContent(grade: Int): String {
        return when (grade) {
            1 -> "Addition and subtraction up to 20..."
            2 -> "Addition and subtraction up to 100, introduction to multiplication..."
            3 -> "Multiplication and division, fractions basics..."
            4 -> "Multi-digit operations, decimals, basic geometry..."
            5 -> "Advanced fractions, percentages, ratios..."
            6 -> "Pre-algebra concepts, equations, proportions..."
            else -> "Advanced arithmetic concepts..."
        }
    }

    private fun getAlgebraContent(grade: Int): String {
        return when (grade) {
            7 -> "Introduction to variables, simple equations..."
            8 -> "Linear equations, graphing, systems of equations..."
            9 -> "Quadratic equations, polynomials, factoring..."
            10 -> "Advanced algebra, functions, complex numbers..."
            11 -> "Trigonometry, logarithms, sequences..."
            12 -> "Pre-calculus, limits, derivatives introduction..."
            else -> "Advanced mathematical concepts..."
        }
    }

    private fun getScienceTopicForGrade(grade: Int): String {
        return when (grade) {
            in 1..3 -> "Basic Life Science"
            in 4..6 -> "Earth and Space Science"
            in 7..8 -> "Physical Science"
            in 9..10 -> "Biology"
            11 -> "Chemistry"
            12 -> "Physics"
            else -> "General Science"
        }
    }

    private fun getScienceContent(grade: Int): String {
        return "Grade-appropriate science content for grade $grade..."
    }

    // Query method with grade-level filtering
    suspend fun queryEducationalContent(
        query: String,
        subject: OfflineRAG.Subject,
        gradeLevel: Int
    ): String {
        val enrichedQuery = "$query grade:$gradeLevel"

        return offlineRAG.queryWithContext(
            query = enrichedQuery,
            subject = subject
        )
    }
}