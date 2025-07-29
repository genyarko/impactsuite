package com.example.mygemma3n.feature.quiz.trivia

import com.example.mygemma3n.feature.quiz.Question
import com.example.mygemma3n.feature.quiz.QuestionType
import com.example.mygemma3n.feature.quiz.Difficulty

/**
 * Hard‑coded trivia questions shown while quizzes are being generated.
 *
 * The map keys correspond to the student grade level (0 = Kindergarten).
 * Each grade level now includes questions from multiple subjects: Math, Science, English,
 * Geography, History, and Economics (where age-appropriate).
 */
object GradeLevelTrivia {

    val triviaByGrade: Map<Int, List<Question>> = mapOf(
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
}