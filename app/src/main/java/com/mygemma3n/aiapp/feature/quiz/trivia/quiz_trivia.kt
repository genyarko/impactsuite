package com.mygemma3n.aiapp.feature.quiz.trivia

import com.mygemma3n.aiapp.feature.quiz.Difficulty
import com.mygemma3n.aiapp.feature.quiz.Question
import com.mygemma3n.aiapp.feature.quiz.QuestionType
import kotlin.random.Random

/**
 * Trivia questions to entertain users while quiz is generating
 */
object QuizTrivia {
    
    /**
     * Pool of trivia questions across different categories
     */
    private val triviaQuestions = listOf(
        // General Knowledge
        Question(
            questionText = "What is the largest mammal in the world?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Elephant", "Blue Whale", "Giraffe", "Hippopotamus"),
            correctAnswer = "Blue Whale",
            explanation = "Blue whales can grow up to 100 feet long and weigh as much as 200 tons!",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("animals", "marine-life")
        ),
        Question(
            questionText = "How many hearts does an octopus have?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("1", "2", "3", "4"),
            correctAnswer = "3",
            explanation = "Octopuses have three hearts! Two pump blood to the gills, and one pumps blood to the rest of the body.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("marine-life", "biology")
        ),
        Question(
            questionText = "The Great Wall of China is visible from space.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "False",
            explanation = "This is a common myth! The Great Wall is actually not visible from space with the naked eye.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("geography", "space")
        ),
        Question(
            questionText = "What is the fastest land animal?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Lion", "Cheetah", "Leopard", "Tiger"),
            correctAnswer = "Cheetah",
            explanation = "Cheetahs can run up to 70 mph in short bursts covering distances up to 1,600 feet!",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("animals", "speed")
        ),
        Question(
            questionText = "Which planet is known as the 'Red Planet'?",
            questionType = QuestionType.FILL_IN_BLANK,
            options = emptyList(),
            correctAnswer = "Mars",
            explanation = "Mars appears red due to iron oxide (rust) on its surface.",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("space", "planets")
        ),
        Question(
            questionText = "What is the smallest country in the world?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Monaco", "Nauru", "Vatican City", "San Marino"),
            correctAnswer = "Vatican City",
            explanation = "Vatican City is only 0.17 square miles (0.44 square kilometers)!",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("geography", "countries")
        ),
        Question(
            questionText = "Honey never spoils.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "Archaeologists have found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still perfectly edible!",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("food", "history")
        ),
        Question(
            questionText = "What is the hardest natural substance on Earth?",
            questionType = QuestionType.FILL_IN_BLANK,
            options = emptyList(),
            correctAnswer = "Diamond",
            explanation = "Diamond ranks 10 on the Mohs scale of mineral hardness.",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("geology", "minerals")
        ),
        Question(
            questionText = "Which ocean is the largest?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Atlantic Ocean", "Indian Ocean", "Arctic Ocean", "Pacific Ocean"),
            correctAnswer = "Pacific Ocean",
            explanation = "The Pacific Ocean covers about 46% of the Earth's water surface!",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("geography", "oceans")
        ),
        Question(
            questionText = "A group of flamingos is called a _____.",
            questionType = QuestionType.FILL_IN_BLANK,
            options = emptyList(),
            correctAnswer = "flamboyance",
            explanation = "A group of flamingos is called a flamboyance! Other collective nouns include a stand or a pat.",
            difficulty = Difficulty.HARD,
            conceptsCovered = listOf("animals", "vocabulary")
        ),
        Question(
            questionText = "Which element has the chemical symbol 'Au'?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("Silver", "Gold", "Aluminum", "Argon"),
            correctAnswer = "Gold",
            explanation = "Au comes from the Latin word 'aurum' meaning gold.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("chemistry", "elements")
        ),
        Question(
            questionText = "Bananas are berries, but strawberries are not.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "Botanically speaking, bananas are berries because they develop from a single flower with one ovary. Strawberries are not true berries!",
            difficulty = Difficulty.HARD,
            conceptsCovered = listOf("botany", "fruits")
        ),
        Question(
            questionText = "What is the most spoken language in the world?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("English", "Spanish", "Mandarin Chinese", "Hindi"),
            correctAnswer = "Mandarin Chinese",
            explanation = "Mandarin Chinese is spoken by over 900 million people worldwide!",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("languages", "culture")
        ),
        Question(
            questionText = "Lightning never strikes the same place twice.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "False",
            explanation = "Lightning can and often does strike the same place multiple times! The Empire State Building gets struck about 25 times per year.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("weather", "physics")
        ),
        Question(
            questionText = "What is the longest river in the world?",
            questionType = QuestionType.FILL_IN_BLANK,
            options = emptyList(),
            correctAnswer = "Nile",
            explanation = "The Nile River in Africa is about 4,135 miles (6,650 kilometers) long.",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("geography", "rivers")
        ),
        // Fun Science Facts
        Question(
            questionText = "A single cloud can weigh more than a million pounds.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "An average cumulus cloud weighs about 1.1 million pounds - that's like 100 elephants!",
            difficulty = Difficulty.HARD,
            conceptsCovered = listOf("weather", "physics")
        ),
        Question(
            questionText = "What percentage of your brain do you actually use?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("10%", "50%", "Nearly 100%", "25%"),
            correctAnswer = "Nearly 100%",
            explanation = "The '10% of your brain' myth is false! Brain imaging shows we use virtually all of our brain.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("neuroscience", "myths")
        ),
        Question(
            questionText = "Sharks are older than trees.",
            questionType = QuestionType.TRUE_FALSE,
            options = listOf("True", "False"),
            correctAnswer = "True",
            explanation = "Sharks have existed for about 400 million years, while trees appeared around 350 million years ago!",
            difficulty = Difficulty.HARD,
            conceptsCovered = listOf("evolution", "history")
        ),
        Question(
            questionText = "What is the only mammal capable of true flight?",
            questionType = QuestionType.FILL_IN_BLANK,
            options = emptyList(),
            correctAnswer = "Bat",
            explanation = "Bats are the only mammals that can truly fly (not just glide like flying squirrels).",
            difficulty = Difficulty.EASY,
            conceptsCovered = listOf("animals", "flight")
        ),
        Question(
            questionText = "How many bones do babies have when they're born?",
            questionType = QuestionType.MULTIPLE_CHOICE,
            options = listOf("206", "270", "300", "150"),
            correctAnswer = "270",
            explanation = "Babies are born with about 270 bones, but many fuse together as they grow, leaving adults with 206 bones.",
            difficulty = Difficulty.MEDIUM,
            conceptsCovered = listOf("human-body", "development")
        )
    )
    
    /**
     * Grade-appropriate trivia questions for younger students
     */
    private val gradeLevelTrivia: Map<Int, List<Question>> = mapOf(
        // Kindergarten (Grade 0)
        0 to listOf(
            Question(
                questionText = "Which shape has three sides?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Triangle", "Square", "Circle", "Rectangle"),
                correctAnswer = "Triangle",
                explanation = "A triangle has exactly three sides.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "True or False: The sun is a star.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "The sun is our closest star.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "What do we call the place where we live with our family?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("School", "Store", "Home", "Park"),
                correctAnswer = "Home",
                explanation = "Home is where we live with our family.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "Fill in the blank: We use our _____ to see things.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "eyes",
                explanation = "Eyes are the sense organs we use for seeing.",
                difficulty = Difficulty.EASY
            )
        ),

        // Grade 1
        1 to listOf(
            Question(
                questionText = "How many legs does an insect have?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("4", "6", "8", "10"),
                correctAnswer = "6",
                explanation = "All insects have six legs.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "Fill in the blank: The opposite of 'hot' is _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "cold",
                explanation = "'Cold' is the antonym of 'hot'.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "What is 3 + 2?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("4", "5", "6", "7"),
                correctAnswer = "5",
                explanation = "Three plus two equals five.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "True or False: We should recycle to help the Earth.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Recycling helps protect our environment by reusing materials.",
                difficulty = Difficulty.EASY
            )
        ),

        // Grade 2
        2 to listOf(
            Question(
                questionText = "Which planet do we live on?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Mars", "Earth", "Venus", "Jupiter"),
                correctAnswer = "Earth",
                explanation = "Humans live on planet Earth.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "True or False: A sentence always begins with a capital letter.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Capital letters start every sentence in standard English writing.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "What is 8 - 3?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("4", "5", "6", "7"),
                correctAnswer = "5",
                explanation = "Eight minus three equals five.",
                difficulty = Difficulty.EASY
            ),
            Question(
                questionText = "Fill in the blank: People who help us in our community are called community _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "helpers",
                explanation = "Community helpers like firefighters and teachers help make our community better.",
                difficulty = Difficulty.EASY
            )
        ),

        // Grade 3
        3 to listOf(
            Question(
                questionText = "What is 7 × 3?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("21", "24", "18", "30"),
                correctAnswer = "21",
                explanation = "Seven times three equals twenty‑one.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: The process by which plants make food is called _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "photosynthesis",
                explanation = "Plants convert sunlight into energy through photosynthesis.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "What do we call a story that is made up and not real?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Biography", "Fiction", "News", "History"),
                correctAnswer = "Fiction",
                explanation = "Fiction stories are imaginary and not based on real events.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: A map uses symbols to show different places.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Maps use symbols like stars for capitals and mountains for elevation.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 4
        4 to listOf(
            Question(
                questionText = "Which continent is the Sahara Desert located on?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Asia", "Africa", "Australia", "South America"),
                correctAnswer = "Africa",
                explanation = "The Sahara covers much of northern Africa.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: Sound travels slower than light.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Light travels much faster than sound waves.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "What is the perimeter of a rectangle with length 6 and width 4?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("10", "20", "24", "30"),
                correctAnswer = "20",
                explanation = "Perimeter = 2(length + width) = 2(6 + 4) = 20.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: Ancient Egypt was famous for building _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "pyramids",
                explanation = "The ancient Egyptians built pyramids as tombs for their pharaohs.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 5
        5 to listOf(
            Question(
                questionText = "Which fraction is equivalent to 0.5?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("1/3", "1/2", "2/3", "3/4"),
                correctAnswer = "1/2",
                explanation = "One‑half equals 0.5 in decimal form.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: The largest organ in the human body is the _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "skin",
                explanation = "Skin is the body's largest organ by area and weight.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "What is the main reason people migrate from one place to another?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Weather", "Better opportunities", "Tourism", "Sports"),
                correctAnswer = "Better opportunities",
                explanation = "People usually migrate seeking better jobs, education, or living conditions.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: Entrepreneurs start their own businesses.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Entrepreneurs create and run their own business ventures.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 6
        6 to listOf(
            Question(
                questionText = "Which ancient civilization built the pyramids at Giza?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Romans", "Greeks", "Aztecs", "Egyptians"),
                correctAnswer = "Egyptians",
                explanation = "The pyramids were constructed in Ancient Egypt.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: In a food web, producers make their own food from sunlight.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Green plants are producers that carry out photosynthesis.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: When resources are limited, we face the economic problem of _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "scarcity",
                explanation = "Scarcity occurs when there aren't enough resources to satisfy all wants and needs.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "What do we call lines that run east to west on a map?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Longitude", "Latitude", "Meridians", "Equator"),
                correctAnswer = "Latitude",
                explanation = "Lines of latitude run horizontally (east to west) and measure distance from the equator.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 7
        7 to listOf(
            Question(
                questionText = "What is the value of π (pi) rounded to two decimal places?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("3.12", "3.14", "3.16", "3.18"),
                correctAnswer = "3.14",
                explanation = "Pi is approximately 3.14159… which rounds to 3.14.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: The powerhouse of the cell is the _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "mitochondria",
                explanation = "Mitochondria generate ATP for cellular energy.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Which type of government allows citizens to vote for their leaders?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Monarchy", "Democracy", "Dictatorship", "Oligarchy"),
                correctAnswer = "Democracy",
                explanation = "In a democracy, citizens elect their representatives through voting.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: Climate change affects different biomes around the world.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Climate change impacts various ecosystems and biomes globally.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 8
        8 to listOf(
            Question(
                questionText = "Which layer of Earth is liquid and composed mainly of iron and nickel?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Crust", "Mantle", "Outer core", "Inner core"),
                correctAnswer = "Outer core",
                explanation = "Earth's outer core is molten metal generating the magnetic field.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "True or False: DNA is double‑stranded.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "DNA forms a double helix with two complementary strands.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "Fill in the blank: The American Revolution was fought to gain independence from _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "Britain",
                explanation = "The American colonies fought for independence from British rule.",
                difficulty = Difficulty.MEDIUM
            ),
            Question(
                questionText = "What happens to population when birth rate exceeds death rate?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Population decreases", "Population increases", "Population stays same", "Population migrates"),
                correctAnswer = "Population increases",
                explanation = "When more people are born than die, the population grows.",
                difficulty = Difficulty.MEDIUM
            )
        ),

        // Grade 9
        9 to listOf(
            Question(
                questionText = "Which law states that force equals mass times acceleration?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Newton's First Law", "Newton's Second Law", "Newton's Third Law", "Law of Gravitation"),
                correctAnswer = "Newton's Second Law",
                explanation = "F = m × a is Newton's Second Law of Motion.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Fill in the blank: The chemical symbol for sodium is _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "Na",
                explanation = "'Na' comes from the Latin name 'natrium'.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "What literary device compares two things using 'like' or 'as'?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Metaphor", "Simile", "Alliteration", "Personification"),
                correctAnswer = "Simile",
                explanation = "A simile makes comparisons using 'like' or 'as'.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "True or False: The French Revolution influenced many other revolutions around the world.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "The French Revolution's ideals of liberty and equality inspired global democratic movements.",
                difficulty = Difficulty.HARD
            )
        ),

        // Grade 10
        10 to listOf(
            Question(
                questionText = "What economic term describes a prolonged period of rising prices?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Deflation", "Inflation", "Recession", "Stagnation"),
                correctAnswer = "Inflation",
                explanation = "Inflation is the sustained increase in the general price level.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "True or False: Covalent bonds involve sharing electrons between atoms.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Atoms in covalent bonds share electron pairs.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Fill in the blank: Shakespeare wrote plays during the _____ period.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "Elizabethan",
                explanation = "Shakespeare wrote during the Elizabethan era, named after Queen Elizabeth I.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Which geographic process forms river deltas?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Erosion", "Deposition", "Weathering", "Volcanic activity"),
                correctAnswer = "Deposition",
                explanation = "Deltas form when rivers deposit sediment as they enter larger bodies of water.",
                difficulty = Difficulty.HARD
            )
        ),

        // Grade 11
        11 to listOf(
            Question(
                questionText = "Which author wrote '1984'?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Aldous Huxley", "George Orwell", "Ray Bradbury", "Margaret Atwood"),
                correctAnswer = "George Orwell",
                explanation = "George Orwell published '1984' in 1949.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Fill in the blank: The derivative of sin x is _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "cos x",
                explanation = "d/dx (sin x) = cos x.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "What type of chemical reaction occurs when an acid and base combine?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Synthesis", "Decomposition", "Neutralization", "Combustion"),
                correctAnswer = "Neutralization",
                explanation = "Acid-base neutralization produces salt and water.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "True or False: Globalization increases economic interdependence between countries.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Globalization creates stronger economic ties and dependencies between nations.",
                difficulty = Difficulty.HARD
            )
        ),

        // Grade 12
        12 to listOf(
            Question(
                questionText = "Which amendment to the U.S. Constitution abolished slavery?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("11th", "13th", "15th", "19th"),
                correctAnswer = "13th",
                explanation = "The 13th Amendment, ratified in 1865, abolished slavery in the United States.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "True or False: In economics, GDP measures the total market value of all final goods and services produced within a country in a given period.",
                questionType = QuestionType.TRUE_FALSE,
                options = listOf("True", "False"),
                correctAnswer = "True",
                explanation = "Gross Domestic Product (GDP) is the standard measure of a nation's economic output.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Fill in the blank: The process by which cells divide to produce gametes is called _____.",
                questionType = QuestionType.FILL_IN_BLANK,
                options = emptyList(),
                correctAnswer = "meiosis",
                explanation = "Meiosis produces reproductive cells (gametes) with half the chromosome number.",
                difficulty = Difficulty.HARD
            ),
            Question(
                questionText = "Which literary technique involves giving human characteristics to non-human things?",
                questionType = QuestionType.MULTIPLE_CHOICE,
                options = listOf("Metaphor", "Simile", "Personification", "Alliteration"),
                correctAnswer = "Personification",
                explanation = "Personification attributes human qualities to animals, objects, or ideas.",
                difficulty = Difficulty.HARD
            )
        )
    )
    
    /**
     * Get a random trivia question
     */
    fun getRandomTriviaQuestion(): Question {
        return triviaQuestions.random().copy(
            id = "trivia_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
        )
    }
    
    /**
     * Get multiple random trivia questions without repetition
     */
    fun getRandomTriviaQuestions(count: Int): List<Question> {
        return triviaQuestions.shuffled().take(count).mapIndexed { index, question ->
            question.copy(
                id = "trivia_${System.currentTimeMillis()}_$index"
            )
        }
    }
    
    /**
     * Get trivia questions filtered by difficulty
     */
    fun getTriviaQuestionsByDifficulty(difficulty: Difficulty, count: Int = 1): List<Question> {
        return triviaQuestions
            .filter { it.difficulty == difficulty }
            .shuffled()
            .take(count)
            .mapIndexed { index, question ->
                question.copy(
                    id = "trivia_${difficulty.name.lowercase()}_${System.currentTimeMillis()}_$index"
                )
            }
    }
    
    /**
     * Get grade-appropriate trivia questions including all questions from current grade and below
     */
    fun getGradeAppropriateTriviaQuestions(gradeLevel: Int?, count: Int = 1): List<Question> {
        return if (gradeLevel != null && gradeLevel in 0..12) {
            // Get all questions from current grade and all grades below
            val availableQuestions = mutableListOf<Question>()
            
            // Add questions from grade 0 up to the current grade level
            for (grade in 0..gradeLevel) {
                gradeLevelTrivia[grade]?.let { questionsForGrade ->
                    availableQuestions.addAll(questionsForGrade)
                }
            }
            
            // For higher grade students (6+), occasionally mix in general knowledge questions
            // This adds variety and keeps older students engaged with more challenging content
            if (gradeLevel >= 6 && Random.nextFloat() < 0.3f) { // 30% chance
                availableQuestions.addAll(triviaQuestions.filter { 
                    it.difficulty == Difficulty.EASY || it.difficulty == Difficulty.MEDIUM 
                })
            }
            
            // If we have grade-level questions, use them
            if (availableQuestions.isNotEmpty()) {
                availableQuestions.shuffled().take(count).mapIndexed { index, question ->
                    question.copy(
                        id = "cascading_trivia_${gradeLevel}_${System.currentTimeMillis()}_$index"
                    )
                }
            } else {
                // Fallback to general trivia if no grade-specific questions found
                getRandomTriviaQuestions(count)
            }
        } else {
            // For null grade level or grades above 12, use general trivia + all grade questions
            val allGradeQuestions = gradeLevelTrivia.values.flatten()
            val combinedQuestions = triviaQuestions + allGradeQuestions
            
            combinedQuestions.shuffled().take(count).mapIndexed { index, question ->
                question.copy(
                    id = "all_trivia_${System.currentTimeMillis()}_$index"
                )
            }
        }
    }
    
    /**
     * Get count of available questions for a specific grade level (including lower grades)
     */
    fun getAvailableQuestionCount(gradeLevel: Int?): Int {
        return if (gradeLevel != null && gradeLevel in 0..12) {
            var totalCount = 0
            for (grade in 0..gradeLevel) {
                totalCount += gradeLevelTrivia[grade]?.size ?: 0
            }
            
            // For higher grade students (6+), add general knowledge questions to the count
            if (gradeLevel >= 6) {
                val generalKnowledgeCount = triviaQuestions.filter { 
                    it.difficulty == Difficulty.EASY || it.difficulty == Difficulty.MEDIUM 
                }.size
                totalCount += generalKnowledgeCount
            }
            
            totalCount
        } else {
            // For null grade level or grades above 12, count all questions
            val allGradeQuestions = gradeLevelTrivia.values.flatten()
            triviaQuestions.size + allGradeQuestions.size
        }
    }
    
    /**
     * Get breakdown of questions by grade level for debugging/info purposes
     */
    fun getQuestionBreakdown(gradeLevel: Int?): Map<String, Int> {
        val breakdown = mutableMapOf<String, Int>()
        
        if (gradeLevel != null && gradeLevel in 0..12) {
            for (grade in 0..gradeLevel) {
                val count = gradeLevelTrivia[grade]?.size ?: 0
                if (count > 0) {
                    val gradeLabel = when (grade) {
                        0 -> "Kindergarten"
                        else -> "Grade $grade"
                    }
                    breakdown[gradeLabel] = count
                }
            }
        } else {
            // Show all grade levels
            gradeLevelTrivia.forEach { (grade, questions) ->
                val gradeLabel = when (grade) {
                    0 -> "Kindergarten"
                    else -> "Grade $grade"
                }
                breakdown[gradeLabel] = questions.size
            }
            breakdown["General Knowledge"] = triviaQuestions.size
        }
        
        return breakdown
    }
    
    /**
     * Get trivia questions by category/concept
     */
    fun getTriviaQuestionsByCategory(category: String, count: Int = 1): List<Question> {
        return triviaQuestions
            .filter { question -> 
                question.conceptsCovered.any { concept -> 
                    concept.contains(category, ignoreCase = true) 
                }
            }
            .shuffled()
            .take(count)
            .mapIndexed { index, question ->
                question.copy(
                    id = "trivia_${category.lowercase()}_${System.currentTimeMillis()}_$index"
                )
            }
    }
    
    /**
     * Get fun facts to display during generation
     */
    fun getRandomFunFact(): String {
        val funFacts = listOf(
            "🧠 Your brain uses about 20% of your body's total energy!",
            "🐙 Octopuses have three hearts and blue blood!",
            "🌟 There are more possible games of chess than atoms in the observable universe!",
            "🐧 Penguins have knees! They're just hidden inside their bodies.",
            "🍯 Honey never expires - archaeologists have found edible honey in ancient tombs!",
            "🦋 Butterflies taste with their feet!",
            "🌙 The Moon is gradually moving away from Earth at about 1.5 inches per year.",
            "🐘 Elephants are one of the few animals that can recognize themselves in a mirror!",
            "🌊 More people have been to space than to the deepest part of the ocean!",
            "🦒 Giraffes only need 5-30 minutes of sleep per day!",
            "🌋 There are more stars in the universe than grains of sand on all Earth's beaches!",
            "🐋 A blue whale's heart is so big that a small child could crawl through its blood vessels!",
            "🍄 The largest living organism on Earth is a fungus in Oregon!",
            "⚡ Lightning is five times hotter than the surface of the Sun!",
            "🐠 Fish can cough! They do it to clear their gills.",
            "🌈 No two rainbows are exactly the same!",
            "🦜 Parrots name their babies!",
            "🌍 If the Earth were the size of a basketball, all of Earth's water would fit in a ping pong ball!",
            "🐜 Ants never sleep and don't have lungs!",
            "🌙 A day on Venus is longer than its year!"
        )
        return funFacts.random()
    }
    
    /**
     * Get encouraging messages for quiz generation
     */
    fun getEncouragingMessage(): String {
        val messages = listOf(
            "🎯 Crafting the perfect questions for your brain!",
            "⚡ Supercharging your learning experience!",
            "🧩 Building a quiz that matches your style!",
            "🎪 Creating an amazing learning adventure!",
            "🔬 Analyzing the best topics for you!",
            "🎨 Painting the perfect educational masterpiece!",
            "🚀 Launching your personalized quiz experience!",
            "💎 Polishing each question to perfection!",
            "🎭 Directing your custom learning show!",
            "🏗️ Constructing your knowledge fortress!"
        )
        return messages.random()
    }
    
    /**
     * Demo function to show how the cascading system works (for testing/debugging)
     */
    fun demonstrateCascadingQuestions() {
        println("🎯 Trivia Question Cascading System Demo")
        println("=".repeat(50))
        
        listOf(1, 3, 6, 9, 12, null).forEach { grade ->
            val count = getAvailableQuestionCount(grade)
            val breakdown = getQuestionBreakdown(grade)
            val sampleQuestions = getGradeAppropriateTriviaQuestions(grade, 2)
            
            println("\n📚 Grade Level: ${grade ?: "All/Unknown"}")
            println("📊 Total Available Questions: $count")
            println("📈 Question Sources:")
            breakdown.forEach { (source, questionCount) ->
                println("   • $source: $questionCount questions")
            }
            println("🎲 Sample Questions:")
            sampleQuestions.forEachIndexed { index, question ->
                println("   ${index + 1}. ${question.questionText.take(50)}...")
                println("      Difficulty: ${question.difficulty}")
            }
        }
    }
}